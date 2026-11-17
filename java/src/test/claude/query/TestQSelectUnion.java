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

import static test.claude.Check.*;
import static test.claude.Fixture.*;

import msfx.lib.db.DBEngine;
import msfx.lib.db.query.Logical;
import msfx.lib.db.query.QSelect;
import msfx.lib.db.query.QWhere;
import msfx.lib.db.query.sa.SyntaxException;
import msfx.lib.md.Table;
import msfx.lib.md.Value;
import test.claude.Check;

/**
 * Tests the FROM clause of {@link QSelect} that wraps the UNION or UNION ALL of
 * other selects, through its toString(), with CMA tables. With "db", the
 * selects are also executed on the local SQL Server, over small master tables,
 * and compared with hand-written queries. Only SELECT statements are executed.
 *
 * @author Miquel Sas
 */
public class TestQSelectUnion {

	/**
	 * The table BRANDS.
	 */
	private static Table brands;
	/**
	 * The table COMPANIES.
	 */
	private static Table companies;
	/**
	 * The table BUSINESSES.
	 */
	private static Table businesses;
	/**
	 * The table DIVISIONS.
	 */
	private static Table divisions;


	/**
	 * Returns a select of a kind literal and the company of a table.
	 *
	 * @param db    The database engine.
	 * @param table The table.
	 * @param alias The alias of the table.
	 * @param kind  The kind literal.
	 * @return The select.
	 */
	private static QSelect companies(DBEngine db, Table table, String alias, String kind) {
		QSelect select = new QSelect(db);
		select.from(table, alias);
		StringBuilder kindExpr = new StringBuilder();
		kindExpr.append("'");
		kindExpr.append(kind);
		kindExpr.append("'");
		select.addColumn(kindExpr.toString(), "KIND");
		StringBuilder companyExpr = new StringBuilder();
		companyExpr.append(alias);
		companyExpr.append(".CCOMPANY");
		select.addColumn(companyExpr.toString(), "CCOMPANY");
		return select;
	}

	/**
	 * Returns a select of the divisions of a table.
	 *
	 * @param db    The database engine.
	 * @param table The table.
	 * @param alias The alias of the table.
	 * @return The select.
	 */
	private static QSelect divisions(DBEngine db, Table table, String alias) {
		QSelect select = new QSelect(db);
		select.from(table, alias);
		StringBuilder expr = new StringBuilder();
		expr.append(alias);
		expr.append(".CDIVISION");
		select.addColumn(expr.toString(), "CDIVISION");
		return select;
	}

	/**
	 * Runs the checks.
	 *
	 * @param db True to also run the selects on the local SQL Server.
	 */
	public static void run(boolean db) {
		Check.start("TestQSelectUnion");
		brands = table("BRANDS");
		companies = table("COMPANIES");
		businesses = table("BUSINESSES");
		divisions = table("DIVISIONS");

		section("UNION ALL and UNION");
		QSelect select = new QSelect(SQLSERVER);
		select.wrapUnionAll("Q",
				companies(SQLSERVER, brands, "B", "B"),
				companies(SQLSERVER, companies, "C", "C"));
		select.addColumn("Q.KIND", "KIND");
		select.addColumn("COUNT(*)", "NROWS");
		select.getWhere().cond("Q.CCOMPANY IS NOT NULL");
		select.groupBy();
		select.orderBy("KIND");
		QSelect countByKind = select;
		sql("UNION ALL, then WHERE, GROUP BY and ORDER BY on the wrap", select, lines(
				"SELECT [Q].[KIND] AS [KIND], COUNT(*) AS [NROWS]",
				"FROM (",
				"\tSELECT N'B' AS [KIND], [B].[CCOMPANY] AS [CCOMPANY]",
				"\tFROM [EXPLMARG_QLT].[BRANDS] [B]",
				"\tUNION ALL",
				"\tSELECT N'C' AS [KIND], [C].[CCOMPANY] AS [CCOMPANY]",
				"\tFROM [EXPLMARG_QLT].[COMPANIES] [C]",
				") [Q]",
				"WHERE [Q].[CCOMPANY] <> ''",
				"GROUP BY [Q].[KIND]",
				"ORDER BY [KIND]"));

		select = new QSelect(SQLSERVER);
		select.wrapUnion("Q", divisions(SQLSERVER, businesses, "U"), divisions(SQLSERVER, divisions, "D"));
		select.addColumn("CDIVISION", "CDIVISION");
		select.orderBy("CDIVISION");
		QSelect allDivisions = select;
		sql("UNION, bare column name with a single source", select, lines(
				"SELECT [Q].[CDIVISION] AS [CDIVISION]",
				"FROM (",
				"\tSELECT [U].[CDIVISION] AS [CDIVISION]",
				"\tFROM [EXPLMARG_QLT].[BUSINESSES] [U]",
				"\tUNION",
				"\tSELECT [D].[CDIVISION] AS [CDIVISION]",
				"\tFROM [EXPLMARG_QLT].[DIVISIONS] [D]",
				") [Q]",
				"ORDER BY [CDIVISION]"));

		section("Joins after the wrap");
		select = new QSelect(SQLSERVER);
		select.wrapUnion("Q", divisions(SQLSERVER, businesses, "U"), divisions(SQLSERVER, divisions, "D"));
		select.leftJoin(divisions, "D", "Q.CDIVISION = D.CDIVISION");
		select.addColumn("Q.CDIVISION", "CDIVISION");
		select.addColumn("D.DDIVISION", "DDIVISION");
		select.getWhere().cond("D.CDIVISION IS NULL");
		select.orderBy("CDIVISION");
		QSelect orphanDivisions = select;
		sql("LEFT JOIN with the alias of an inner table, reused outside", select, lines(
				"SELECT [Q].[CDIVISION] AS [CDIVISION], [D].[DDIVISION] AS [DDIVISION]",
				"FROM (",
				"\tSELECT [U].[CDIVISION] AS [CDIVISION]",
				"\tFROM [EXPLMARG_QLT].[BUSINESSES] [U]",
				"\tUNION",
				"\tSELECT [D].[CDIVISION] AS [CDIVISION]",
				"\tFROM [EXPLMARG_QLT].[DIVISIONS] [D]",
				") [Q]",
				"LEFT JOIN [EXPLMARG_QLT].[DIVISIONS] [D] ON [Q].[CDIVISION] = [D].[CDIVISION]",
				"WHERE ([D].[CDIVISION] IS NULL OR [D].[CDIVISION] = '')",
				"ORDER BY [CDIVISION]"));

		section("Types of the wrapped columns");
		select = new QSelect(SQLSERVER);
		select.wrapUnionAll("Q",
				companies(SQLSERVER, brands, "B", "B"),
				companies(SQLSERVER, companies, "C", "C"));
		select.addColumn("Q.CCOMPANY", "CCOMPANY");
		QWhere where = select.getWhere();
		where.cond("Q.CCOMPANY IS NULL");
		where.cond(Logical.OR, "Q.KIND IS NULL");
		String result = select.toString();
		String expected = "WHERE ([Q].[CCOMPANY] IS NULL OR [Q].[CCOMPANY] = '') OR [Q].[KIND] IS NULL";
		report(result.endsWith(expected), "Plain column: STRING; literal: not known, plain IS NULL",
				result.substring(result.lastIndexOf("WHERE")), expected);

		section("Nested wrap");
		QSelect inner = new QSelect(SQLSERVER);
		inner.wrapUnion("I", divisions(SQLSERVER, businesses, "U"), divisions(SQLSERVER, divisions, "D"));
		inner.addColumn("I.CDIVISION", "CDIVISION");
		select = new QSelect(SQLSERVER);
		select.wrapUnionAll("Q", inner, divisions(SQLSERVER, divisions, "D"));
		select.addColumn("Q.CDIVISION", "CDIVISION");
		select.addColumn("COUNT(*)", "NROWS");
		select.groupBy();
		select.having("COUNT(*) > ?", new Value(1));
		select.orderBy("CDIVISION");
		QSelect nested = select;
		sql("A wrapping select wrapped again, indented twice", select, lines(
				"SELECT [Q].[CDIVISION] AS [CDIVISION], COUNT(*) AS [NROWS]",
				"FROM (",
				"\tSELECT [I].[CDIVISION] AS [CDIVISION]",
				"\tFROM (",
				"\t\tSELECT [U].[CDIVISION] AS [CDIVISION]",
				"\t\tFROM [EXPLMARG_QLT].[BUSINESSES] [U]",
				"\t\tUNION",
				"\t\tSELECT [D].[CDIVISION] AS [CDIVISION]",
				"\t\tFROM [EXPLMARG_QLT].[DIVISIONS] [D]",
				"\t) [I]",
				"\tUNION ALL",
				"\tSELECT [D].[CDIVISION] AS [CDIVISION]",
				"\tFROM [EXPLMARG_QLT].[DIVISIONS] [D]",
				") [Q]",
				"GROUP BY [Q].[CDIVISION]",
				"HAVING COUNT(*) > 1",
				"ORDER BY [CDIVISION]"));

		select = new QSelect(ORACLE);
		select.wrapUnionAll("Q",
				companies(ORACLE, brands, "B", "B"),
				companies(ORACLE, companies, "C", "C"));
		select.addColumn("Q.KIND", "KIND");
		sql("Oracle", select, lines(
				"SELECT \"Q\".\"KIND\" AS \"KIND\"",
				"FROM (",
				"\tSELECT N'B' AS \"KIND\", \"B\".\"CCOMPANY\" AS \"CCOMPANY\"",
				"\tFROM \"EXPLMARG_QLT\".\"BRANDS\" \"B\"",
				"\tUNION ALL",
				"\tSELECT N'C' AS \"KIND\", \"C\".\"CCOMPANY\" AS \"CCOMPANY\"",
				"\tFROM \"EXPLMARG_QLT\".\"COMPANIES\" \"C\"",
				") \"Q\""));

		section("Errors");
		error("FROM already set", IllegalStateException.class, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(brands, "B");
			s.wrapUnion("Q", divisions(SQLSERVER, divisions, "D"));
		});
		error("Wrapped twice", IllegalStateException.class, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.wrapUnion("Q", divisions(SQLSERVER, divisions, "D"));
			s.wrapUnion("R", divisions(SQLSERVER, divisions, "D"));
		});
		error("No selects", IllegalArgumentException.class, () -> {
			new QSelect(SQLSERVER).wrapUnion("Q");
		});
		error("Null select", NullPointerException.class, () -> {
			new QSelect(SQLSERVER).wrapUnion("Q", divisions(SQLSERVER, divisions, "D"), null);
		});
		error("Blank alias", IllegalArgumentException.class, () -> {
			new QSelect(SQLSERVER).wrapUnion(" ", divisions(SQLSERVER, divisions, "D"));
		});
		error("Another engine", IllegalArgumentException.class, () -> {
			new QSelect(SQLSERVER).wrapUnion("Q", divisions(ORACLE, divisions, "D"));
		});
		error("Wrapped select with ORDER BY", IllegalArgumentException.class, () -> {
			QSelect w = divisions(SQLSERVER, divisions, "D");
			w.orderBy("CDIVISION");
			new QSelect(SQLSERVER).wrapUnion("Q", w);
		});
		error("Wrapped select without FROM", IllegalStateException.class, () -> {
			new QSelect(SQLSERVER).wrapUnion("Q", new QSelect(SQLSERVER));
		});
		error("Wrapped select without columns", IllegalStateException.class, () -> {
			QSelect w = new QSelect(SQLSERVER);
			w.from(divisions, "D");
			new QSelect(SQLSERVER).wrapUnion("Q", w);
		});
		error("Wrapped EXISTS select", IllegalStateException.class, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(brands, "B");
			QSelect e = s.getWhere().createExists();
			e.from(divisions, "D");
			e.addColumn("D.CDIVISION", "CDIVISION");
			new QSelect(SQLSERVER).wrapUnion("Q", e);
		});
		error("Wrapped select with aggregates, not grouped", IllegalStateException.class, () -> {
			QSelect w = divisions(SQLSERVER, divisions, "D");
			w.addColumn("COUNT(*)", "NROWS");
			new QSelect(SQLSERVER).wrapUnion("Q", w);
		});
		error("Different number of columns", IllegalArgumentException.class, () -> {
			QSelect w = divisions(SQLSERVER, divisions, "D");
			w.addColumn("D.DDIVISION", "DDIVISION");
			new QSelect(SQLSERVER).wrapUnion("Q", divisions(SQLSERVER, businesses, "U"), w);
		});
		error("Different aliases", IllegalArgumentException.class, () -> {
			QSelect w = new QSelect(SQLSERVER);
			w.from(divisions, "D");
			w.addColumn("D.CDIVISION", "CODE");
			new QSelect(SQLSERVER).wrapUnion("Q", divisions(SQLSERVER, businesses, "U"), w);
		});
		error("Same aliases in another order", IllegalArgumentException.class, () -> {
			QSelect w = new QSelect(SQLSERVER);
			w.from(companies, "C");
			w.addColumn("C.CCOMPANY", "CCOMPANY");
			w.addColumn("'C'", "KIND");
			new QSelect(SQLSERVER).wrapUnion("Q", companies(SQLSERVER, brands, "B", "B"), w);
		});
		error("Wrapping alias used by an outer select", IllegalArgumentException.class, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(brands, "Q");
			QSelect e = s.getWhere().createExists();
			e.wrapUnion("Q", divisions(SQLSERVER, divisions, "D"));
		});
		error("Unknown column of the wrap", SyntaxException.class, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.wrapUnion("Q", divisions(SQLSERVER, divisions, "D"));
			s.addColumn("Q.DDIVISION", "DDIVISION");
		});

		String exprs;
		try {
			QSelect w = new QSelect(SQLSERVER);
			w.from(divisions, "D");
			w.addColumn("d.cdivision", "cdivision");
			new QSelect(SQLSERVER).wrapUnion("Q", divisions(SQLSERVER, businesses, "U"), w);
			exprs = "accepted";
		} catch (RuntimeException exc) {
			exprs = exc.getMessage();
		}
		report(exprs.equals("accepted"), "Aliases compared ignoring case", exprs, "accepted");

		if (db) {
			section("Run on SQL Server");
			connect();
			sameRows("UNION ALL counted by kind", countByKind, lines(
					"SELECT 'B', COUNT(*) FROM EXPLMARG_QLT.BRANDS WHERE CCOMPANY <> ''",
					"UNION ALL",
					"SELECT 'C', COUNT(*) FROM EXPLMARG_QLT.COMPANIES WHERE CCOMPANY <> ''",
					"ORDER BY 1"));
			sameRows("UNION of divisions", allDivisions, lines(
					"SELECT DISTINCT CDIVISION FROM (",
					"SELECT CDIVISION FROM EXPLMARG_QLT.BUSINESSES",
					"UNION ALL",
					"SELECT CDIVISION FROM EXPLMARG_QLT.DIVISIONS) X",
					"ORDER BY 1"));
			String orphans = rows(orphanDivisions.toString());
			String expectedOrphans = rows(lines(
					"SELECT DISTINCT U.CDIVISION, NULL FROM EXPLMARG_QLT.BUSINESSES U",
					"WHERE NOT EXISTS (SELECT 1 FROM EXPLMARG_QLT.DIVISIONS D WHERE D.CDIVISION = U.CDIVISION)",
					"ORDER BY 1"));
			report(orphans.equals(expectedOrphans), "Divisions of businesses not in DIVISIONS",
					orphans.isEmpty() ? "none" : orphans, expectedOrphans.isEmpty() ? "none" : expectedOrphans);
			sameRows("Nested wrap with HAVING", nested, lines(
					"SELECT D.CDIVISION, 2 FROM EXPLMARG_QLT.DIVISIONS D",
					"ORDER BY 1"));
		}
	}

	/**
	 * Entry point.
	 *
	 * @param args Pass "db" to also run the selects on the local SQL Server.
	 */
	public static void main(String[] args) {
		boolean db = args.length > 0 && args[0].equals("db");
		run(db);
		summary();
		System.exit(0);
	}
}
