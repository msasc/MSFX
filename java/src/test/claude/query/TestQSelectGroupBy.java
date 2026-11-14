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
 * Tests the GROUP BY clause of {@link QSelect}, generated from the select
 * list, through its toString(). Nothing is executed, except an optional run on
 * the local SQL Server over a derived table of literals, that reads no table.
 *
 * @author Miquel Sas
 */
public class TestQSelectGroupBy {

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
	 * Entry point.
	 *
	 * @param args Pass "db" to also run a grouped select on the local SQL
	 *             Server.
	 */
	public static void main(String[] args) {

		sales = new Table("SALES");
		sales.setSchema("dbo");
		sales.columns().add(new Column("CCOMPANY", Types.STRING, 10, -1));
		sales.columns().add(new Column("CARTICLE", Types.STRING, 40, -1));
		sales.columns().add(new Column("QSALES", Types.DECIMAL, 16, 2));

		articles = new Table("ARTICLES");
		articles.setSchema("dbo");
		articles.columns().add(new Column("CARTICLE", Types.STRING, 40, -1));
		articles.columns().add(new Column("CBUSINESS", Types.STRING, 10, -1));

		System.out.println("== GROUP BY");
		QSelect select = fromSales(SQLSERVER);
		select.addColumn("S.CCOMPANY", "CCOMPANY");
		select.addColumn("S.CARTICLE", "CARTICLE");
		select.groupBy();
		sql("No aggregates: all the expressions", select, lines(
				"SELECT [S].[CCOMPANY] AS [CCOMPANY], [S].[CARTICLE] AS [CARTICLE]",
				"FROM [dbo].[SALES] [S]",
				"GROUP BY [S].[CCOMPANY], [S].[CARTICLE]"));

		select = fromSales(SQLSERVER);
		select.leftJoin(articles, "A", "S.CARTICLE = A.CARTICLE");
		select.addColumn("S.CCOMPANY", "CCOMPANY");
		select.addColumn("A.CBUSINESS", "CBUSINESS");
		select.addColumn("SUM(S.QSALES)", "QSALES");
		select.addColumn("COUNT(*)", "NSALES");
		select.whereValue("S.QSALES", GT, 0);
		select.groupBy();
		sql("Aggregates left out, after WHERE", select, lines(
				"SELECT [S].[CCOMPANY] AS [CCOMPANY], [A].[CBUSINESS] AS [CBUSINESS], SUM([S].[QSALES]) AS [QSALES], COUNT(*) AS [NSALES]",
				"FROM [dbo].[SALES] [S]",
				"LEFT JOIN [dbo].[ARTICLES] [A] ON [S].[CARTICLE] = [A].[CARTICLE]",
				"WHERE [S].[QSALES] > 0",
				"GROUP BY [S].[CCOMPANY], [A].[CBUSINESS]"));

		select = fromSales(SQLSERVER);
		select.groupBy();
		select.addColumn("S.CCOMPANY || '-' || S.CARTICLE", "CODE");
		select.addColumn("1", "NREF");
		select.addColumn("MAX(S.QSALES)", "QMAX");
		sql("Expression grouped, constant left out, columns added after groupBy", select, lines(
				"SELECT CONCAT([S].[CCOMPANY], N'-', [S].[CARTICLE]) AS [CODE], 1 AS [NREF], MAX([S].[QSALES]) AS [QMAX]",
				"FROM [dbo].[SALES] [S]",
				"GROUP BY CONCAT([S].[CCOMPANY], N'-', [S].[CARTICLE])"));

		select = fromSales(SQLSERVER);
		select.addColumn("SUM(S.QSALES)", "QSALES");
		select.groupBy();
		sql("Only aggregates: no GROUP BY", select, lines(
				"SELECT SUM([S].[QSALES]) AS [QSALES]",
				"FROM [dbo].[SALES] [S]"));

		select = fromSales(SQLSERVER);
		select.addColumn("S.CCOMPANY", "CCOMPANY");
		sql("Not grouped", select, lines(
				"SELECT [S].[CCOMPANY] AS [CCOMPANY]",
				"FROM [dbo].[SALES] [S]"));

		select = fromSales(SQLSERVER);
		QSelect exists = new QSelect(select);
		exists.from(articles, "A");
		exists.addColumn("A.CBUSINESS", "CBUSINESS");
		exists.addColumn("S.CCOMPANY", "CCOMPANY");
		exists.addColumn("COUNT(*)", "NARTICLES");
		exists.whereExpr("A.CARTICLE", EQ, "S.CARTICLE");
		exists.groupBy();
		select.addColumn("S.CARTICLE", "CARTICLE");
		select.whereExists(exists);
		sql("EXISTS select grouped: outer columns left out", select, lines(
				"SELECT [S].[CARTICLE] AS [CARTICLE]",
				"FROM [dbo].[SALES] [S]",
				"WHERE EXISTS (",
				"\tSELECT [A].[CBUSINESS] AS [CBUSINESS], [S].[CCOMPANY] AS [CCOMPANY], COUNT(*) AS [NARTICLES]",
				"\tFROM [dbo].[ARTICLES] [A]",
				"\tWHERE [A].[CARTICLE] = [S].[CARTICLE]",
				"\tGROUP BY [A].[CBUSINESS]",
				")"));

		select = fromSales(ORACLE);
		select.addColumn("S.CCOMPANY", "CCOMPANY");
		select.addColumn("SUM(S.QSALES)", "QSALES");
		select.groupBy();
		sql("Oracle", select, lines(
				"SELECT \"S\".\"CCOMPANY\" AS \"CCOMPANY\", SUM(\"S\".\"QSALES\") AS \"QSALES\"",
				"FROM \"dbo\".\"SALES\" \"S\"",
				"GROUP BY \"S\".\"CCOMPANY\""));

		System.out.println("== Errors");
		error("Grouped twice", IllegalStateException.class, () -> {
			QSelect s = fromSales(SQLSERVER);
			s.groupBy();
			s.groupBy();
		});
		error("EXISTS select with aggregates and other expressions, not grouped", IllegalStateException.class, () -> {
			QSelect s = fromSales(SQLSERVER);
			QSelect e = new QSelect(s);
			e.from(articles, "A");
			e.addColumn("A.CBUSINESS", "CBUSINESS");
			e.addColumn("COUNT(*)", "NARTICLES");
			s.whereExists(e);
		});

		select = fromSales(SQLSERVER);
		exists = new QSelect(select);
		exists.from(articles, "A");
		exists.addColumn("S.CCOMPANY", "CCOMPANY");
		exists.addColumn("COUNT(*)", "NARTICLES");
		exists.whereExpr("A.CARTICLE", EQ, "S.CARTICLE");
		String attached;
		try {
			select.whereExists(exists);
			attached = "attached";
		} catch (IllegalStateException exc) {
			attached = exc.getMessage();
		}
		report(attached.equals("attached"), "Aggregates and outer columns only: no GROUP BY needed", attached, "attached");

		if (args.length > 0 && args[0].equals("db")) {
			System.out.println("== Run on SQL Server, over a derived table of literals");
			SQLSERVER.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");
			StringBuilder rows = new StringBuilder();
			rows.append("FROM (SELECT N'LCZ' AS [CCOMPANY], N'A1' AS [CARTICLE], 10.00 AS [QSALES] ");
			rows.append("UNION ALL SELECT N'LCZ', N'A2', 20.00 ");
			rows.append("UNION ALL SELECT N'ABC', N'A1', 5.00) AS [S]");
			select = fromSales(SQLSERVER);
			select.addColumn("S.CCOMPANY", "CCOMPANY");
			select.addColumn("1", "NREF");
			select.addColumn("SUM(S.QSALES)", "QSALES");
			select.groupBy();
			StringBuilder sql = new StringBuilder();
			sql.append(select.toString().replace("FROM [dbo].[SALES] [S]", rows.toString()));
			sql.append("\nORDER BY 1");
			StringBuilder result = new StringBuilder();
			try (DBConnection cn = SQLSERVER.getConnection();
					DBStatement st = cn.createStatement();
					DBResultSet rs = st.executeQuery(sql.toString())) {
				while (rs.next()) {
					if (!result.isEmpty()) {
						result.append(", ");
					}
					result.append(rs.getString(1));
					result.append(" ");
					result.append(rs.getString(3));
				}
			} catch (SQLException exc) {
				result.append(exc.getMessage());
			}
			report(result.toString().equals("ABC 5.00, LCZ 30.00"), "Rows selected", result.toString(), "ABC 5.00, LCZ 30.00");
		}

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
