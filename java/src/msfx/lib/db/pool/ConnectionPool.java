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

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Iterator;
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
	 * Connections in use.
	 */
	private final Set<PoolEntry> inUse = new HashSet<>();
	/**
	 * Connections free.
	 */
	private final Set<PoolEntry> free = new HashSet<>();
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
	 * @param factory The factory that opens the physical connections.
	 */
	public ConnectionPool(ConnectionFactory factory) {
		this.factory = factory;
	}

	/**
	 * @return A new connection handle over a free or a new physical connection.
	 * @throws SQLException If an error occurs.
	 */
	public DBConnection getConnection() throws SQLException {

		/*
		 * Look up a free connection. Free connections are already in auto-commit
		 * mode, see PoolConnection.close().
		 */
		lock.lock();
		try {
			if (timer == null) {
				timer = new Timer("ConnectionPool closer", true);
				timer.schedule(new Closer(), CLOSER_PERIOD, CLOSER_PERIOD);
			}
			Iterator<PoolEntry> i = free.iterator();
			if (i.hasNext()) {
				PoolEntry entry = i.next();
				i.remove();
				entry.timeNotActive = 0;
				inUse.add(entry);
				return new PoolConnection(this, entry);
			}
		} finally {
			lock.unlock();
		}

		/*
		 * Open the physical connection outside the lock, it may take long and would block concurrent
		 * requests, and register it under the lock, sets are not thread safe.
		 */
		PoolEntry entry = new PoolEntry(factory.open());

		lock.lock();
		try {
			inUse.add(entry);
		} finally {
			lock.unlock();
		}
		return new PoolConnection(this, entry);
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
	 * Return a physical connection to the pool, already in auto-commit mode.
	 * @param entry The pooled connection.
	 */
	void recycleConnection(PoolEntry entry) {
		lock.lock();
		try {
			entry.timeNotActive = System.currentTimeMillis();
			inUse.remove(entry);
			free.add(entry);
		} finally {
			lock.unlock();
		}
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
		try {
			entry.cn.close();
		} catch (SQLException ignore) {
		}
	}
}
