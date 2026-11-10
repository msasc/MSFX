/*
 * Copyright (c) 2026 Miquel Sas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package msfx.lib.db.pool;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

import msfx.lib.db.DBConnection;

/**
 * Connection pool.
 * <p>
 * The pool keeps the physical connections as {@link PoolEntry} objects and
 * gives a new {@link PoolConnection} handle over one of them on each request.
 *
 * @author Miquel Sas
 */
public class ConnectionPool {

	/**
	 * Closer timer task.
	 */
	private class Closer extends TimerTask {
		/**
		 * Constructor.
		 */
		Closer() {
		}
		@Override
		public void run() {
			try {
				closeConnections();
			} catch (Exception e) {
				// Any exception would cancel the timer, so catch them all.
				// TODO Use Logger
				e.printStackTrace();
			}
		}
	}

	/**
	 * Time in millis after which a free connection is closed.
	 */
	private static final long FREE_TIMEOUT = 60000;
	/**
	 * Period in millis of the scan for free connections to close.
	 */
	private static final long CLOSER_PERIOD = 10000;
	/**
	 * Idle time in millis from which a free connection is validated with a round
	 * trip to the database before being handed out.
	 */
	private static final long VALIDATION_IDLE = 1000;
	/**
	 * Timeout in seconds of the validation, after which a connection whose link
	 * died silently is considered dead.
	 */
	private static final int VALIDATION_TIMEOUT = 5;

	/**
	 * Connections in use.
	 */
	private final Set<PoolEntry> inUse = new HashSet<>();
	/**
	 * Connections free, as a stack: the last returned is the first reused, so the
	 * connections in use stay warm, and the others age at the bottom until the
	 * closer closes them.
	 */
	private final Deque<PoolEntry> free = new ArrayDeque<>();
	/**
	 * Lock.
	 */
	private final ReentrantLock lock = new ReentrantLock();

	/**
	 * Factory that opens the physical connections.
	 */
	private final ConnectionFactory factory;
	/**
	 * Timer that runs the closer, started with the first connection. Its thread
	 * is a daemon, so it does not keep the application alive.
	 */
	private Timer timer;
	/**
	 * Closed flag, once true it is never false again.
	 */
	private boolean closed = false;

	/**
	 * @param factory The factory that opens the physical connections.
	 */
	public ConnectionPool(ConnectionFactory factory) {
		this.factory = factory;
	}

	/**
	 * Returns a new connection handle over a free or a new physical connection.
	 * <p>
	 * A free connection may have died while idle, by an error in the chain
	 * driver, network, database. It is checked before being handed out if it has
	 * been idle at least {@link #VALIDATION_IDLE} millis, with
	 * {@link Connection#isValid(int)}. A dead one means the others probably died
	 * for the same cause, so all the free connections are discarded and a new
	 * one is opened: the caller waits at most one validation timeout.
	 * <p>
	 * Checks and opening are done outside the lock, so they do not block
	 * concurrent requests.
	 *
	 * @return A new connection handle.
	 * @throws SQLException If an error occurs opening a new connection.
	 * @throws IllegalStateException If the pool is closed.
	 */
	public DBConnection getConnection() throws SQLException {
		PoolEntry entry = takeFree();
		if (entry != null && !isAlive(entry)) {
			closeQuietly(entry);
			discardFree();
			entry = null;
		}
		if (entry == null) {
			entry = new PoolEntry(factory.open());
		}
		register(entry);
		return new PoolConnection(this, entry);
	}

	/**
	 * Takes the top free connection, the last returned, removing it from the
	 * free connections so no other request can take it. Starts the closer with
	 * the first request.
	 *
	 * @return The free connection, or null if there is none.
	 * @throws IllegalStateException If the pool is closed.
	 */
	private PoolEntry takeFree() {
		lock.lock();
		try {
			if (closed) {
				throw new IllegalStateException("Connection pool is closed");
			}
			if (timer == null) {
				timer = new Timer("ConnectionPool closer", true);
				timer.schedule(new Closer(), CLOSER_PERIOD, CLOSER_PERIOD);
			}
			return free.pollFirst();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Checks whether a free connection is still alive. The local closed flag is
	 * always checked, and the round trip to the database only if the connection
	 * has been idle at least {@link #VALIDATION_IDLE} millis: one returned a
	 * moment ago is almost certainly alive.
	 *
	 * @param entry The free connection, already taken from the pool.
	 * @return True if the connection can be used.
	 */
	private static boolean isAlive(PoolEntry entry) {
		try {
			if (entry.cn.isClosed()) {
				return false;
			}
			if (System.currentTimeMillis() - entry.timeNotActive < VALIDATION_IDLE) {
				return true;
			}
			return entry.cn.isValid(VALIDATION_TIMEOUT);
		} catch (SQLException e) {
			return false;
		}
	}

	/**
	 * Registers a connection as in use. If the pool has been closed meanwhile,
	 * the connection is closed.
	 *
	 * @param entry The connection.
	 * @throws IllegalStateException If the pool is closed.
	 */
	private void register(PoolEntry entry) {
		lock.lock();
		try {
			if (!closed) {
				entry.timeNotActive = 0;
				inUse.add(entry);
				return;
			}
		} finally {
			lock.unlock();
		}
		closeQuietly(entry);
		throw new IllegalStateException("Connection pool is closed");
	}

	/**
	 * Closes all the free connections, outside the lock.
	 */
	private void discardFree() {
		List<PoolEntry> toClose;
		lock.lock();
		try {
			toClose = new ArrayList<>(free);
			free.clear();
		} finally {
			lock.unlock();
		}
		for (PoolEntry entry : toClose) {
			closeQuietly(entry);
		}
	}

	/**
	 * Scan free connections to close those closed by the server or not used
	 * within the timeout.
	 *
	 * @throws SQLException if an error occurs
	 */
	private void closeConnections() throws SQLException {
		long time = System.currentTimeMillis();
		lock.lock();
		try {
			Iterator<PoolEntry> i = free.iterator();
			while (i.hasNext()) {
				PoolEntry entry = i.next();
				if (entry.cn.isClosed()) {
					i.remove();
					continue;
				}
				if (time - entry.timeNotActive >= FREE_TIMEOUT) {
					i.remove();
					entry.cn.close();
				}
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Closes the pool: stops the closer and closes the free connections. The
	 * connections in use are closed when their users close them. Once closed,
	 * the pool gives no more connections. Closing a closed pool has no effect.
	 */
	public void close() {
		List<PoolEntry> toClose;
		lock.lock();
		try {
			if (closed) {
				return;
			}
			closed = true;
			if (timer != null) {
				timer.cancel();
				timer = null;
			}
			toClose = new ArrayList<>(free);
			free.clear();
		} finally {
			lock.unlock();
		}
		// Close outside the lock, closing may take a round trip to the server.
		for (PoolEntry entry : toClose) {
			closeQuietly(entry);
		}
	}

	/**
	 * Return a physical connection to the pool, already in auto-commit mode, or
	 * close it if the pool is closed.
	 * @param entry The pooled connection.
	 */
	void recycleConnection(PoolEntry entry) {
		lock.lock();
		try {
			inUse.remove(entry);
			if (!closed) {
				entry.timeNotActive = System.currentTimeMillis();
				free.addFirst(entry);
				return;
			}
		} finally {
			lock.unlock();
		}
		closeQuietly(entry);
	}

	/**
	 * Discard a physical connection whose state could not be restored: close it,
	 * ignoring errors, and do not return it to the pool.
	 * @param entry The pooled connection.
	 */
	void discardConnection(PoolEntry entry) {
		lock.lock();
		try {
			inUse.remove(entry);
		} finally {
			lock.unlock();
		}
		closeQuietly(entry);
	}

	/**
	 * Close a physical connection, ignoring errors.
	 * @param entry The pooled connection.
	 */
	private static void closeQuietly(PoolEntry entry) {
		try {
			entry.cn.close();
		} catch (SQLException ignore) {
		}
	}
}
