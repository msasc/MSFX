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

package test;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.adapters.DBEngineSQLServer;

public class TestConnectionMSFX {
	public static void main(String[] args) {
		DBEngineSQLServer db = new DBEngineSQLServer();
		String cnString = "localhost:1433;databaseName=sql_cma_qlt";
		String user = "sa";
		String password = "SqlPassword2026!";
		db.connectTo(cnString, user, password);
		db.addSchema("EXPLMARG_QLT");

		String sql = "SELECT * FROM EXPLMARG_QLT.ARTICLES";
		// String sql = "SELECT * FROM guest.ARTICLES";
		try (DBConnection cn = db.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql)) {
			long start = System.currentTimeMillis();
			int count = 0;
			while (rs.next()) {
				System.out.println(rs.getString("CARTICLE") + "- " + rs.getString("DARTICLE"));
				count++;
			}
			long end = System.currentTimeMillis();
		System.out.println(count + " records read.");
		System.out.println((end-start) + " millis.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
