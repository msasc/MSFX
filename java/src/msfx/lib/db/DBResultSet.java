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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A simplified JDBC result set, read forward only, with one getter per type of
 * {@link msfx.lib.md.Types}, named and typed as in {@link msfx.lib.md.Value}.
 * <p>
 * Column values are accessed by index, starting at 1 as in JDBC, or by alias,
 * the label given to the column in the query (AS alias), which is the column
 * name when no alias is given. Access by index is faster; access by alias is
 * more readable.
 * <p>
 * Numbers and booleans are never null, so their getters return primitives. The
 * getters of the nullable types (dates, times, strings and binaries) return null
 * for a null value, so there is no need for a wasNull method.
 *
 * @author Miquel Sas
 */
public interface DBResultSet extends AutoCloseable {
	/**
	 * Moves to the next row. The result set starts before the first row.
	 * @return A boolean indicating if there is a row, false after the last one.
	 * @throws SQLException If an error occurs.
	 */
	boolean next() throws SQLException;
	/**
	 * @return A boolean indicating if the result set is closed.
	 * @throws SQLException If an error occurs.
	 */
	boolean isClosed() throws SQLException;
	/**
	 * Closes the result set.
	 * @throws SQLException If an error occurs.
	 */
	@Override
	void close() throws SQLException;
	/**
	 * Gives access to the description of the result columns: number, labels and
	 * types. It is the JDBC object, meant to be used only within the db package.
	 * @return The ResultSetMetaData object that describes the columns.
	 * @throws SQLException If an error occurs.
	 */
	ResultSetMetaData getMetaData() throws SQLException;

	/**
	 * BOOLEAN.
	 * @param index The column index, starting at 1.
	 * @return The value.
	 * @throws SQLException If an error occurs.
	 */
	boolean getBoolean(int index) throws SQLException;
	/**
	 * BOOLEAN.
	 * @param alias The column alias.
	 * @return The value.
	 * @throws SQLException If an error occurs or the alias is not found.
	 */
	boolean getBoolean(String alias) throws SQLException;

	/**
	 * DECIMAL.
	 * @param index The column index, starting at 1.
	 * @return The value.
	 * @throws SQLException If an error occurs.
	 */
	BigDecimal getDecimal(int index) throws SQLException;
	/**
	 * DECIMAL.
	 * @param alias The column alias.
	 * @return The value.
	 * @throws SQLException If an error occurs or the alias is not found.
	 */
	BigDecimal getDecimal(String alias) throws SQLException;
	/**
	 * DOUBLE.
	 * @param index The column index, starting at 1.
	 * @return The value.
	 * @throws SQLException If an error occurs.
	 */
	double getDouble(int index) throws SQLException;
	/**
	 * DOUBLE.
	 * @param alias The column alias.
	 * @return The value.
	 * @throws SQLException If an error occurs or the alias is not found.
	 */
	double getDouble(String alias) throws SQLException;
	/**
	 * INTEGER.
	 * @param index The column index, starting at 1.
	 * @return The value.
	 * @throws SQLException If an error occurs.
	 */
	int getInteger(int index) throws SQLException;
	/**
	 * INTEGER.
	 * @param alias The column alias.
	 * @return The value.
	 * @throws SQLException If an error occurs or the alias is not found.
	 */
	int getInteger(String alias) throws SQLException;
	/**
	 * LONG.
	 * @param index The column index, starting at 1.
	 * @return The value.
	 * @throws SQLException If an error occurs.
	 */
	long getLong(int index) throws SQLException;
	/**
	 * LONG.
	 * @param alias The column alias.
	 * @return The value.
	 * @throws SQLException If an error occurs or the alias is not found.
	 */
	long getLong(String alias) throws SQLException;

	/**
	 * DATE.
	 * @param index The column index, starting at 1.
	 * @return The value, or null.
	 * @throws SQLException If an error occurs.
	 */
	LocalDate getDate(int index) throws SQLException;
	/**
	 * DATE.
	 * @param alias The column alias.
	 * @return The value, or null.
	 * @throws SQLException If an error occurs or the alias is not found.
	 */
	LocalDate getDate(String alias) throws SQLException;
	/**
	 * TIME.
	 * @param index The column index, starting at 1.
	 * @return The value, or null.
	 * @throws SQLException If an error occurs.
	 */
	LocalTime getTime(int index) throws SQLException;
	/**
	 * TIME.
	 * @param alias The column alias.
	 * @return The value, or null.
	 * @throws SQLException If an error occurs or the alias is not found.
	 */
	LocalTime getTime(String alias) throws SQLException;
	/**
	 * DATETIME.
	 * @param index The column index, starting at 1.
	 * @return The value, or null.
	 * @throws SQLException If an error occurs.
	 */
	LocalDateTime getDateTime(int index) throws SQLException;
	/**
	 * DATETIME.
	 * @param alias The column alias.
	 * @return The value, or null.
	 * @throws SQLException If an error occurs or the alias is not found.
	 */
	LocalDateTime getDateTime(String alias) throws SQLException;

	/**
	 * STRING, and OBJECT as JSON text.
	 * @param index The column index, starting at 1.
	 * @return The value, or null.
	 * @throws SQLException If an error occurs.
	 */
	String getString(int index) throws SQLException;
	/**
	 * STRING, and OBJECT as JSON text.
	 * @param alias The column alias.
	 * @return The value, or null.
	 * @throws SQLException If an error occurs or the alias is not found.
	 */
	String getString(String alias) throws SQLException;
	/**
	 * STRING or OBJECT stored as CLOB, read as a stream.
	 * @param index The column index, starting at 1.
	 * @return The reader, or null if the value is null.
	 * @throws SQLException If an error occurs.
	 */
	Reader getClob(int index) throws SQLException;
	/**
	 * STRING or OBJECT stored as CLOB, read as a stream.
	 * @param alias The column alias.
	 * @return The reader, or null if the value is null.
	 * @throws SQLException If an error occurs or the alias is not found.
	 */
	Reader getClob(String alias) throws SQLException;

	/**
	 * BINARY.
	 * @param index The column index, starting at 1.
	 * @return The value, or null.
	 * @throws SQLException If an error occurs.
	 */
	byte[] getBinary(int index) throws SQLException;
	/**
	 * BINARY.
	 * @param alias The column alias.
	 * @return The value, or null.
	 * @throws SQLException If an error occurs or the alias is not found.
	 */
	byte[] getBinary(String alias) throws SQLException;
	/**
	 * BINARY stored as BLOB, read as a stream.
	 * @param index The column index, starting at 1.
	 * @return The input stream, or null if the value is null.
	 * @throws SQLException If an error occurs.
	 */
	InputStream getBlob(int index) throws SQLException;
	/**
	 * BINARY stored as BLOB, read as a stream.
	 * @param alias The column alias.
	 * @return The input stream, or null if the value is null.
	 * @throws SQLException If an error occurs or the alias is not found.
	 */
	InputStream getBlob(String alias) throws SQLException;
}
