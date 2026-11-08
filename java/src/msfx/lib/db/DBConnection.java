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

package msfx.lib.db;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * A simplified JDBC connection with just the methods needed by MSFX.
 *
 * @author Miquel Sas
 */
public interface DBConnection extends AutoCloseable {
	/**
	 * @return A boolean indicating if the connection is closed.
	 * @throws SQLException If an error occurs.
	 */
	boolean isClosed() throws SQLException;
	/**
	 * Closes the connection.
	 * @throws SQLException If an error occurs.
	 */
	@Override
	void close() throws SQLException;
	/**
	 * @return A boolean indicating if the connection is in auto-commit mode.
	 * @throws SQLException If an error occurs.
	 */
	boolean isAutoCommit() throws SQLException;
	/**
	 * @param autoCommit A boolean indicating if the connection should be in auto-commit mode.
	 * @throws SQLException If an error occurs.
	 */
	void setAutoCommit(boolean autoCommit) throws SQLException;
	/**
	 * Commits the current transaction.
	 * @throws SQLException If an error occurs.
	 */
	void commit() throws SQLException;
	/**
	 * Rolls back the current transaction.
	 * @throws SQLException If an error occurs.
	 */
	void rollback() throws SQLException;

	/**
	 * @return A new statement, to be closed by the caller.
	 * @throws SQLException If an error occurs.
	 */
	DBStatement createStatement() throws SQLException;
	/**
	 * @param sql The SQL command, with a ? for each parameter.
	 * @return A new prepared statement, to be closed by the caller.
	 * @throws SQLException If an error occurs.
	 */
	DBPreparedStatement prepareStatement(String sql) throws SQLException;
	/**
	 * Gives access to the information about the database, for instance to read
	 * the structure of existing tables. It is the JDBC object, meant to be used
	 * only within the db package.
	 * @return A DatabaseMetaData object that contains information about the database.
	 * @throws SQLException If an error occurs.
	 */
	DatabaseMetaData getMetaData() throws SQLException;
}
