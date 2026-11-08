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
 * The methods shared by {@link DBStatement} and {@link DBPreparedStatement}.
 * <p>
 * Unlike JDBC, where a prepared statement extends a statement and inherits
 * methods it must not call, both statements extend this common root, so each
 * one only has the methods it can use.
 *
 * @author Miquel Sas
 */
public interface DBStatementBase extends AutoCloseable {
	/**
	 * @return A boolean indicating if the statement is closed.
	 * @throws SQLException If an error occurs.
	 */
	boolean isClosed() throws SQLException;
	/**
	 * Cancels the statement, if both the database and the driver support it.
	 * Usually called from another thread to stop a long running query.
	 * @throws SQLException If an error occurs.
	 */
	void cancel() throws SQLException;
	/**
	 * Closes the statement and its current result set, if any.
	 * @throws SQLException If an error occurs.
	 */
	@Override
	void close() throws SQLException;
	/**
	 * Sets the number of rows fetched from the database each time more rows are
	 * needed, a hint to read large result sets without loading them whole in
	 * memory. Some drivers, like PostgreSQL, only apply it with auto-commit off.
	 * @param rows The number of rows, or 0 to leave the choice to the driver.
	 * @throws SQLException If an error occurs.
	 */
	void setFetchSize(int rows) throws SQLException;
	/**
	 * Clears the commands added to the batch.
	 * @throws SQLException If an error occurs.
	 */
	void clearBatch() throws SQLException;
	/**
	 * Executes the commands added to the batch.
	 * @return The update counts, one per command, in the order they were added.
	 * @throws SQLException If an error occurs.
	 */
	int[] executeBatch() throws SQLException;
}
