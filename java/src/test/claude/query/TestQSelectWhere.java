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
import static msfx.lib.db.query.Logical.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

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
 * Tests the WHERE clause of {@link QSelect}, through its toString(): groups,
 * logical operators, the parentheses written, the conditions composed from
 * comparisons and IN, placeholders, and the errors when adding conditions.
 * Nothing is executed, except an optional run on the local SQL Server over a
 * derived table of literals, that reads no table.
 *
 * @author Miquel Sas
 */
public class TestQSelectWhere {

	/**
	 * SQL Server engine.
	 */
	private static final DBEngineSQLServer SQLSERVER = new DBEngineSQLServer();
	/**
	 * Oracle engine.
	 */
	private static final DBEngine ORACLE = new DBEngineOracle();
	/**
	 * FROM and join written for SQL Server.
	 */
	private static final String FROM_JOIN = "FROM [dbo].[SALES] [S]\nLEFT JOIN [dbo].[ARTICLES] [A] ON [S].[CARTICLE] = [A].[CARTICLE]";
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
	 * The table ARTICLES.
	 */
	private static Table articles;

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
	 * Returns a select from SALES S.
	 *
	 * @param db The database engine.
	 * @return The select.
	 */
	private static QSelect fromSales(DBEngine db) {
		QSelect select = new QSelect(db);
		select.from(sales, "S");
		return select;
	}

	/**
	 * Returns a select from SALES S joined to ARTICLES A.
	 *
	 * @param db The database engine.
	 * @return The select.
	 */
	private static QSelect joined(DBEngine db) {
		QSelect select = fromSales(db);
		select.leftJoin(articles, "A", "S.CARTICLE = A.CARTICLE");
		return select;
	}

	/**
	 * Entry point.
	 *
	 * @param args Pass "db" to also run a select with a WHERE clause on the
	 *             local SQL Server.
	 */
	public static void main(String[] args) {

		sales = new Table("SALES");
		sales.setSchema("dbo");
		sales.columns().add(new Column("CCOMPANY", Types.STRING, 10, -1));
		sales.columns().add(new Column("CARTICLE", Types.STRING, 40, -1));
		sales.columns().add(new Column("DSALE", Types.DATE, -1, -1));
		sales.columns().add(new Column("QSALES", Types.DECIMAL, 16, 2));
		sales.columns().add(new Column("QCOST", Types.DECIMAL, 16, 2));

		articles = new Table("ARTICLES");
		articles.setSchema("dbo");
		articles.columns().add(new Column("CARTICLE", Types.STRING, 40, -1));
		articles.columns().add(new Column("CTYPE", Types.STRING, 10, -1));

		System.out.println("== One group");
		QSelect select = fromSales(SQLSERVER);
		select.whereCond("S.CCOMPANY = 'LCZ'");
		sql("Single raw condition", select, lines(
				"FROM [dbo].[SALES] [S]",
				"WHERE [S].[CCOMPANY] = N'LCZ'"));

		select = joined(SQLSERVER);
		select.whereValue("S.CCOMPANY", EQ, "LCZ");
		select.whereValue(AND, "S.QSALES", GT, 1000);
		select.whereExpr(AND, "S.QSALES", GE, "S.QCOST * 2");
		select.whereIn(AND_NOT, "A.CTYPE", new Value("X"), new Value("Y"));
		sql("Value, expression and NOT IN", select, lines(
				FROM_JOIN,
				"WHERE [S].[CCOMPANY] = N'LCZ' AND [S].[QSALES] > 1000 AND [S].[QSALES] >= [S].[QCOST] * 2 AND NOT [A].[CTYPE] IN (N'X', N'Y')"));

		select = fromSales(SQLSERVER);
		select.whereCond("S.CCOMPANY = 'LCZ' OR S.CCOMPANY = 'ABC'");
		select.whereValue(AND, "S.QSALES", NE, new BigDecimal("0.50"));
		sql("Condition with OR wrapped when not alone", select, lines(
				"FROM [dbo].[SALES] [S]",
				"WHERE ([S].[CCOMPANY] = N'LCZ' OR [S].[CCOMPANY] = N'ABC') AND [S].[QSALES] <> 0.50"));

		select = fromSales(SQLSERVER);
		select.whereCond("S.CCOMPANY = 'LCZ' OR S.CCOMPANY = 'ABC'");
		sql("Condition with OR alone, not wrapped", select, lines(
				"FROM [dbo].[SALES] [S]",
				"WHERE [S].[CCOMPANY] = N'LCZ' OR [S].[CCOMPANY] = N'ABC'"));

		select = fromSales(SQLSERVER);
		select.whereCond("S.QSALES BETWEEN ? AND ?", new Value(100), new Value(new BigDecimal("999.99")));
		select.whereValue(AND, "S.DSALE", LT, new Value(LocalDate.of(2026, 1, 1)));
		sql("Placeholders and a date", select, lines(
				"FROM [dbo].[SALES] [S]",
				"WHERE [S].[QSALES] BETWEEN 100 AND 999.99 AND [S].[DSALE] < '2026-01-01'"));

		System.out.println("== Groups");
		select = joined(SQLSERVER);
		select.whereValue("S.CCOMPANY", EQ, "LCZ");
		select.whereValue(AND, "S.QSALES", GT, 1000);
		select.whereGroup(OR);
		select.whereIn("A.CTYPE", new Value("A"), new Value("B"));
		select.whereValue(AND_NOT, "S.QCOST", EQ, 0);
		sql("Two groups of two conditions", select, lines(
				FROM_JOIN,
				"WHERE ([S].[CCOMPANY] = N'LCZ' AND [S].[QSALES] > 1000)",
				"OR ([A].[CTYPE] IN (N'A', N'B') AND NOT [S].[QCOST] = 0)"));

		select = fromSales(SQLSERVER);
		select.whereValue("S.CCOMPANY", EQ, "LCZ");
		select.whereGroup(OR_NOT);
		select.whereCond("S.QSALES > 0 AND S.QCOST > 0");
		sql("Groups of one condition, an AND condition wrapped", select, lines(
				"FROM [dbo].[SALES] [S]",
				"WHERE [S].[CCOMPANY] = N'LCZ'",
				"OR NOT ([S].[QSALES] > 0 AND [S].[QCOST] > 0)"));

		select = fromSales(SQLSERVER);
		select.whereValue("S.CCOMPANY", EQ, "LCZ");
		select.whereGroup(AND);
		sql("Last group still empty, not written", select, lines(
				"FROM [dbo].[SALES] [S]",
				"WHERE [S].[CCOMPANY] = N'LCZ'"));

		select = fromSales(ORACLE);
		select.whereValue("S.CCOMPANY", EQ, "LCZ");
		select.whereValue(AND, "S.DSALE", GE, new Value(LocalDate.of(2026, 1, 1)));
		select.whereGroup(OR);
		select.whereValue("S.QSALES", LT, 0.5);
		sql("Oracle", select, lines(
				"FROM \"dbo\".\"SALES\" \"S\"",
				"WHERE (\"S\".\"CCOMPANY\" = N'LCZ' AND \"S\".\"DSALE\" >= DATE '2026-01-01')",
				"OR \"S\".\"QSALES\" < 0.5"));

		System.out.println("== NOT in the first condition of a group");
		select = fromSales(SQLSERVER);
		select.whereIn(NOT, "S.CCOMPANY", new Value("LCZ"), new Value("ABC"));
		sql("NOT IN alone", select, lines(
				"FROM [dbo].[SALES] [S]",
				"WHERE NOT [S].[CCOMPANY] IN (N'LCZ', N'ABC')"));

		select = fromSales(SQLSERVER);
		select.whereCond(NOT, "S.QSALES > 0 OR S.QCOST > 0");
		sql("NOT before an OR alone: wrapped", select, lines(
				"FROM [dbo].[SALES] [S]",
				"WHERE NOT ([S].[QSALES] > 0 OR [S].[QCOST] > 0)"));

		select = fromSales(SQLSERVER);
		select.whereValue("S.CCOMPANY", EQ, "LCZ");
		select.whereGroup(OR);
		select.whereValue(NOT, "S.QSALES", GT, 0);
		select.whereValue(AND, "S.QCOST", GT, 0);
		sql("NOT in the first condition of a second group", select, lines(
				"FROM [dbo].[SALES] [S]",
				"WHERE [S].[CCOMPANY] = N'LCZ'",
				"OR (NOT [S].[QSALES] > 0 AND [S].[QCOST] > 0)"));

		System.out.println("== Errors");
		error("NOT after the first condition", IllegalStateException.class, () -> {
			QSelect s = fromSales(SQLSERVER);
			s.whereCond("S.QSALES > 0");
			s.whereCond(NOT, "S.QCOST > 0");
		});
		error("NOT linking a group", IllegalArgumentException.class, () -> {
			QSelect s = fromSales(SQLSERVER);
			s.whereCond("S.QSALES > 0");
			s.whereGroup(NOT);
		});
		error("Condition before FROM", IllegalStateException.class, () -> {
			new QSelect(SQLSERVER).whereCond("S.QSALES > 0");
		});
		error("Logical operator in the first condition", IllegalStateException.class, () -> {
			fromSales(SQLSERVER).whereCond(AND, "S.QSALES > 0");
		});
		error("No logical operator after the first condition", IllegalStateException.class, () -> {
			QSelect s = fromSales(SQLSERVER);
			s.whereCond("S.QSALES > 0");
			s.whereCond("S.QCOST > 0");
		});
		error("Null logical operator", NullPointerException.class, () -> {
			QSelect s = fromSales(SQLSERVER);
			s.whereCond("S.QSALES > 0");
			s.whereCond((msfx.lib.db.query.Logical) null, "S.QCOST > 0");
		});
		error("Group after an empty group", IllegalStateException.class, () -> {
			fromSales(SQLSERVER).whereGroup(OR);
		});
		error("Column of a table not joined", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).whereValue("A.CTYPE", EQ, "X");
		});
		error("Aggregate in WHERE", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).whereValue("SUM(S.QSALES)", GT, 0);
		});
		error("Value instead of a condition", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).whereCond("S.QSALES * 2");
		});
		error("Comparison operand with an OR", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).whereExpr("S.QSALES > 0 OR S.QCOST", GT, "1");
		});
		error("Missing placeholder value", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).whereCond("S.QSALES > ?");
		});
		error("Null value", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).whereValue("S.CCOMPANY", EQ, (String) null);
		});
		error("IN without values", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).whereIn("S.CCOMPANY");
		});

		select = fromSales(SQLSERVER);
		select.whereValue("S.CCOMPANY", EQ, "LCZ");
		String before = select.toString();
		try {
			select.whereValue(AND, "S.UNKNOWN", EQ, 1);
		} catch (IllegalArgumentException exc) {
			// expected
		}
		sql("A rejected condition leaves the select as it was", select, before);

		if (args.length > 0 && args[0].equals("db")) {
			System.out.println("== Run on SQL Server, over a derived table of literals");
			SQLSERVER.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");
			StringBuilder rows = new StringBuilder();
			rows.append("FROM (SELECT N'LCZ' AS [CCOMPANY], N'A1' AS [CARTICLE], CAST('2025-06-01' AS DATE) AS [DSALE], ");
			rows.append("1500.00 AS [QSALES], 100.00 AS [QCOST] ");
			rows.append("UNION ALL SELECT N'ABC', N'A2', CAST('2026-02-01' AS DATE), 50.00, 0.00 ");
			rows.append("UNION ALL SELECT N'XYZ', N'A3', CAST('2025-01-01' AS DATE), 10.00, 5.00) AS [S]");
			select = fromSales(SQLSERVER);
			select.addColumn("S.CARTICLE", "CARTICLE");
			select.whereValue("S.CCOMPANY", EQ, "LCZ");
			select.whereValue(AND, "S.DSALE", LT, new Value(LocalDate.of(2026, 1, 1)));
			select.whereExpr(AND, "S.QSALES", GE, "S.QCOST * 2");
			select.whereGroup(OR);
			select.whereIn("S.CCOMPANY", new Value("ABC"), new Value("DEF"));
			select.whereValue(AND_NOT, "S.QCOST", NE, 0);
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
				}
			} catch (SQLException exc) {
				result.append(exc.getMessage());
			}
			report(result.toString().equals("A1, A2"), "Rows selected", result.toString(), "A1, A2");
		}

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
