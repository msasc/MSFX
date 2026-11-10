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

import msfx.lib.md.Types;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A simplified JDBC prepared statement with just the methods needed by MSFX.
 * <p>
 * The SQL command is given when the statement is prepared, with a ? for each
 * parameter. Parameters are set by index, starting at 1, with one setter per
 * type of {@link msfx.lib.md.Types}, named and typed as in
 * {@link msfx.lib.md.Value}, and keep their values until set again, so the
 * statement can be executed or added to the batch many times setting only the
 * parameters that change.
 * <p>
 * Setters of object values accept null, which sets the parameter to SQL NULL,
 * the same as {@link #setNull(int, Types)}.
 *
 * @author Miquel Sas
 */
public interface DBPreparedStatement extends DBStatementBase {
	/**
	 * BOOLEAN.
	 * @param index The parameter index, starting at 1.
	 * @param x     The value.
	 * @throws SQLException If an error occurs.
	 */
	void setBoolean(int index, boolean x) throws SQLException;

	/**
	 * DECIMAL.
	 * @param index The parameter index, starting at 1.
	 * @param x     The value, or null.
	 * @throws SQLException If an error occurs.
	 */
	void setDecimal(int index, BigDecimal x) throws SQLException;
	/**
	 * DOUBLE.
	 * @param index The parameter index, starting at 1.
	 * @param x     The value.
	 * @throws SQLException If an error occurs.
	 */
	void setDouble(int index, double x) throws SQLException;
	/**
	 * INTEGER.
	 * @param index The parameter index, starting at 1.
	 * @param x     The value.
	 * @throws SQLException If an error occurs.
	 */
	void setInteger(int index, int x) throws SQLException;
	/**
	 * LONG.
	 * @param index The parameter index, starting at 1.
	 * @param x     The value.
	 * @throws SQLException If an error occurs.
	 */
	void setLong(int index, long x) throws SQLException;

	/**
	 * DATE.
	 * @param index The parameter index, starting at 1.
	 * @param x     The value, or null.
	 * @throws SQLException If an error occurs.
	 */
	void setDate(int index, LocalDate x) throws SQLException;
	/**
	 * TIME.
	 * @param index The parameter index, starting at 1.
	 * @param x     The value, or null.
	 * @throws SQLException If an error occurs.
	 */
	void setTime(int index, LocalTime x) throws SQLException;
	/**
	 * DATETIME.
	 * @param index The parameter index, starting at 1.
	 * @param x     The value, or null.
	 * @throws SQLException If an error occurs.
	 */
	void setDateTime(int index, LocalDateTime x) throws SQLException;

	/**
	 * STRING, and OBJECT as JSON text.
	 * @param index The parameter index, starting at 1.
	 * @param x     The value, or null.
	 * @throws SQLException If an error occurs.
	 */
	void setString(int index, String x) throws SQLException;
	/**
	 * STRING or OBJECT stored as CLOB, written from a stream.
	 * @param index  The parameter index, starting at 1.
	 * @param reader The reader with the value, read until its end, or null.
	 * @throws SQLException If an error occurs.
	 */
	void setClob(int index, Reader reader) throws SQLException;

	/**
	 * BINARY.
	 * @param index The parameter index, starting at 1.
	 * @param x     The value, or null.
	 * @throws SQLException If an error occurs.
	 */
	void setBinary(int index, byte[] x) throws SQLException;
	/**
	 * BINARY stored as BLOB, written from a stream.
	 * @param index       The parameter index, starting at 1.
	 * @param inputStream The input stream with the value, read until its end,
	 *                    or null.
	 * @throws SQLException If an error occurs.
	 */
	void setBlob(int index, InputStream inputStream) throws SQLException;

	/**
	 * Sets a parameter to SQL NULL, for any type.
	 * @param index The parameter index, starting at 1.
	 * @param type  The type of the column the parameter refers to.
	 * @throws SQLException If an error occurs.
	 */
	void setNull(int index, Types type) throws SQLException;

	/**
	 * Adds the current parameter values to the batch, to be executed with
	 * {@link #executeBatch()}.
	 * @throws SQLException If an error occurs.
	 */
	void addBatch() throws SQLException;
	/**
	 * Executes the command, which must not return rows, with the current
	 * parameter values.
	 * @return The number of rows affected, or 0 for DDL commands.
	 * @throws SQLException If an error occurs.
	 */
	int executeUpdate() throws SQLException;
	/**
	 * Executes the query with the current parameter values.
	 * @return The result set, to be closed by the caller.
	 * @throws SQLException If an error occurs.
	 */
	DBResultSet executeQuery() throws SQLException;
}
