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

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBPreparedStatement;
import msfx.lib.db.DBStatement;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Pool connection, the handle given by the pool on each request over a pooled
 * physical connection.
 * <p>
 * A handle is used once: after it is closed it stays closed, and any method
 * other than close and isClosed throws an exception, even when the pool has
 * given the physical connection to another request.
 *
 * @author Miquel Sas
 */
class PoolConnection implements DBConnection {
	/**
	 * Error message when the pooled connection is closed.
	 */
	private static final String CLOSED = "PoolConnection is closed";
	/**
	 * Pool manager.
	 */
	private final ConnectionPool pool;
	/**
	 * Pooled physical connection.
	 */
	private final PoolEntry entry;
	/**
	 * JDBC statements created through this handle and not yet closed, plain and
	 * prepared.
	 */
	private final List<Statement> statements = new ArrayList<>();
	/**
	 * Close flag, once true it is never false again.
	 */
	private boolean closed = false;

	/**
	 * @param pool  The pool.
	 * @param entry The pooled physical connection.
	 */
	PoolConnection(ConnectionPool pool, PoolEntry entry) {
		this.pool = pool;
		this.entry = entry;
	}

	/**
	 * @return True if the connection is closed.
	 * @throws SQLException If an error occurs.
	 */
	@Override
	public boolean isClosed() throws SQLException {
		return closed;
	}
	/**
	 * Close the connection and return the physical connection to the pool. From
	 * now on, any method other than close and isClosed throws an exception.
	 * <p>
	 * The statements created through this connection are closed. A pending
	 * transaction is rolled back, never committed: work is only saved by an
	 * explicit commit, and the behavior is the same whatever the driver does on
	 * close. The physical connection is returned to the pool in auto-commit
	 * mode, without locks held. If restoring that state fails, it is discarded
	 * instead of being returned to the pool.
	 * <p>
	 * Closing a closed connection has no effect.
	 *
	 * @throws SQLException If the rollback or the restore of auto-commit fails.
	 */
	@Override
	public void close() throws SQLException {
		if (closed) {
			return;
		}
		closed = true;
		closeStatements();
		try {
			if (!entry.cn.getAutoCommit()) {
				entry.cn.rollback();
				entry.cn.setAutoCommit(true);
			}
		} catch (SQLException e) {
			pool.discardConnection(entry);
			throw e;
		}
		pool.recycleConnection(entry);
	}
	/**
	 * @return True if the connection is in auto-commit mode.
	 * @throws SQLException If an error occurs.
	 */
	@Override
	public boolean isAutoCommit() throws SQLException {
		checkOpen();
		return entry.cn.getAutoCommit();
	}
	/**
	 * @param autoCommit A boolean indicating if the connection should be in auto-commit mode.
	 * @throws SQLException If an error occurs.
	 */
	@Override
	public void setAutoCommit(boolean autoCommit) throws SQLException {
		checkOpen();
		entry.cn.setAutoCommit(autoCommit);
	}
	/**
	 * Commits the current transaction.
	 * @throws SQLException If an error occurs.
	 */
	@Override
	public void commit() throws SQLException {
		checkOpen();
		entry.cn.commit();
	}
	/**
	 * Rolls back the current transaction.
	 * @throws SQLException If an error occurs.
	 */
	@Override
	public void rollback() throws SQLException {
		checkOpen();
		entry.cn.rollback();
	}
	/**
	 * @return A DBStatement object.
	 * @throws SQLException If an error occurs.
	 */
	@Override
	public DBStatement createStatement() throws SQLException {
		checkOpen();
		Statement st = entry.cn.createStatement();
		statements.add(st);
		return new PoolStatement(this, st);
	}
	/**
	 * @param sql The SQL command, with a ? for each parameter.
	 * @return A DBPreparedStatement object.
	 * @throws SQLException If an error occurs.
	 */
	@Override
	public DBPreparedStatement prepareStatement(String sql) throws SQLException {
		checkOpen();
		PreparedStatement ps = entry.cn.prepareStatement(sql);
		statements.add(ps);
		return new PoolPreparedStatement(this, ps);
	}
	/**
	 * @return A DatabaseMetaData object.
	 * @throws SQLException If an error occurs.
	 */
	@Override
	public DatabaseMetaData getMetaData() throws SQLException {
		checkOpen();
		return entry.cn.getMetaData();
	}

	/**
	 * Called by a statement when it is closed directly.
	 * @param st The JDBC statement.
	 */
	void removeStatement(Statement st) {
		statements.remove(st);
	}
	/**
	 * @throws SQLException If the connection is closed.
	 */
	private void checkOpen() throws SQLException {
		if (closed) {
			throw new SQLException(CLOSED);
		}
	}
	/**
	 * Close all the statements created through this connection, each one even if
	 * closing another fails.
	 */
	private void closeStatements() {
		for (Statement st : statements) {
			try {
				st.close();
			} catch (SQLException ignore) {
			}
		}
		statements.clear();
	}
}
