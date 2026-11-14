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

package test.claude.query;

import static msfx.lib.db.query.Comparison.*;

import java.sql.SQLException;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBEngine;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.adapters.DBEngineOracle;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.db.query.QSelect;
import msfx.lib.md.Column;
import msfx.lib.md.Table;
import msfx.lib.md.Types;

/**
 * Tests the ORDER BY clause of {@link QSelect}, by aliases of the select list,
 * through its toString(). Nothing is executed, except an optional run on the
 * local SQL Server over a derived table of literals, that reads no table.
 *
 * @author Miquel Sas
 */
public class TestQSelectOrderBy {

	/**
	 * SQL Server engine.
	 */
	private static final DBEngineSQLServer SQLSERVER = new DBEngineSQLServer();
	/**
	 * Oracle engine.
	 */
	private static final DBEngine ORACLE = new DBEngineOracle();
	/**
	 * Number of checks that failed.
	 */
	private static int failed = 0;
	/**
	 * Number of checks that passed.
	 */
	private static int passed = 0;
	/**
	 * The table SALES.
	 */
	private static Table sales;

	/**
	 * Prints the result of a check and counts it.
	 *
	 * @param ok       Whether the check passed.
	 * @param title    What is checked.
	 * @param result   The result.
	 * @param expected The expected result.
	 */
	private static void report(boolean ok, String title, String result, String expected) {
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
		if (!ok) {
			line.append("         expected:\n");
			line.append(expected.indent(9));
		}
		System.out.print(line);
	}

	/**
	 * Joins lines with new lines, as the select writes its clauses.
	 *
	 * @param lines The lines.
	 * @return The text.
	 */
	private static String lines(String... lines) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				b.append("\n");
			}
			b.append(lines[i]);
		}
		return b.toString();
	}

	/**
	 * Compares the SQL of a select with the expected one.
	 *
	 * @param title    What is checked.
	 * @param select   The select.
	 * @param expected The expected SQL.
	 */
	private static void sql(String title, QSelect select, String expected) {
		String result = select.toString();
		report(expected.equals(result), title, result, expected);
	}

	/**
	 * Runs an action that must fail with an exception of the expected class,
	 * and prints its message.
	 *
	 * @param title    What is checked.
	 * @param expected The expected class of exception.
	 * @param action   The action.
	 */
	private static void error(String title, Class<? extends Exception> expected, Runnable action) {
		String result;
		boolean ok;
		try {
			action.run();
			result = "no error";
			ok = false;
		} catch (Exception exc) {
			StringBuilder b = new StringBuilder();
			b.append(exc.getClass().getSimpleName());
			b.append(": ");
			b.append(exc.getMessage());
			result = b.toString();
			ok = expected.isInstance(exc);
		}
		report(ok, title, result, expected.getSimpleName());
	}

	/**
	 * Returns a select of the sales by year and month, grouped.
	 *
	 * @param db The database engine.
	 * @return The select.
	 */
	private static QSelect byMonth(DBEngine db) {
		QSelect select = new QSelect(db);
		select.from(sales, "S");
		select.addColumn("S.NYEAR", "NYEAR");
		select.addColumn("S.NMONTH", "NMONTH");
		select.addColumn("SUM(S.QSALES)", "QSALES");
		select.groupBy();
		return select;
	}

	/**
	 * Entry point.
	 *
	 * @param args Pass "db" to also run an ordered select on the local SQL
	 *             Server.
	 */
	public static void main(String[] args) {

		sales = new Table("SALES");
		sales.setSchema("dbo");
		sales.columns().add(new Column("NYEAR", Types.INTEGER, -1, -1));
		sales.columns().add(new Column("NMONTH", Types.INTEGER, -1, -1));
		sales.columns().add(new Column("QSALES", Types.DECIMAL, 16, 2));

		System.out.println("== ORDER BY");
		QSelect select = byMonth(SQLSERVER);
		select.orderBy("NYEAR", "NMONTH DESC");
		sql("Ascending and descending", select, lines(
				"SELECT [S].[NYEAR] AS [NYEAR], [S].[NMONTH] AS [NMONTH], SUM([S].[QSALES]) AS [QSALES]",
				"FROM [dbo].[SALES] [S]",
				"GROUP BY [S].[NYEAR], [S].[NMONTH]",
				"ORDER BY [NYEAR], [NMONTH] DESC"));

		select = byMonth(SQLSERVER);
		select.whereValue("S.NYEAR", GE, 2025);
		select.orderBy("  qsales   desc ", "nyear ASC");
		sql("Aliases ignoring case, ASC not written, spaces trimmed, after WHERE", select, lines(
				"SELECT [S].[NYEAR] AS [NYEAR], [S].[NMONTH] AS [NMONTH], SUM([S].[QSALES]) AS [QSALES]",
				"FROM [dbo].[SALES] [S]",
				"WHERE [S].[NYEAR] >= 2025",
				"GROUP BY [S].[NYEAR], [S].[NMONTH]",
				"ORDER BY [QSALES] DESC, [NYEAR]"));

		select = byMonth(ORACLE);
		select.orderBy("NYEAR DESC");
		sql("Oracle", select, lines(
				"SELECT \"S\".\"NYEAR\" AS \"NYEAR\", \"S\".\"NMONTH\" AS \"NMONTH\", SUM(\"S\".\"QSALES\") AS \"QSALES\"",
				"FROM \"dbo\".\"SALES\" \"S\"",
				"GROUP BY \"S\".\"NYEAR\", \"S\".\"NMONTH\"",
				"ORDER BY \"NYEAR\" DESC"));

		System.out.println("== Errors");
		error("Unknown alias", IllegalArgumentException.class, () -> {
			byMonth(SQLSERVER).orderBy("NDAY");
		});
		error("Wrong direction", IllegalArgumentException.class, () -> {
			byMonth(SQLSERVER).orderBy("NYEAR DOWN");
		});
		error("Too many words", IllegalArgumentException.class, () -> {
			byMonth(SQLSERVER).orderBy("NYEAR DESC NULLS");
		});
		error("Column repeated", IllegalArgumentException.class, () -> {
			byMonth(SQLSERVER).orderBy("NYEAR", "nyear DESC");
		});
		error("Blank segment", IllegalArgumentException.class, () -> {
			byMonth(SQLSERVER).orderBy("NYEAR", "  ");
		});
		error("No segments", IllegalArgumentException.class, () -> {
			byMonth(SQLSERVER).orderBy();
		});
		error("Ordered twice", IllegalStateException.class, () -> {
			QSelect s = byMonth(SQLSERVER);
			s.orderBy("NYEAR");
			s.orderBy("NMONTH");
		});

		if (args.length > 0 && args[0].equals("db")) {
			System.out.println("== Run on SQL Server, over a derived table of literals");
			SQLSERVER.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");
			StringBuilder rows = new StringBuilder();
			rows.append("FROM (SELECT 2025 AS [NYEAR], 11 AS [NMONTH], 10.00 AS [QSALES] ");
			rows.append("UNION ALL SELECT 2025, 12, 20.00 ");
			rows.append("UNION ALL SELECT 2026, 1, 5.00 ");
			rows.append("UNION ALL SELECT 2025, 12, 1.00) AS [S]");
			select = byMonth(SQLSERVER);
			select.orderBy("NYEAR", "NMONTH DESC");
			String sql = select.toString().replace("FROM [dbo].[SALES] [S]", rows.toString());
			StringBuilder result = new StringBuilder();
			try (DBConnection cn = SQLSERVER.getConnection();
					DBStatement st = cn.createStatement();
					DBResultSet rs = st.executeQuery(sql)) {
				while (rs.next()) {
					if (!result.isEmpty()) {
						result.append(", ");
					}
					result.append(rs.getString(1));
					result.append("/");
					result.append(rs.getString(2));
					result.append(" ");
					result.append(rs.getString(3));
				}
			} catch (SQLException exc) {
				result.append(exc.getMessage());
			}
			String expected = "2025/12 21.00, 2025/11 10.00, 2026/1 5.00";
			report(result.toString().equals(expected), "Rows selected", result.toString(), expected);
		}

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
