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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;

class PoolPreparedStatement implements DBPreparedStatement {

	PoolConnection cn;
	PreparedStatement ps;

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
	public void setBigDecimal(int index, BigDecimal x) throws SQLException {
		ps.setBigDecimal(index, x);
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
	public void setDate(int index, Date x) throws SQLException {
		ps.setDate(index, x);
	}
	@Override
	public void setTime(int index, Time x) throws SQLException {
		ps.setTime(index, x);
	}
	@Override
	public void setTimestamp(int index, Timestamp x) throws SQLException {
		ps.setTimestamp(index, x);
	}

	@Override
	public void setString(int index, String x) throws SQLException {
		ps.setString(index, x);
	}
	@Override
	public void setClob(int index, Reader reader) throws SQLException {
		ps.setClob(index, reader);
	}

	@Override
	public void setBytes(int index, byte[] x) throws SQLException {
		ps.setBytes(index, x);
	}
	@Override
	public void setBlob(int index, InputStream inputStream) throws SQLException {
		ps.setBlob(index, inputStream);
	}

	@Override
	public int executeUpdate() throws SQLException {
		return ps.executeUpdate();
	}
	@Override
	public DBResultSet executeQuery() throws SQLException {
		return new PoolResultSet(ps.executeQuery());
	}
}
