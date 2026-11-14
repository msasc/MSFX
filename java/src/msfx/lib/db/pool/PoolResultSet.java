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

import msfx.lib.db.DBResultSet;
import msfx.lib.util.json.JSONObject;
import msfx.lib.util.json.JSONParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Pool result set, a thin wrapper over the JDBC result set. It is closed when
 * closed directly or when its statement is closed.
 *
 * @author Miquel Sas
 */
class PoolResultSet implements DBResultSet {

	/**
	 * Internal JDBC result set.
	 */
	private final ResultSet rs;

	/**
	 * @param rs The JDBC result set to wrap.
	 */
	PoolResultSet(ResultSet rs) {
		this.rs = rs;
	}

	@Override
	public boolean next() throws SQLException {
		return rs.next();
	}
	@Override
	public boolean isClosed() throws SQLException {
		return rs.isClosed();
	}
	@Override
	public void close() throws SQLException {
		rs.close();
	}
	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		return rs.getMetaData();
	}

	@Override
	public boolean getBoolean(int index) throws SQLException {
		return toBoolean(rs.getString(index));
	}
	@Override
	public boolean getBoolean(String alias) throws SQLException {
		return toBoolean(rs.getString(alias));
	}
	/**
	 * Converts the text of a boolean column, not left to the driver, since
	 * booleans are stored as Y/N, 1/0 or T/F in one character, or as bits that
	 * read as 1/0.
	 *
	 * @param value The text of the column, or null.
	 * @return True if the text starts with 1, Y or T, in any case. False
	 *         otherwise, also for null or an empty text.
	 */
	private static boolean toBoolean(String value) {
		if (value == null || value.isEmpty()) {
			return false;
		}
		boolean bool = switch (value.charAt(0)) {
			case '1':
			case 'Y':
			case 'y':
			case 'T':
			case 't':
				yield true;
			default:
				yield false;
		};
		return bool;
	}

	@Override
	public BigDecimal getDecimal(int index) throws SQLException {
		return rs.getBigDecimal(index);
	}
	@Override
	public BigDecimal getDecimal(String alias) throws SQLException {
		return rs.getBigDecimal(alias);
	}
	@Override
	public double getDouble(int index) throws SQLException {
		return rs.getDouble(index);
	}
	@Override
	public double getDouble(String alias) throws SQLException {
		return rs.getDouble(alias);
	}
	@Override
	public int getInteger(int index) throws SQLException {
		return rs.getInt(index);
	}
	@Override
	public int getInteger(String alias) throws SQLException {
		return rs.getInt(alias);
	}
	@Override
	public long getLong(int index) throws SQLException {
		return rs.getLong(index);
	}
	@Override
	public long getLong(String alias) throws SQLException {
		return rs.getLong(alias);
	}

	@Override
	public LocalDate getDate(int index) throws SQLException {
		return rs.getObject(index, LocalDate.class);
	}
	@Override
	public LocalDate getDate(String alias) throws SQLException {
		return rs.getObject(alias, LocalDate.class);
	}
	@Override
	public LocalTime getTime(int index) throws SQLException {
		return rs.getObject(index, LocalTime.class);
	}
	@Override
	public LocalTime getTime(String alias) throws SQLException {
		return rs.getObject(alias, LocalTime.class);
	}
	@Override
	public LocalDateTime getDateTime(int index) throws SQLException {
		return rs.getObject(index, LocalDateTime.class);
	}
	@Override
	public LocalDateTime getDateTime(String alias) throws SQLException {
		return rs.getObject(alias, LocalDateTime.class);
	}

	@Override
	public String getString(int index) throws SQLException {
		return rs.getString(index);
	}
	@Override
	public String getString(String alias) throws SQLException {
		return rs.getString(alias);
	}
	@Override
	public Reader getClob(int index) throws SQLException {
		return rs.getCharacterStream(index);
	}
	@Override
	public Reader getClob(String alias) throws SQLException {
		return rs.getCharacterStream(alias);
	}

	@Override
	public byte[] getBinary(int index) throws SQLException {
		return rs.getBytes(index);
	}
	@Override
	public byte[] getBinary(String alias) throws SQLException {
		return rs.getBytes(alias);
	}
	@Override
	public InputStream getBlob(int index) throws SQLException {
		return rs.getBinaryStream(index);
	}
	@Override
	public InputStream getBlob(String alias) throws SQLException {
		return rs.getBinaryStream(alias);
	}

	@Override
	public JSONObject getObject(int index) throws SQLException {
		StringBuilder column = new StringBuilder();
		column.append("column ");
		column.append(index);
		return toObject(rs.getCharacterStream(index), column.toString());
	}
	@Override
	public JSONObject getObject(String alias) throws SQLException {
		StringBuilder column = new StringBuilder();
		column.append("column ");
		column.append(alias);
		return toObject(rs.getCharacterStream(alias), column.toString());
	}
	/**
	 * Parses the text of an object column, read as a stream. A new parser is
	 * used on each call, since it keeps its reading state.
	 *
	 * @param reader The stream of the text, or null.
	 * @param column The column, for error messages.
	 * @return The object, or null if the stream is null.
	 * @throws SQLException If the text is not a JSON object, or can not be read,
	 *                      with the parse error as the cause.
	 */
	private static JSONObject toObject(Reader reader, String column) throws SQLException {
		if (reader == null) {
			return null;
		}
		try (reader) {
			return new JSONParser().parse(reader);
		} catch (IOException exc) {
			StringBuilder msg = new StringBuilder();
			msg.append("The value of ");
			msg.append(column);
			msg.append(" is not a JSON object: ");
			msg.append(exc.getMessage());
			throw new SQLException(msg.toString(), exc);
		}
	}
}
