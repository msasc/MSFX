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
import msfx.lib.md.Value;

/**
 * Tests the HAVING condition of {@link QSelect}, through its toString():
 * conditions on the aggregates of the select list only. Nothing is executed,
 * except an optional run on the local SQL Server over a derived table of
 * literals, that reads no table.
 *
 * @author Miquel Sas
 */
public class TestQSelectHaving {

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
	 * The table MSALESORIG.
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
	 * Returns the sales of 2026 by company, grouped.
	 *
	 * @param db The database engine.
	 * @return The select.
	 */
	private static QSelect byCompany(DBEngine db) {
		QSelect select = new QSelect(db);
		select.from(sales, "S");
		select.addColumn("S.CCOMPANY", "CCOMPANY");
		select.addColumn("SUM(S.QSALES)", "QSALES");
		select.addColumn("SUM(S.QCOST)", "QCOST");
		select.whereValue("S.NYEAR", EQ, 2026);
		select.groupBy();
		return select;
	}

	/**
	 * Entry point.
	 *
	 * @param args Pass "db" to also run a select with HAVING on the local SQL
	 *             Server.
	 */
	public static void main(String[] args) {

		sales = new Table("MSALESORIG");
		sales.setSchema("EXPLMARG");
		sales.columns().add(new Column("CCOMPANY", Types.STRING, 10, -1));
		sales.columns().add(new Column("NYEAR", Types.INTEGER, -1, -1));
		sales.columns().add(new Column("QSALES", Types.DECIMAL, 16, 2));
		sales.columns().add(new Column("QCOST", Types.DECIMAL, 16, 2));

		System.out.println("== HAVING");
		QSelect select = byCompany(SQLSERVER);
		select.having("SUM(S.QSALES) > ?", new Value(1000));
		select.orderBy("QSALES DESC");
		sql("Aggregate with a placeholder, before ORDER BY", select, lines(
				"SELECT [S].[CCOMPANY] AS [CCOMPANY], SUM([S].[QSALES]) AS [QSALES], SUM([S].[QCOST]) AS [QCOST]",
				"FROM [EXPLMARG].[MSALESORIG] [S]",
				"WHERE [S].[NYEAR] = 2026",
				"GROUP BY [S].[CCOMPANY]",
				"HAVING SUM([S].[QSALES]) > 1000",
				"ORDER BY [QSALES] DESC"));

		select = byCompany(SQLSERVER);
		select.having("sum(qsales) * 2 > SUM(S.QCOST) OR SUM(S.QSALES) - SUM(S.QCOST) > 0");
		sql("Arithmetic around aggregates, matched ignoring case and qualification", select, lines(
				"SELECT [S].[CCOMPANY] AS [CCOMPANY], SUM([S].[QSALES]) AS [QSALES], SUM([S].[QCOST]) AS [QCOST]",
				"FROM [EXPLMARG].[MSALESORIG] [S]",
				"WHERE [S].[NYEAR] = 2026",
				"GROUP BY [S].[CCOMPANY]",
				"HAVING SUM([S].[QSALES]) * 2 > SUM([S].[QCOST]) OR SUM([S].[QSALES]) - SUM([S].[QCOST]) > 0"));

		select = byCompany(ORACLE);
		select.having("SUM(S.QSALES) BETWEEN ? AND ?", new Value(100), new Value(5000));
		sql("Oracle", select, lines(
				"SELECT \"S\".\"CCOMPANY\" AS \"CCOMPANY\", SUM(\"S\".\"QSALES\") AS \"QSALES\", SUM(\"S\".\"QCOST\") AS \"QCOST\"",
				"FROM \"EXPLMARG\".\"MSALESORIG\" \"S\"",
				"WHERE \"S\".\"NYEAR\" = 2026",
				"GROUP BY \"S\".\"CCOMPANY\"",
				"HAVING SUM(\"S\".\"QSALES\") BETWEEN 100 AND 5000"));

		System.out.println("== Errors");
		error("Plain column, it goes in WHERE", IllegalArgumentException.class, () -> {
			byCompany(SQLSERVER).having("S.CCOMPANY IN ('LCZ', 'RSA') AND SUM(S.QSALES) > 1000");
		});
		error("Aggregate not in the select list", IllegalArgumentException.class, () -> {
			byCompany(SQLSERVER).having("COUNT(*) > 10");
		});
		error("Function around an aggregate", IllegalArgumentException.class, () -> {
			byCompany(SQLSERVER).having("ROUND(SUM(S.QSALES), 2) > 0");
		});
		error("Value instead of a condition", IllegalArgumentException.class, () -> {
			byCompany(SQLSERVER).having("SUM(S.QSALES)");
		});
		error("Null value", IllegalArgumentException.class, () -> {
			byCompany(SQLSERVER).having("SUM(S.QSALES) > ?", (Value) null);
		});
		error("HAVING twice", IllegalStateException.class, () -> {
			QSelect s = byCompany(SQLSERVER);
			s.having("SUM(S.QSALES) > 0");
			s.having("SUM(S.QCOST) > 0");
		});

		if (args.length > 0 && args[0].equals("db")) {
			System.out.println("== Run on SQL Server, over a derived table of literals");
			SQLSERVER.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");
			StringBuilder rows = new StringBuilder();
			rows.append("FROM (SELECT N'LCZ' AS [CCOMPANY], 2026 AS [NYEAR], 800.00 AS [QSALES], 100.00 AS [QCOST] ");
			rows.append("UNION ALL SELECT N'LCZ', 2026, 300.00, 100.00 ");
			rows.append("UNION ALL SELECT N'RSA', 2026, 500.00, 50.00 ");
			rows.append("UNION ALL SELECT N'RSA', 2025, 900.00, 50.00) AS [S]");
			select = byCompany(SQLSERVER);
			select.having("SUM(S.QSALES) > ?", new Value(1000));
			String sql = select.toString().replace("FROM [EXPLMARG].[MSALESORIG] [S]", rows.toString());
			StringBuilder result = new StringBuilder();
			try (DBConnection cn = SQLSERVER.getConnection();
					DBStatement st = cn.createStatement();
					DBResultSet rs = st.executeQuery(sql)) {
				while (rs.next()) {
					if (!result.isEmpty()) {
						result.append(", ");
					}
					result.append(rs.getString(1));
					result.append(" ");
					result.append(rs.getString(2));
				}
			} catch (SQLException exc) {
				result.append(exc.getMessage());
			}
			report(result.toString().equals("LCZ 1100.00"), "Rows selected", result.toString(), "LCZ 1100.00");
		}

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
