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

import java.sql.SQLException;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.db.query.QSelect;
import msfx.lib.md.Column;
import msfx.lib.md.Table;
import msfx.lib.md.Types;

/**
 * Tests the EXISTS conditions of {@link QSelect}, through its toString(): the
 * access of the inner select to the outer tables, nesting, NOT EXISTS, the
 * uniqueness of aliases in the tree of selects, and the errors when attaching.
 * Nothing is executed, except an optional run on the local SQL Server over
 * derived tables of literals, that reads no table.
 *
 * @author Miquel Sas
 */
public class TestQSelectExists {

	/**
	 * SQL Server engine.
	 */
	private static final DBEngineSQLServer SQLSERVER = new DBEngineSQLServer();
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
	 * The table BUSINESSES.
	 */
	private static Table businesses;

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
	 * Returns a select of the articles sold, from SALES S.
	 *
	 * @return The select.
	 */
	private static QSelect fromSales() {
		QSelect select = new QSelect(SQLSERVER);
		select.from(sales, "S");
		select.addColumn("S.CARTICLE", "CARTICLE");
		return select;
	}

	/**
	 * Returns an EXISTS select of the articles of a business, linked to the
	 * sales of the outer select.
	 *
	 * @param outer    The outer select.
	 * @param business The business.
	 * @return The EXISTS select.
	 */
	private static QSelect articlesOf(QSelect outer, String business) {
		QSelect exists = new QSelect(outer);
		exists.from(articles, "A");
		exists.addColumn("1", "NREF");
		exists.whereExpr("A.CARTICLE", EQ, "S.CARTICLE");
		exists.whereValue(AND, "A.CBUSINESS", EQ, business);
		return exists;
	}

	/**
	 * Entry point.
	 *
	 * @param args Pass "db" to also run a select with an EXISTS condition on the
	 *             local SQL Server.
	 */
	public static void main(String[] args) {

		sales = new Table("SALES");
		sales.setSchema("dbo");
		sales.columns().add(new Column("CCOMPANY", Types.STRING, 10, -1));
		sales.columns().add(new Column("CARTICLE", Types.STRING, 40, -1));

		articles = new Table("ARTICLES");
		articles.setSchema("dbo");
		articles.columns().add(new Column("CARTICLE", Types.STRING, 40, -1));
		articles.columns().add(new Column("CBUSINESS", Types.STRING, 10, -1));

		businesses = new Table("BUSINESSES");
		businesses.setSchema("dbo");
		businesses.columns().add(new Column("CBUSINESS", Types.STRING, 10, -1));
		businesses.columns().add(new Column("CCOMPANY", Types.STRING, 10, -1));

		System.out.println("== EXISTS");
		QSelect select = fromSales();
		select.whereExists(articlesOf(select, "FURN"));
		sql("Inner select using the outer table", select, lines(
				"SELECT [S].[CARTICLE] AS [CARTICLE]",
				"FROM [dbo].[SALES] [S]",
				"WHERE EXISTS (",
				"\tSELECT 1 AS [NREF]",
				"\tFROM [dbo].[ARTICLES] [A]",
				"\tWHERE [A].[CARTICLE] = [S].[CARTICLE] AND [A].[CBUSINESS] = N'FURN'",
				")"));

		select = fromSales();
		select.whereValue("S.CCOMPANY", EQ, "LCZ");
		select.whereExists(AND_NOT, articlesOf(select, "FURN"));
		sql("NOT EXISTS after a condition", select, lines(
				"SELECT [S].[CARTICLE] AS [CARTICLE]",
				"FROM [dbo].[SALES] [S]",
				"WHERE [S].[CCOMPANY] = N'LCZ' AND NOT EXISTS (",
				"\tSELECT 1 AS [NREF]",
				"\tFROM [dbo].[ARTICLES] [A]",
				"\tWHERE [A].[CARTICLE] = [S].[CARTICLE] AND [A].[CBUSINESS] = N'FURN'",
				")"));

		select = fromSales();
		select.whereExists(NOT, articlesOf(select, "FURN"));
		sql("NOT EXISTS as the first condition", select, lines(
				"SELECT [S].[CARTICLE] AS [CARTICLE]",
				"FROM [dbo].[SALES] [S]",
				"WHERE NOT EXISTS (",
				"\tSELECT 1 AS [NREF]",
				"\tFROM [dbo].[ARTICLES] [A]",
				"\tWHERE [A].[CARTICLE] = [S].[CARTICLE] AND [A].[CBUSINESS] = N'FURN'",
				")"));

		select = fromSales();
		select.whereValue("S.CCOMPANY", EQ, "LCZ");
		select.whereGroup(OR);
		select.whereExists(articlesOf(select, "FURN"));
		select.whereValue(AND, "S.CARTICLE", NE, "X");
		sql("EXISTS in a group of two conditions", select, lines(
				"SELECT [S].[CARTICLE] AS [CARTICLE]",
				"FROM [dbo].[SALES] [S]",
				"WHERE [S].[CCOMPANY] = N'LCZ'",
				"OR (EXISTS (",
				"\tSELECT 1 AS [NREF]",
				"\tFROM [dbo].[ARTICLES] [A]",
				"\tWHERE [A].[CARTICLE] = [S].[CARTICLE] AND [A].[CBUSINESS] = N'FURN'",
				") AND [S].[CARTICLE] <> N'X')"));

		select = fromSales();
		QSelect exists = new QSelect(select);
		exists.from(articles, "A");
		exists.addColumn("1", "NREF");
		exists.whereExpr("A.CARTICLE", EQ, "S.CARTICLE");
		QSelect nested = new QSelect(exists);
		nested.from(businesses, "B");
		nested.addColumn("1", "NREF");
		nested.whereExpr("B.CBUSINESS", EQ, "A.CBUSINESS");
		nested.whereExpr(AND, "B.CCOMPANY", EQ, "S.CCOMPANY");
		exists.whereExists(AND, nested);
		select.whereExists(exists);
		sql("Nested EXISTS, seeing both outer levels", select, lines(
				"SELECT [S].[CARTICLE] AS [CARTICLE]",
				"FROM [dbo].[SALES] [S]",
				"WHERE EXISTS (",
				"\tSELECT 1 AS [NREF]",
				"\tFROM [dbo].[ARTICLES] [A]",
				"\tWHERE [A].[CARTICLE] = [S].[CARTICLE] AND EXISTS (",
				"\t\tSELECT 1 AS [NREF]",
				"\t\tFROM [dbo].[BUSINESSES] [B]",
				"\t\tWHERE [B].[CBUSINESS] = [A].[CBUSINESS] AND [B].[CCOMPANY] = [S].[CCOMPANY]",
				"\t)",
				")"));

		select = fromSales();
		select.whereExists(articlesOf(select, "FURN"));
		select.whereExists(OR, articlesOf(select, "TEXT"));
		sql("Sibling EXISTS selects can reuse an alias", select, lines(
				"SELECT [S].[CARTICLE] AS [CARTICLE]",
				"FROM [dbo].[SALES] [S]",
				"WHERE EXISTS (",
				"\tSELECT 1 AS [NREF]",
				"\tFROM [dbo].[ARTICLES] [A]",
				"\tWHERE [A].[CARTICLE] = [S].[CARTICLE] AND [A].[CBUSINESS] = N'FURN'",
				") OR EXISTS (",
				"\tSELECT 1 AS [NREF]",
				"\tFROM [dbo].[ARTICLES] [A]",
				"\tWHERE [A].[CARTICLE] = [S].[CARTICLE] AND [A].[CBUSINESS] = N'TEXT'",
				")"));

		System.out.println("== Errors");
		error("EXISTS select created for another select", IllegalArgumentException.class, () -> {
			QSelect other = fromSales();
			fromSales().whereExists(articlesOf(other, "FURN"));
		});
		error("EXISTS select attached twice", IllegalStateException.class, () -> {
			QSelect s = fromSales();
			QSelect e = articlesOf(s, "FURN");
			s.whereExists(e);
			s.whereExists(AND, e);
		});
		error("EXISTS select without FROM", IllegalStateException.class, () -> {
			QSelect s = fromSales();
			s.whereExists(new QSelect(s));
		});
		error("EXISTS select without columns", IllegalStateException.class, () -> {
			QSelect s = fromSales();
			QSelect e = new QSelect(s);
			e.from(articles, "A");
			s.whereExists(e);
		});
		error("Inner alias equal to an outer one", IllegalArgumentException.class, () -> {
			new QSelect(fromSales()).from(articles, "s");
		});
		error("Outer join added later with the alias of an attached EXISTS", IllegalArgumentException.class, () -> {
			QSelect s = fromSales();
			s.whereExists(articlesOf(s, "FURN"));
			s.leftJoin(articles, "A", "S.CARTICLE = A.CARTICLE");
		});
		error("Outer join added before attaching, with an inner alias", IllegalArgumentException.class, () -> {
			QSelect s = fromSales();
			QSelect e = articlesOf(s, "FURN");
			s.leftJoin(articles, "A", "S.CARTICLE = A.CARTICLE");
			s.whereExists(e);
		});
		error("Outer condition on an inner table", IllegalArgumentException.class, () -> {
			QSelect s = fromSales();
			s.whereExists(articlesOf(s, "FURN"));
			s.whereValue(AND, "A.CBUSINESS", EQ, "FURN");
		});
		error("Bare column in an EXISTS select", IllegalArgumentException.class, () -> {
			QSelect e = new QSelect(fromSales());
			e.from(articles, "A");
			e.whereValue("CBUSINESS", EQ, "FURN");
		});
		error("Logical operator in the first condition", IllegalStateException.class, () -> {
			QSelect s = fromSales();
			s.whereExists(AND, articlesOf(s, "FURN"));
		});

		select = fromSales();
		select.whereValue("S.CCOMPANY", EQ, "LCZ");
		QSelect empty = new QSelect(select);
		try {
			select.whereExists(AND, empty);
		} catch (IllegalStateException exc) {
			// expected
		}
		empty.from(articles, "A");
		empty.addColumn("1", "NREF");
		select.whereExists(AND, empty);
		report(select.toString().contains("[A]"), "A rejected EXISTS select can be attached once completed",
				select.toString(), "the EXISTS written");
		select = fromSales();
		select.whereValue("S.CCOMPANY", EQ, "LCZ");
		try {
			select.whereExists(articlesOf(select, "FURN"));
		} catch (IllegalStateException exc) {
			// expected, the logical operator is missing
		}
		String joined;
		try {
			select.leftJoin(articles, "A", "S.CARTICLE = A.CARTICLE");
			joined = "join added";
		} catch (IllegalArgumentException exc) {
			joined = exc.getMessage();
		}
		report(joined.equals("join added"), "A rejected EXISTS select reserves no alias", joined, "join added");

		if (args.length > 0 && args[0].equals("db")) {
			System.out.println("== Run on SQL Server, over derived tables of literals");
			SQLSERVER.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");
			StringBuilder salesRows = new StringBuilder();
			salesRows.append("FROM (SELECT N'LCZ' AS [CCOMPANY], N'A1' AS [CARTICLE] ");
			salesRows.append("UNION ALL SELECT N'LCZ', N'A2' ");
			salesRows.append("UNION ALL SELECT N'LCZ', N'A3') AS [S]");
			StringBuilder articleRows = new StringBuilder();
			articleRows.append("FROM (SELECT N'A1' AS [CARTICLE], N'FURN' AS [CBUSINESS] ");
			articleRows.append("UNION ALL SELECT N'A2', N'TEXT') AS [A]");
			select = fromSales();
			select.whereExists(articlesOf(select, "FURN"));
			select.whereExists(OR_NOT, articlesOf(select, "TEXT"));
			String sql = select.toString();
			sql = sql.replace("FROM [dbo].[SALES] [S]", salesRows.toString());
			sql = sql.replace("FROM [dbo].[ARTICLES] [A]", articleRows.toString());
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
			report(result.toString().equals("A1, A3"), "Rows selected", result.toString(), "A1, A3");
		}

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
