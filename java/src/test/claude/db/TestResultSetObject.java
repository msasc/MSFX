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

import java.sql.SQLException;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.util.json.JSONObject;

/**
 * Tests {@link DBResultSet#getObject(int)} and
 * {@link DBResultSet#getObject(String)} on the local SQL Server, over a select
 * of literals cast to NVARCHAR(MAX), the type of OBJECT columns, that reads no
 * table: a JSON object, an empty text, a null and malformed texts.
 *
 * @author Miquel Sas
 */
public class TestResultSetObject {

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
	 * @param ok     Whether the check passed.
	 * @param title  What is checked.
	 * @param result The result.
	 */
	private static void report(boolean ok, String title, String result) {
		if (ok) {
			passed++;
		} else {
			failed++;
		}
		StringBuilder line = new StringBuilder();
		line.append(ok ? "   ok    " : "   FAIL  ");
		line.append(title);
		line.append("\n");
		line.append(result.indent(9));
		System.out.print(line);
	}

	/**
	 * Reads a value by index and its copy by alias, since a streamed column is
	 * read only once, and describes the result: the object with its keys, null,
	 * or the error and its cause.
	 *
	 * @param rs    The result set.
	 * @param index The index of the value, read from column C[index], its copy
	 *              read from column A[index].
	 * @return The description of the reads by index and by alias.
	 */
	private static String read(DBResultSet rs, int index) {
		StringBuilder alias = new StringBuilder();
		alias.append("A");
		alias.append(index);
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 2; i++) {
			if (i > 0) {
				b.append("\n");
			}
			try {
				JSONObject obj = i == 0 ? rs.getObject(index) : rs.getObject(alias.toString());
				if (obj == null) {
					b.append("null");
				} else {
					b.append(obj.size());
					b.append(" keys ");
					b.append(obj.keys());
				}
			} catch (SQLException exc) {
				b.append("SQLException: ");
				b.append(exc.getMessage());
				b.append(" / cause ");
				b.append(exc.getCause() == null ? "none" : exc.getCause().getClass().getSimpleName());
			}
		}
		return b.toString();
	}

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {
		DBEngineSQLServer db = new DBEngineSQLServer();
		db.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");

		String[] texts = {
				"N'{\"CCOMPANY\":\"LCZ\",\"QSALES\":10.5,\"ITEMS\":[1,2]}'",
				"N''",
				"NULL",
				"N'{\"CCOMPANY\":'",
				"N'[1,2]'" };
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT ");
		for (int i = 0; i < texts.length; i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append("CAST(");
			sql.append(texts[i]);
			sql.append(" AS NVARCHAR(MAX)) AS C");
			sql.append(i + 1);
		}
		for (int i = 0; i < texts.length; i++) {
			sql.append(", CAST(");
			sql.append(texts[i]);
			sql.append(" AS NVARCHAR(MAX)) AS A");
			sql.append(i + 1);
		}

		try (DBConnection cn = db.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql.toString())) {
			rs.next();
			String result = read(rs, 1);
			report(result.equals("3 keys [CCOMPANY, QSALES, ITEMS]\n3 keys [CCOMPANY, QSALES, ITEMS]"),
					"JSON object", result);
			result = read(rs, 2);
			report(result.equals("0 keys []\n0 keys []"), "Empty text: empty object", result);
			result = read(rs, 3);
			report(result.equals("null\nnull"), "Null: null", result);
			result = read(rs, 4);
			report(result.startsWith("SQLException: The value of column 4 is not a JSON object")
					&& result.contains("SQLException: The value of column A4 is not a JSON object")
					&& result.endsWith("cause IOException"), "Malformed text: SQLException with the parse error as cause", result);
			result = read(rs, 5);
			report(result.startsWith("SQLException: The value of column 5 is not a JSON object")
					&& result.endsWith("cause IOException"), "An array: not an object", result);
		} catch (SQLException exc) {
			report(false, "Select", exc.getMessage());
		}

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
