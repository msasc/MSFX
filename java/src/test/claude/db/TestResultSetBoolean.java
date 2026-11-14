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

/**
 * Tests {@link DBResultSet#getBoolean(int)} and
 * {@link DBResultSet#getBoolean(String)} on the local SQL Server, over a
 * select of literals and bits that reads no table: Y/N, 1/0 and T/F in any
 * case and bits are read, anything else and null are false.
 *
 * @author Miquel Sas
 */
public class TestResultSetBoolean {

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {
		DBEngineSQLServer db = new DBEngineSQLServer();
		db.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");

		String[] exprs = {
				"'Y'", "'y'", "'N'", "'n'",
				"'1'", "'0'",
				"'T'", "'t'", "'F'", "'f'",
				"CAST(1 AS BIT)", "CAST(0 AS BIT)",
				"'S'", "'2'", "''", "' '", "NULL" };
		boolean[] expected = {
				true, true, false, false,
				true, false,
				true, true, false, false,
				true, false,
				false, false, false, false, false };

		StringBuilder sql = new StringBuilder();
		sql.append("SELECT ");
		for (int i = 0; i < exprs.length; i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append(exprs[i]);
			sql.append(" AS C");
			sql.append(i + 1);
		}

		int passed = 0;
		int failed = 0;
		try (DBConnection cn = db.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql.toString())) {
			rs.next();
			for (int i = 0; i < exprs.length; i++) {
				StringBuilder alias = new StringBuilder();
				alias.append("C");
				alias.append(i + 1);
				boolean byIndex = rs.getBoolean(i + 1);
				boolean byAlias = rs.getBoolean(alias.toString());
				boolean ok = byIndex == expected[i] && byAlias == expected[i];
				if (ok) {
					passed++;
				} else {
					failed++;
				}
				StringBuilder line = new StringBuilder();
				line.append(ok ? "   ok    " : "   FAIL  ");
				line.append(exprs[i]);
				line.append(" -> ");
				line.append(byIndex);
				line.append(" / ");
				line.append(byAlias);
				if (!ok) {
					line.append(", expected ");
					line.append(expected[i]);
				}
				System.out.println(line);
			}
		} catch (SQLException exc) {
			failed++;
			StringBuilder line = new StringBuilder();
			line.append("   FAIL  ");
			line.append(exc.getMessage());
			System.out.println(line);
		}

		String trueSQL = db.getLiteralSQL(true);
		String falseSQL = db.getLiteralSQL(false);
		StringBuilder roundTrip = new StringBuilder();
		roundTrip.append("SELECT ");
		roundTrip.append(trueSQL);
		roundTrip.append(" AS T, ");
		roundTrip.append(falseSQL);
		roundTrip.append(" AS F");
		try (DBConnection cn = db.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(roundTrip.toString())) {
			rs.next();
			boolean readTrue = rs.getBoolean(1);
			boolean readFalse = rs.getBoolean(2);
			boolean ok = trueSQL.equals("'Y'") && falseSQL.equals("'N'") && readTrue && !readFalse;
			if (ok) {
				passed++;
			} else {
				failed++;
			}
			StringBuilder line = new StringBuilder();
			line.append(ok ? "   ok    " : "   FAIL  ");
			line.append("Literals written ");
			line.append(trueSQL);
			line.append(" / ");
			line.append(falseSQL);
			line.append(", read ");
			line.append(readTrue);
			line.append(" / ");
			line.append(readFalse);
			System.out.println(line);
		} catch (SQLException exc) {
			failed++;
			StringBuilder line = new StringBuilder();
			line.append("   FAIL  ");
			line.append(exc.getMessage());
			System.out.println(line);
		}

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
