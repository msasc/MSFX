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

import msfx.lib.db.DBPreparedStatement;
import msfx.lib.db.DBResultSet;
import msfx.lib.md.Types;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Pool prepared statement, a thin wrapper over the JDBC prepared statement
 * created through a {@link PoolConnection}. It is closed when closed directly
 * or when its connection is closed.
 * <p>
 * Setters of object values set SQL NULL with the proper type when the value is
 * null, and date and time values are passed as java.time objects (JDBC 4.2),
 * without conversions through the default time zone.
 *
 * @author Miquel Sas
 */
class PoolPreparedStatement implements DBPreparedStatement {

	/**
	 * The connection that created the statement, informed when it is closed.
	 */
	private final PoolConnection cn;
	/**
	 * Internal JDBC prepared statement.
	 */
	private final PreparedStatement ps;

	/**
	 * @param cn The connection that created the statement.
	 * @param ps The JDBC prepared statement to wrap.
	 */
	PoolPreparedStatement(PoolConnection cn, PreparedStatement ps) {
		this.cn = cn;
		this.ps = ps;
	}

	@Override
	public void addBatch() throws SQLException {
		ps.addBatch();
	}
	@Override
	public void clearBatch() throws SQLException {
		ps.clearBatch();
	}
	@Override
	public int[] executeBatch() throws SQLException {
		return ps.executeBatch();
	}

	@Override
	public boolean isClosed() throws SQLException {
		return ps.isClosed();
	}
	@Override
	public void cancel() throws SQLException {
		ps.cancel();
	}
	@Override
	public void close() throws SQLException {
		cn.removeStatement(ps);
		ps.close();
	}
	@Override
	public void setFetchSize(int rows) throws SQLException {
		ps.setFetchSize(rows);
	}
	@Override
	public void setBoolean(int index, boolean x) throws SQLException {
		ps.setBoolean(index, x);
	}

	@Override
	public void setDecimal(int index, BigDecimal x) throws SQLException {
		if (x == null) {
			setNull(index, Types.DECIMAL);
		} else {
			ps.setBigDecimal(index, x);
		}
	}
	@Override
	public void setDouble(int index, double x) throws SQLException {
		ps.setDouble(index, x);
	}
	@Override
	public void setInteger(int index, int x) throws SQLException {
		ps.setInt(index, x);
	}
	@Override
	public void setLong(int index, long x) throws SQLException {
		ps.setLong(index, x);
	}

	@Override
	public void setDate(int index, LocalDate x) throws SQLException {
		if (x == null) {
			setNull(index, Types.DATE);
		} else {
			ps.setObject(index, x);
		}
	}
	@Override
	public void setTime(int index, LocalTime x) throws SQLException {
		if (x == null) {
			setNull(index, Types.TIME);
		} else {
			ps.setObject(index, x);
		}
	}
	@Override
	public void setDateTime(int index, LocalDateTime x) throws SQLException {
		if (x == null) {
			setNull(index, Types.DATETIME);
		} else {
			ps.setObject(index, x);
		}
	}

	@Override
	public void setString(int index, String x) throws SQLException {
		if (x == null) {
			setNull(index, Types.STRING);
		} else {
			ps.setString(index, x);
		}
	}
	@Override
	public void setClob(int index, Reader reader) throws SQLException {
		if (reader == null) {
			ps.setNull(index, java.sql.Types.CLOB);
		} else {
			ps.setClob(index, reader);
		}
	}

	@Override
	public void setBinary(int index, byte[] x) throws SQLException {
		if (x == null) {
			setNull(index, Types.BINARY);
		} else {
			ps.setBytes(index, x);
		}
	}
	@Override
	public void setBlob(int index, InputStream inputStream) throws SQLException {
		if (inputStream == null) {
			ps.setNull(index, java.sql.Types.BLOB);
		} else {
			ps.setBlob(index, inputStream);
		}
	}

	@Override
	public void setNull(int index, Types type) throws SQLException {
		ps.setNull(index, sqlType(type));
	}

	@Override
	public int executeUpdate() throws SQLException {
		return ps.executeUpdate();
	}
	@Override
	public DBResultSet executeQuery() throws SQLException {
		return new PoolResultSet(ps.executeQuery());
	}

	/**
	 * @param type The MSFX type.
	 * @return The JDBC type code used to set a null value of the type. OBJECT is
	 *         stored as JSON text.
	 */
	private static int sqlType(Types type) {
		int sqlType = switch (type) {
			case BOOLEAN:
				yield java.sql.Types.BOOLEAN;
			case DECIMAL:
				yield java.sql.Types.DECIMAL;
			case DOUBLE:
				yield java.sql.Types.DOUBLE;
			case INTEGER:
				yield java.sql.Types.INTEGER;
			case LONG:
				yield java.sql.Types.BIGINT;
			case DATE:
				yield java.sql.Types.DATE;
			case TIME:
				yield java.sql.Types.TIME;
			case DATETIME:
				yield java.sql.Types.TIMESTAMP;
			case STRING:
			case OBJECT:
				yield java.sql.Types.VARCHAR;
			case BINARY:
				yield java.sql.Types.VARBINARY;
		};
		return sqlType;
	}
}
