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
import msfx.lib.db.DBStatement;

import java.sql.SQLException;
import java.sql.Statement;

class PoolStatement implements DBStatement {

	PoolConnection cn;
	Statement st;

	PoolStatement(PoolConnection cn, Statement st) {
		this.cn = cn;
		this.st = st;
	}

	@Override
	public boolean isClosed() throws SQLException {
		return st.isClosed();
	}
	@Override
	public void cancel() throws SQLException {
		st.cancel();
	}
	@Override
	public void close() throws SQLException {
		cn.removeStatement(st);
		st.close();
	}
	@Override
	public void addBatch(String sql) throws SQLException {
		st.addBatch(sql);
	}
	@Override
	public void clearBatch() throws SQLException {
		st.clearBatch();
	}
	@Override
	public int[] executeBatch() throws SQLException {
		return st.executeBatch();
	}
	@Override
	public int executeUpdate(String sql) throws SQLException {
		return st.executeUpdate(sql);
	}
	@Override
	public DBResultSet executeQuery(String sql) throws SQLException {
		return new PoolResultSet(st.executeQuery(sql));
	}
	@Override
	public void setFetchSize(int rows) throws SQLException {
		st.setFetchSize(rows);
	}
}
