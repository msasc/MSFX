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

import java.sql.SQLException;

/**
 * A simplified JDBC statement with just the methods needed by MSFX.
 * <p>
 * Executes complete SQL commands, without parameters. Use a
 * {@link DBPreparedStatement} to execute a command many times with different
 * values.
 *
 * @author Miquel Sas
 */
public interface DBStatement extends DBStatementBase {
	/**
	 * Adds a command to the batch, to be executed with {@link #executeBatch()}.
	 * @param sql An INSERT, UPDATE, DELETE or DDL command.
	 * @throws SQLException If an error occurs.
	 */
	void addBatch(String sql) throws SQLException;
	/**
	 * Executes a command that does not return rows.
	 * @param sql An INSERT, UPDATE, DELETE or DDL command.
	 * @return The number of rows affected, or 0 for DDL commands.
	 * @throws SQLException If an error occurs.
	 */
	int executeUpdate(String sql) throws SQLException;
	/**
	 * Executes a query.
	 * @param sql The SELECT command.
	 * @return The result set, to be closed by the caller.
	 * @throws SQLException If an error occurs.
	 */
	DBResultSet executeQuery(String sql) throws SQLException;
}
