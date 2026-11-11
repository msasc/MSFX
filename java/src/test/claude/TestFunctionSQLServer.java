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

package test.claude;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.db.query.sa.Function;

/**
 * Checks the SQL Server translations of the functions against the local
 * database, with SELECTs of literal expressions only: no tables are read or
 * changed.
 *
 * @author Miquel Sas
 */
public class TestFunctionSQLServer {

	/**
	 * The database engine.
	 */
	private static final DBEngineSQLServer db = new DBEngineSQLServer();
	/**
	 * Number of checks that failed.
	 */
	private static int failed = 0;
	/**
	 * Number of checks that passed.
	 */
	private static int passed = 0;

	/**
	 * Selects the function applied to the arguments and compares the result,
	 * read as a string, with the expected one. A query that fails counts as a
	 * failed check, with the error as result.
	 *
	 * @param function  The function.
	 * @param expected  The expected result, or null.
	 * @param arguments The arguments, written in SQL.
	 */
	private static void check(Function function, String expected, String... arguments) {
		String expr = db.getFunctionSQL(function, List.of(arguments));
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT ");
		sql.append(expr);
		String result;
		try (DBConnection cn = db.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql.toString())) {
			rs.next();
			result = rs.getString(1);
		} catch (SQLException exc) {
			StringBuilder error = new StringBuilder();
			error.append("error: ");
			error.append(exc.getMessage());
			result = error.toString();
		}
		boolean ok = (expected == null ? result == null : expected.equals(result));
		if (ok) {
			passed++;
		} else {
			failed++;
		}
		StringBuilder line = new StringBuilder();
		line.append(ok ? "   ok    " : "   FAIL  ");
		line.append(function);
		line.append(" ");
		line.append(List.of(arguments));
		line.append(" -> [");
		line.append(result);
		line.append("]");
		if (!ok) {
			line.append(" expected [");
			line.append(expected);
			line.append("]");
		}
		line.append("    ");
		line.append(expr);
		System.out.println(line);
	}

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {
		db.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");

		System.out.println("== Strings");
		check(Function.LENGTH, "5", "N'ab   '");
		check(Function.LENGTH, "3", "N'   '");
		check(Function.LENGTH, "0", "N''");
		check(Function.LENGTH, "0", "CAST(NULL AS NVARCHAR(10))");
		check(Function.LEFT, "ab", "N'abcd'", "2");
		check(Function.RIGHT, "abcd", "N'abcd'", "9");
		check(Function.POSITION, "3", "N'abcd'", "N'cd'");
		check(Function.POSITION, "0", "N'abcd'", "N'x'");
		check(Function.LPAD, "000ab", "N'ab'", "5", "N'0'");
		check(Function.LPAD, "abc", "N'abcdef'", "3", "N'0'");
		check(Function.LPAD, "   ab", "N'ab'", "5");
		check(Function.RPAD, "ab000", "N'ab'", "5", "N'0'");
		check(Function.RPAD, "abc", "N'abcdef'", "3", "N'0'");
		check(Function.RPAD, "ab   ", "N'ab'", "5");

		System.out.println("== Numbers");
		check(Function.ROUND, "3.000", "2.567");
		check(Function.ROUND, "2.570", "2.567", "2");
		check(Function.TRUNCATE, "2.000", "2.567");
		check(Function.TRUNCATE, "2.560", "2.567", "2");
		check(Function.MOD, "1", "7", "2 * 3");

		System.out.println("== Dates and times");
		check(Function.CURRENT_DATE, LocalDate.now().toString());
		check(Function.HOUR, "13", "CAST('2026-11-11T13:45:30' AS DATETIME2)");
		check(Function.SECOND, "30", "CAST('2026-11-11T13:45:30.7' AS DATETIME2)");
		check(Function.ADD_DAYS, "2026-11-13", "CAST('2026-11-11' AS DATE)", "2");
		check(Function.ADD_MONTHS, "2027-01-11", "CAST('2026-11-11' AS DATE)", "2");
		check(Function.DAYS_BETWEEN, "3", "CAST('2026-11-11T23:00:00' AS DATETIME2)", "CAST('2026-11-14T01:00:00' AS DATETIME2)");

		System.out.println("== Conversions");
		check(Function.TO_NUMBER, "12.5000000000", "N'12.5'");
		check(Function.TO_DATE, "2026-11-11", "N'2026-11-11'");
		check(Function.TO_TIMESTAMP, "2026-11-11 13:45:30.1230000", "N'2026-11-11T13:45:30.123'");
		check(Function.TO_TIMESTAMP, "2026-11-11 13:45:30.0000000", "N'2026-11-11T13:45:30'");
		check(Function.TO_TIMESTAMP, "2026-11-11 13:45:00.0000000", "N'2026-11-11T13:45'");
		check(Function.TO_TIMESTAMP, "2026-11-11 13:45:00.0000000", "N'2026-11-11 13:45'");
		check(Function.TO_TIMESTAMP, "2026-11-11 13:45:30.1230000", "N'2026-11-11 13:45:30.123'");

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
