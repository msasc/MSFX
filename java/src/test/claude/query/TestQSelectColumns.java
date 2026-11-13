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

import java.sql.SQLException;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBEngine;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.adapters.DBEngineOracle;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.db.query.QSelect;
import msfx.lib.md.Column;
import msfx.lib.md.ColumnList;
import msfx.lib.md.Domain;
import msfx.lib.md.Table;
import msfx.lib.md.Types;

/**
 * Tests the select list of {@link QSelect}, through its toString(): the columns
 * written, the errors when adding them, the limit of columns of each database,
 * and the lookup ignoring case of {@link ColumnList} with many columns. Nothing
 * is executed, except an optional run of a wide select of literals on the local
 * SQL Server, that reads no table.
 *
 * @author Miquel Sas
 */
public class TestQSelectColumns {

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
	 * Returns a select from SALES S with as many columns as the database
	 * accepts, all of them the same column with a different alias.
	 *
	 * @param db The database engine.
	 * @return The select.
	 */
	private static QSelect full(DBEngine db) {
		QSelect select = fromSales(db);
		for (int i = 1; i <= db.getMaxSelectColumns(); i++) {
			StringBuilder alias = new StringBuilder();
			alias.append("C");
			alias.append(i);
			select.addColumn("S.QSALES", alias.toString());
		}
		return select;
	}

	/**
	 * Entry point.
	 *
	 * @param args Pass "db" to also run the widest select on the local SQL
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
		articles.columns().add(new Column("DARTICLE", Types.STRING, 200, -1));

		System.out.println("== Select list");
		QSelect select = fromSales(SQLSERVER);
		select.leftJoin(articles, "A", "S.CARTICLE = A.CARTICLE");
		select.addColumn("S.CCOMPANY", "CCOMPANY");
		select.addColumn("a.darticle", "Description");
		select.addColumn("S.CCOMPANY || '-' || S.CARTICLE", "CODE", new Domain("CODE", Types.STRING, 51, -1));
		sql("Columns, SQL Server", select, lines(
				"SELECT [S].[CCOMPANY] AS [CCOMPANY], [A].[DARTICLE] AS [Description], CONCAT([S].[CCOMPANY], N'-', [S].[CARTICLE]) AS [CODE]",
				"FROM [dbo].[SALES] [S]",
				"LEFT JOIN [dbo].[ARTICLES] [A] ON [S].[CARTICLE] = [A].[CARTICLE]"));

		select = fromSales(ORACLE);
		select.addColumn("SUM(QSALES)", "QSALES");
		sql("Aggregate and bare column with one table, Oracle", select, lines(
				"SELECT SUM(\"S\".\"QSALES\") AS \"QSALES\"",
				"FROM \"dbo\".\"SALES\" \"S\""));

		select = fromSales(SQLSERVER);
		sql("No columns yet: FROM only", select, "FROM [dbo].[SALES] [S]");

		System.out.println("== Errors");
		error("Column before FROM", IllegalStateException.class, () -> {
			new QSelect(SQLSERVER).addColumn("S.CCOMPANY", "CCOMPANY");
		});
		error("Column of a table not joined yet", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).addColumn("A.DARTICLE", "DARTICLE");
		});
		error("Alias already used, ignoring case", IllegalArgumentException.class, () -> {
			QSelect s = fromSales(SQLSERVER);
			s.addColumn("S.CCOMPANY", "COMPANY");
			s.addColumn("S.CARTICLE", "company");
		});
		error("Null alias", NullPointerException.class, () -> {
			fromSales(SQLSERVER).addColumn("S.CCOMPANY", null);
		});
		error("Blank alias", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).addColumn("S.CCOMPANY", " ");
		});
		error("Condition instead of a value", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).addColumn("S.QSALES > 0", "POSITIVE");
		});
		error("Expression around an aggregate", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).addColumn("SUM(S.QSALES) / 100", "QSALES");
		});

		System.out.println("== Limit of columns");
		for (DBEngine db : new DBEngine[] { SQLSERVER, ORACLE }) {
			QSelect wide = full(db);
			StringBuilder title = new StringBuilder();
			title.append(db.getMaxSelectColumns());
			title.append(" columns accepted, one more rejected, ");
			title.append(db.getAdapter());
			error(title.toString(), IllegalStateException.class, () -> {
				wide.addColumn("S.QSALES", "ONE_MORE");
			});
		}

		System.out.println("== ColumnList with many columns");
		Table wideTable = new Table("WIDE");
		long start = System.nanoTime();
		for (int i = 1; i <= 2000; i++) {
			StringBuilder name = new StringBuilder();
			name.append("COLUMN_");
			name.append(i);
			wideTable.columns().add(new Column(name.toString(), Types.STRING, 10, -1));
		}
		boolean found = true;
		for (int i = 1; i <= 2000; i++) {
			StringBuilder name = new StringBuilder();
			name.append("column_");
			name.append(i);
			found &= wideTable.columns().getIgnoreCase(name.toString()) != null;
		}
		long millis = (System.nanoTime() - start) / 1_000_000;
		StringBuilder result = new StringBuilder();
		result.append("2000 columns added and found ignoring case in ");
		result.append(millis);
		result.append(" ms");
		report(found, "Lookup ignoring case", result.toString(), "all found");
		error("Alias that differs only in case", IllegalArgumentException.class, () -> {
			wideTable.columns().add(new Column("Column_7", Types.STRING, 10, -1));
		});

		if (args.length > 0 && args[0].equals("db")) {
			System.out.println("== Widest select run on SQL Server, over a row of literals");
			SQLSERVER.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");
			StringBuilder sql = new StringBuilder();
			sql.append(full(SQLSERVER).toString().replace("FROM [dbo].[SALES] [S]", "FROM (SELECT 1.50 AS [QSALES]) AS [S]"));
			String columns;
			try (DBConnection cn = SQLSERVER.getConnection();
					DBStatement st = cn.createStatement();
					DBResultSet rs = st.executeQuery(sql.toString())) {
				columns = String.valueOf(rs.getMetaData().getColumnCount());
			} catch (SQLException exc) {
				columns = exc.getMessage();
			}
			report(columns.equals("4096"), "4096 columns read", columns, "4096");
		}

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
