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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;

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
		return rs.getBoolean(index);
	}
	@Override
	public boolean getBoolean(String alias) throws SQLException {
		return rs.getBoolean(alias);
	}

	@Override
	public BigDecimal getBigDecimal(int index) throws SQLException {
		return rs.getBigDecimal(index);
	}
	@Override
	public BigDecimal getBigDecimal(String alias) throws SQLException {
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
	public Date getDate(int index) throws SQLException {
		return rs.getDate(index);
	}
	@Override
	public Date getDate(String alias) throws SQLException {
		return rs.getDate(alias);
	}
	@Override
	public Time getTime(int index) throws SQLException {
		return rs.getTime(index);
	}
	@Override
	public Time getTime(String alias) throws SQLException {
		return rs.getTime(alias);
	}
	@Override
	public Timestamp getTimestamp(int index) throws SQLException {
		return rs.getTimestamp(index);
	}
	@Override
	public Timestamp getTimestamp(String alias) throws SQLException {
		return rs.getTimestamp(alias);
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
	public byte[] getBytes(int index) throws SQLException {
		return rs.getBytes(index);
	}
	@Override
	public byte[] getBytes(String alias) throws SQLException {
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
}
