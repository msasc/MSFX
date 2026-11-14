/*
 * Copyright (c) 2026 Miquel Sas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test.claude.db;

import java.io.IOException;
import java.io.StringReader;
import java.sql.SQLException;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBPreparedStatement;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.md.Types;
import msfx.lib.util.json.JSONObject;
import msfx.lib.util.json.JSONParser;

/**
 * Tests {@link DBPreparedStatement#setBoolean(int, boolean)} and
 * {@link DBPreparedStatement#setObject(int, JSONObject)} on the local SQL
 * Server: the parameters of a select that reads no table are read back, as the
 * text sent and through the getters of the result set.
 *
 * @author Miquel Sas
 */
public class TestPreparedStatementSetters {

	/**
	 * Number of checks that failed.
	 */
	private static int failed = 0;
	/**
	 * Number of checks that passed.
	 */
	private static int passed = 0;

	/**
	 * Prints the result of a check and counts it.
	 *
	 * @param title    What is checked.
	 * @param result   The result.
	 * @param expected The expected result.
	 */
	private static void check(String title, String result, String expected) {
		boolean ok = expected.equals(result);
		if (ok) {
			passed++;
		} else {
			failed++;
		}
		StringBuilder line = new StringBuilder();
		line.append(ok ? "   ok    " : "   FAIL  ");
		line.append(title);
		line.append(": ");
		line.append(result);
		if (!ok) {
			line.append(", expected ");
			line.append(expected);
		}
		System.out.println(line);
	}

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 * @throws IOException If the test object can not be parsed.
	 */
	public static void main(String[] args) throws IOException {
		DBEngineSQLServer db = new DBEngineSQLServer();
		db.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");

		StringReader source = new StringReader("{\"CCOMPANY\":\"LCZ\",\"QSALES\":10.5}");
		JSONObject obj = new JSONParser().parse(source);

		StringBuilder sql = new StringBuilder();
		sql.append("SELECT CAST(? AS VARCHAR(1)) AS BTRUE, CAST(? AS VARCHAR(1)) AS BFALSE, ");
		sql.append("CAST(? AS NVARCHAR(MAX)) AS OTEXT, CAST(? AS NVARCHAR(MAX)) AS OVALUE, ");
		sql.append("CAST(? AS NVARCHAR(MAX)) AS ONULL, CAST(? AS VARCHAR(1)) AS BNULL");

		try (DBConnection cn = db.getConnection();
				DBPreparedStatement ps = cn.prepareStatement(sql.toString())) {
			ps.setBoolean(1, true);
			ps.setBoolean(2, false);
			ps.setObject(3, obj);
			ps.setObject(4, obj);
			ps.setObject(5, null);
			ps.setNull(6, Types.BOOLEAN);
			try (DBResultSet rs = ps.executeQuery()) {
				rs.next();
				String bTrue = rs.getString(1);
				String bFalse = rs.getString(2);
				check("setBoolean(true) sends", bTrue, "Y");
				check("setBoolean(false) sends", bFalse, "N");
				check("read back with getBoolean", String.valueOf(rs.getBoolean("BTRUE")), "true");
				String text = rs.getString(3);
				check("setObject sends the JSON text", text, "{\"CCOMPANY\":\"LCZ\",\"QSALES\":10.5}");
				JSONObject read = rs.getObject(4);
				check("read back with getObject", String.valueOf(read.keys()), "[CCOMPANY, QSALES]");
				JSONObject none = rs.getObject(5);
				check("setObject(null) sends NULL", String.valueOf(none), "null");
				String bNull = rs.getString(6);
				check("setNull(BOOLEAN) sends NULL", String.valueOf(bNull), "null");
			}
		} catch (SQLException exc) {
			check("Select", exc.getMessage(), "no error");
		}

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
