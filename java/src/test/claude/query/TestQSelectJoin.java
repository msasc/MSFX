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

import msfx.lib.db.DBEngine;
import msfx.lib.db.adapters.DBEngineOracle;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.db.query.QCreateTable;
import msfx.lib.db.query.QSelect;
import msfx.lib.md.Column;
import msfx.lib.md.Index;
import msfx.lib.md.Table;
import msfx.lib.md.Types;

/**
 * Tests the FROM clause and the joins of {@link QSelect}, through its
 * toString(), and the toString() of a DDL statement. Nothing is executed.
 *
 * @author Miquel Sas
 */
public class TestQSelectJoin {

	/**
	 * SQL Server engine.
	 */
	private static final DBEngine SQLSERVER = new DBEngineSQLServer();
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
	 * Returns a select from SALES S, with the ARTICLES A left joined.
	 *
	 * @param db The database engine.
	 * @return The select.
	 */
	private static QSelect salesWithArticles(DBEngine db) {
		QSelect select = new QSelect(db);
		select.from(sales, "S");
		select.leftJoin(articles, "A", "S.CARTICLE = A.CARTICLE");
		return select;
	}

	/**
	 * Entry point.
	 *
	 * @param args Not used.
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
		articles.columns().add(new Column("CBUSINESS", Types.STRING, 12, -1));

		businesses = new Table("BUSINESSES");
		businesses.setSchema("dbo");
		businesses.columns().add(new Column("CBUSINESS", Types.STRING, 12, -1));
		businesses.columns().add(new Column("DBUSINESS", Types.STRING, 100, -1));

		System.out.println("== Joins");
		QSelect select = new QSelect(SQLSERVER);
		sql("Empty select", select, "");
		select.from(sales, "S");
		sql("FROM only", select, "FROM [dbo].[SALES] [S]");
		select.leftJoin(articles, "A", "s.carticle = a.CARTICLE");
		select.innerJoin(businesses, "B", "A.CBUSINESS = B.CBUSINESS AND B.DBUSINESS <> ''");
		sql("LEFT and INNER JOIN, SQL Server", select,
				lines("FROM [dbo].[SALES] [S]",
						"LEFT JOIN [dbo].[ARTICLES] [A] ON [S].[CARTICLE] = [A].[CARTICLE]",
						"INNER JOIN [dbo].[BUSINESSES] [B] ON [A].[CBUSINESS] = [B].[CBUSINESS] AND [B].[DBUSINESS] <> N''"));

		select = salesWithArticles(ORACLE);
		sql("LEFT JOIN, Oracle", select,
				lines("FROM \"dbo\".\"SALES\" \"S\"",
						"LEFT JOIN \"dbo\".\"ARTICLES\" \"A\" ON \"S\".\"CARTICLE\" = \"A\".\"CARTICLE\""));

		select = new QSelect(SQLSERVER);
		select.from(articles, "A1");
		select.innerJoin(articles, "A2", "A1.CARTICLE = A2.CARTICLE");
		sql("Same table twice", select,
				lines("FROM [dbo].[ARTICLES] [A1]",
						"INNER JOIN [dbo].[ARTICLES] [A2] ON [A1].[CARTICLE] = [A2].[CARTICLE]"));

		System.out.println("== Errors");
		error("Join before FROM", IllegalStateException.class, () -> {
			new QSelect(SQLSERVER).leftJoin(articles, "A", "1 = 1");
		});
		error("Null alias in FROM", NullPointerException.class, () -> {
			new QSelect(SQLSERVER).from(sales, null);
		});
		error("Null alias in a join", NullPointerException.class, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(sales, "S");
			s.leftJoin(articles, null, "S.CARTICLE = CARTICLE");
		});
		error("Alias already used, ignoring case", IllegalArgumentException.class, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(sales, "S");
			s.leftJoin(articles, "s", "1 = 1");
		});
		error("Table joined later", IllegalArgumentException.class, () -> {
			QSelect s = salesWithArticles(SQLSERVER);
			s.innerJoin(businesses, "B", "A.CBUSINESS = X.CBUSINESS");
		});
		error("Column not in the table", IllegalArgumentException.class, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(sales, "S");
			s.leftJoin(articles, "A", "S.CARTICLE = A.CNOTHING");
		});
		error("Placeholder in ON", IllegalArgumentException.class, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(sales, "S");
			s.leftJoin(articles, "A", "S.CARTICLE = ?");
		});
		error("Aggregate in ON", IllegalArgumentException.class, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(sales, "S");
			s.leftJoin(articles, "A", "COUNT(*) > 1");
		});

		/* A rejected join leaves the statement as it was. */
		select = new QSelect(SQLSERVER);
		select.from(sales, "S");
		try {
			select.leftJoin(articles, "A", "S.CARTICLE = A.WRONG");
		} catch (IllegalArgumentException exc) {
			/* Expected. */
		}
		select.leftJoin(articles, "A", "S.CARTICLE = A.CARTICLE");
		sql("Alias free again after a rejected join", select,
				lines("FROM [dbo].[SALES] [S]",
						"LEFT JOIN [dbo].[ARTICLES] [A] ON [S].[CARTICLE] = [A].[CARTICLE]"));

		System.out.println("== toString of a DDL statement");
		Index pk = new Index(articles);
		pk.addColumns("CARTICLE");
		articles.setPrimaryKey(pk);
		System.out.print(new QCreateTable(SQLSERVER, articles).toString().indent(9));

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
