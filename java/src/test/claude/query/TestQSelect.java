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
import static test.claude.Check.*;
import static test.claude.Fixture.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBEngine;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.query.Logical;
import msfx.lib.db.query.QSelect;
import msfx.lib.db.query.QWhere;
import msfx.lib.md.Table;
import msfx.lib.md.Value;
import test.claude.Check;

/**
 * Tests building a {@link QSelect} and the SQL it writes, on the CMA tables:
 * FROM and joins, the select list, the WHERE clause with its groups, EXISTS
 * selects, GROUP BY, HAVING and ORDER BY, one statement for Oracle, and the
 * errors of each part. With "db", runs selects on the local SQL Server and
 * compares their rows with hand-written queries. Only SELECT statements are
 * executed, and never on WC_SALES, whose millions of rows are only written.
 *
 * @author Miquel Sas
 */
public class TestQSelect {

	/**
	 * FROM clause of WC_SALES S, on SQL Server.
	 */
	private static final String FROM_SALES = "FROM [EXPLMARG_QLT].[WC_SALES] [S]";
	/**
	 * Join of ARTICLES A to WC_SALES S, on SQL Server.
	 */
	private static final String JOIN_ARTICLES = "LEFT JOIN [EXPLMARG_QLT].[ARTICLES] [A] ON [S].[CARTICLE] = [A].[CARTICLE]";
	/**
	 * The table WC_SALES.
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
	 * The table TPIECES.
	 */
	private static Table pieces;

	/**
	 * Returns a select from WC_SALES S.
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
	 * Returns a select from WC_SALES S with ARTICLES A left joined.
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
	 * Returns an EXISTS select of the articles of a business, linked to the
	 * sales of the outer select.
	 *
	 * @param outer    The outer select.
	 * @param business The business.
	 * @return The EXISTS select.
	 */
	private static QSelect articlesOf(QSelect outer, String business) {
		QWhere outerWhere = outer.getWhere();
		QSelect exists = outerWhere.createExists();
		exists.from(articles, "A");
		exists.addColumn("1", "NREF");
		QWhere where = exists.getWhere();
		where.expr("A.CARTICLE", EQ, "S.CARTICLE");
		where.value(AND, "A.CBUSINESS", EQ, business);
		return exists;
	}

	/**
	 * Returns the sales by company, grouped.
	 *
	 * @param db The database engine.
	 * @return The select.
	 */
	private static QSelect byCompany(DBEngine db) {
		QSelect select = fromSales(db);
		select.addColumn("S.CCOMPANY", "CCOMPANY");
		select.addColumn("SUM(S.QSALES)", "QSALES");
		select.addColumn("SUM(S.MCOST)", "MCOST");
		select.getWhere().value("S.NYEAR", EQ, 2026);
		select.groupBy();
		return select;
	}

	/**
	 * Returns a select with as many columns as the database accepts, all of
	 * them the same column under a different alias.
	 *
	 * @param db     The database engine.
	 * @param table  The table, under the alias S.
	 * @param column The column expression.
	 * @return The select.
	 */
	private static QSelect widest(DBEngine db, Table table, String column) {
		QSelect select = new QSelect(db);
		select.from(table, "S");
		for (int i = 1; i <= db.getMaxSelectColumns(); i++) {
			StringBuilder alias = new StringBuilder();
			alias.append("C");
			alias.append(i);
			select.addColumn(column, alias.toString());
		}
		return select;
	}

	/**
	 * Checks the FROM clause and the joins.
	 */
	private static void joins() {
		section("FROM and joins");
		QSelect select = new QSelect(SQLSERVER);
		sql("Empty select", select, "");
		select.from(sales, "S");
		sql("FROM only", select, FROM_SALES);
		select.leftJoin(articles, "A", "s.carticle = a.CARTICLE");
		select.innerJoin(businesses, "B", "A.CBUSINESS = B.CBUSINESS AND B.DBUSINESS <> ''");
		sql("LEFT and INNER JOIN, aliases ignoring case", select, lines(
				FROM_SALES,
				JOIN_ARTICLES,
				"INNER JOIN [EXPLMARG_QLT].[BUSINESSES] [B] ON [A].[CBUSINESS] = [B].[CBUSINESS] AND [B].[DBUSINESS] <> N''"));

		select = new QSelect(SQLSERVER);
		select.from(articles, "A1");
		select.innerJoin(articles, "A2", "A1.CARTICLE = A2.CARTICLE");
		sql("Same table twice", select, lines(
				"FROM [EXPLMARG_QLT].[ARTICLES] [A1]",
				"INNER JOIN [EXPLMARG_QLT].[ARTICLES] [A2] ON [A1].[CARTICLE] = [A2].[CARTICLE]"));

		error("Join before FROM", IllegalStateException.class, () -> {
			new QSelect(SQLSERVER).leftJoin(articles, "A", "1 = 1");
		});
		error("Null alias in FROM", NullPointerException.class, () -> {
			new QSelect(SQLSERVER).from(sales, null);
		});
		error("Null alias in a join", NullPointerException.class, () -> {
			fromSales(SQLSERVER).leftJoin(articles, null, "S.CARTICLE = CARTICLE");
		});
		error("Table alias already used, ignoring case", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).leftJoin(articles, "s", "1 = 1");
		});
		error("Table joined later", IllegalArgumentException.class, () -> {
			joined(SQLSERVER).innerJoin(businesses, "B", "A.CBUSINESS = X.CBUSINESS");
		});
		error("Column not in the table", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).leftJoin(articles, "A", "S.CARTICLE = A.CNOTHING");
		});
		error("Placeholder in ON", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).leftJoin(articles, "A", "S.CARTICLE = ?");
		});
		error("Aggregate in ON", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).leftJoin(articles, "A", "COUNT(*) > 1");
		});

		select = fromSales(SQLSERVER);
		try {
			select.leftJoin(articles, "A", "S.CARTICLE = A.WRONG");
		} catch (IllegalArgumentException exc) {
			/* Expected. */
		}
		select.leftJoin(articles, "A", "S.CARTICLE = A.CARTICLE");
		sql("Alias free again after a rejected join", select, lines(FROM_SALES, JOIN_ARTICLES));
	}

	/**
	 * Checks the select list and its limit of columns.
	 */
	private static void columns() {
		section("Select list");
		QSelect select = joined(SQLSERVER);
		select.addColumn("S.CCOMPANY", "CCOMPANY");
		select.addColumn("a.darticle", "Description");
		select.addColumn("S.CCOMPANY || '-' || S.CARTICLE", "CODE");
		sql("Columns, alias case kept, concatenation translated", select, lines(
				"SELECT [S].[CCOMPANY] AS [CCOMPANY], [A].[DARTICLE] AS [Description], CONCAT([S].[CCOMPANY], N'-', [S].[CARTICLE]) AS [CODE]",
				FROM_SALES,
				JOIN_ARTICLES));

		select = fromSales(SQLSERVER);
		select.addColumn("SUM(QSALES)", "QSALES");
		sql("Aggregate of a bare column with one table", select, lines(
				"SELECT SUM([S].[QSALES]) AS [QSALES]",
				FROM_SALES));

		error("Column before FROM", IllegalStateException.class, () -> {
			new QSelect(SQLSERVER).addColumn("S.CCOMPANY", "CCOMPANY");
		});
		error("Column of a table not joined yet", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).addColumn("A.DARTICLE", "DARTICLE");
		});
		error("Column alias already used, ignoring case", IllegalArgumentException.class, () -> {
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
		for (DBEngine db : new DBEngine[] { SQLSERVER, ORACLE }) {
			QSelect wide = widest(db, sales, "S.QSALES");
			StringBuilder title = new StringBuilder();
			title.append(db.getMaxSelectColumns());
			title.append(" columns accepted, one more rejected, ");
			title.append(db.getAdapter());
			error(title.toString(), IllegalStateException.class, () -> {
				wide.addColumn("S.QSALES", "ONE_MORE");
			});
		}
	}

	/**
	 * Checks the WHERE clause: groups, logical operators, the parentheses
	 * written, comparisons, IN and placeholders.
	 */
	private static void where() {
		section("WHERE");
		QSelect select = fromSales(SQLSERVER);
		QWhere where = select.getWhere();
		where.cond("S.CCOMPANY = 'LCZ'");
		sql("Single raw condition", select, lines(
				FROM_SALES,
				"WHERE [S].[CCOMPANY] = N'LCZ'"));

		select = joined(SQLSERVER);
		where = select.getWhere();
		where.value("S.CCOMPANY", EQ, "LCZ");
		where.value(AND, "S.QSALES", GT, 1000);
		where.expr(AND, "S.QSALES", GE, "S.MCOST * 2");
		where.in(AND_NOT, "A.CPTYPE", new Value("X"), new Value("Y"));
		sql("Value, expression and NOT IN", select, lines(
				FROM_SALES,
				JOIN_ARTICLES,
				"WHERE [S].[CCOMPANY] = N'LCZ' AND [S].[QSALES] > 1000 AND [S].[QSALES] >= [S].[MCOST] * 2 AND NOT [A].[CPTYPE] IN (N'X', N'Y')"));

		select = fromSales(SQLSERVER);
		where = select.getWhere();
		where.cond("S.CCOMPANY = 'LCZ' OR S.CCOMPANY = 'ABC'");
		where.value(AND, "S.QSALES", NE, new BigDecimal("0.50"));
		sql("Condition with OR wrapped when not alone", select, lines(
				FROM_SALES,
				"WHERE ([S].[CCOMPANY] = N'LCZ' OR [S].[CCOMPANY] = N'ABC') AND [S].[QSALES] <> 0.50"));

		select = fromSales(SQLSERVER);
		select.getWhere().cond("S.CCOMPANY = 'LCZ' OR S.CCOMPANY = 'ABC'");
		sql("Condition with OR alone, not wrapped", select, lines(
				FROM_SALES,
				"WHERE [S].[CCOMPANY] = N'LCZ' OR [S].[CCOMPANY] = N'ABC'"));

		select = joined(SQLSERVER);
		where = select.getWhere();
		where.cond("S.QSALES BETWEEN ? AND ?", new Value(100), new Value(new BigDecimal("999.99")));
		where.value(AND, "A.TOBSOLETE", LT, new Value(LocalDate.of(2026, 1, 1)));
		sql("Placeholders and a date", select, lines(
				FROM_SALES,
				JOIN_ARTICLES,
				"WHERE [S].[QSALES] BETWEEN 100 AND 999.99 AND [A].[TOBSOLETE] < '2026-01-01'"));

		select = joined(SQLSERVER);
		where = select.getWhere();
		where.value("S.CCOMPANY", EQ, "LCZ");
		where.value(AND, "S.QSALES", GT, 1000);
		where.group(OR);
		where.in("A.CPTYPE", new Value("A"), new Value("B"));
		where.value(AND_NOT, "S.MCOST", EQ, 0);
		sql("Two groups of two conditions", select, lines(
				FROM_SALES,
				JOIN_ARTICLES,
				"WHERE ([S].[CCOMPANY] = N'LCZ' AND [S].[QSALES] > 1000)",
				"OR ([A].[CPTYPE] IN (N'A', N'B') AND NOT [S].[MCOST] = 0)"));

		select = fromSales(SQLSERVER);
		where = select.getWhere();
		where.value("S.CCOMPANY", EQ, "LCZ");
		where.group(OR_NOT);
		where.cond("S.QSALES > 0 AND S.MCOST > 0");
		sql("Groups of one condition, an AND condition wrapped", select, lines(
				FROM_SALES,
				"WHERE [S].[CCOMPANY] = N'LCZ'",
				"OR NOT ([S].[QSALES] > 0 AND [S].[MCOST] > 0)"));

		select = fromSales(SQLSERVER);
		where = select.getWhere();
		where.value("S.CCOMPANY", EQ, "LCZ");
		where.group(AND);
		sql("Last group still empty, not written", select, lines(
				FROM_SALES,
				"WHERE [S].[CCOMPANY] = N'LCZ'"));

		select = fromSales(SQLSERVER);
		select.getWhere().in(NOT, "S.CCOMPANY", new Value("LCZ"), new Value("ABC"));
		sql("NOT IN as the first condition", select, lines(
				FROM_SALES,
				"WHERE NOT [S].[CCOMPANY] IN (N'LCZ', N'ABC')"));

		select = fromSales(SQLSERVER);
		select.getWhere().cond(NOT, "S.QSALES > 0 OR S.MCOST > 0");
		sql("NOT before an OR alone: wrapped", select, lines(
				FROM_SALES,
				"WHERE NOT ([S].[QSALES] > 0 OR [S].[MCOST] > 0)"));

		select = fromSales(SQLSERVER);
		where = select.getWhere();
		where.value("S.CCOMPANY", EQ, "LCZ");
		where.group(OR);
		where.value(NOT, "S.QSALES", GT, 0);
		where.value(AND, "S.MCOST", GT, 0);
		sql("NOT in the first condition of a second group", select, lines(
				FROM_SALES,
				"WHERE [S].[CCOMPANY] = N'LCZ'",
				"OR (NOT [S].[QSALES] > 0 AND [S].[MCOST] > 0)"));

		error("NOT after the first condition", IllegalStateException.class, () -> {
			QWhere w = fromSales(SQLSERVER).getWhere();
			w.cond("S.QSALES > 0");
			w.cond(NOT, "S.MCOST > 0");
		});
		error("NOT linking a group", IllegalArgumentException.class, () -> {
			QWhere w = fromSales(SQLSERVER).getWhere();
			w.cond("S.QSALES > 0");
			w.group(NOT);
		});
		error("Condition before FROM", IllegalStateException.class, () -> {
			new QSelect(SQLSERVER).getWhere().cond("S.QSALES > 0");
		});
		error("Logical operator in the first condition", IllegalStateException.class, () -> {
			fromSales(SQLSERVER).getWhere().cond(AND, "S.QSALES > 0");
		});
		error("No logical operator after the first condition", IllegalStateException.class, () -> {
			QWhere w = fromSales(SQLSERVER).getWhere();
			w.cond("S.QSALES > 0");
			w.cond("S.MCOST > 0");
		});
		error("Null logical operator", NullPointerException.class, () -> {
			QWhere w = fromSales(SQLSERVER).getWhere();
			w.cond("S.QSALES > 0");
			w.cond((Logical) null, "S.MCOST > 0");
		});
		error("Group after an empty group", IllegalStateException.class, () -> {
			fromSales(SQLSERVER).getWhere().group(OR);
		});
		error("Column of a table not joined", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).getWhere().value("A.CPTYPE", EQ, "X");
		});
		error("Aggregate in WHERE", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).getWhere().value("SUM(S.QSALES)", GT, 0);
		});
		error("Value instead of a condition", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).getWhere().cond("S.QSALES * 2");
		});
		error("Comparison operand with an OR", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).getWhere().expr("S.QSALES > 0 OR S.MCOST", GT, "1");
		});
		error("Missing placeholder value", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).getWhere().cond("S.QSALES > ?");
		});
		error("Null value", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).getWhere().value("S.CCOMPANY", EQ, (String) null);
		});
		error("IN without values", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).getWhere().in("S.CCOMPANY");
		});

		select = fromSales(SQLSERVER);
		where = select.getWhere();
		where.value("S.CCOMPANY", EQ, "LCZ");
		String before = select.toString();
		try {
			where.value(AND, "S.UNKNOWN", EQ, 1);
		} catch (IllegalArgumentException exc) {
			/* Expected. */
		}
		sql("A rejected condition leaves the select as it was", select, before);
	}

	/**
	 * Checks the EXISTS selects: the outer tables they see, nesting, aliases
	 * unique in the tree, and their attachment.
	 */
	private static void exists() {
		section("EXISTS");
		String furn = lines(
				"\tSELECT 1 AS [NREF]",
				"\tFROM [EXPLMARG_QLT].[ARTICLES] [A]",
				"\tWHERE [A].[CARTICLE] = [S].[CARTICLE] AND [A].[CBUSINESS] = N'FURN'");
		QSelect select = fromSales(SQLSERVER);
		QWhere where = select.getWhere();
		where.value("S.CCOMPANY", EQ, "LCZ");
		where.exists(AND_NOT, articlesOf(select, "FURN"));
		sql("NOT EXISTS after a condition, using the outer table", select, lines(
				FROM_SALES,
				"WHERE [S].[CCOMPANY] = N'LCZ' AND NOT EXISTS (",
				furn,
				")"));

		select = fromSales(SQLSERVER);
		where = select.getWhere();
		where.value("S.CCOMPANY", EQ, "LCZ");
		where.group(OR);
		where.exists(articlesOf(select, "FURN"));
		where.value(AND, "S.CARTICLE", NE, "X");
		sql("EXISTS in a group of two conditions", select, lines(
				FROM_SALES,
				"WHERE [S].[CCOMPANY] = N'LCZ'",
				"OR (EXISTS (",
				furn,
				") AND [S].[CARTICLE] <> N'X')"));

		select = fromSales(SQLSERVER);
		where = select.getWhere();
		QSelect exists = where.createExists();
		exists.from(articles, "A");
		exists.addColumn("1", "NREF");
		QWhere existsWhere = exists.getWhere();
		existsWhere.expr("A.CARTICLE", EQ, "S.CARTICLE");
		QSelect nested = existsWhere.createExists();
		nested.from(businesses, "B");
		nested.addColumn("1", "NREF");
		QWhere nestedWhere = nested.getWhere();
		nestedWhere.expr("B.CBUSINESS", EQ, "A.CBUSINESS");
		nestedWhere.expr(AND, "B.CDIVISION", EQ, "S.CCOMPANY");
		existsWhere.exists(AND, nested);
		where.exists(exists);
		sql("Nested EXISTS, seeing both outer levels", select, lines(
				FROM_SALES,
				"WHERE EXISTS (",
				"\tSELECT 1 AS [NREF]",
				"\tFROM [EXPLMARG_QLT].[ARTICLES] [A]",
				"\tWHERE [A].[CARTICLE] = [S].[CARTICLE] AND EXISTS (",
				"\t\tSELECT 1 AS [NREF]",
				"\t\tFROM [EXPLMARG_QLT].[BUSINESSES] [B]",
				"\t\tWHERE [B].[CBUSINESS] = [A].[CBUSINESS] AND [B].[CDIVISION] = [S].[CCOMPANY]",
				"\t)",
				")"));

		select = fromSales(SQLSERVER);
		where = select.getWhere();
		where.exists(NOT, articlesOf(select, "FURN"));
		where.exists(OR, articlesOf(select, "TEXT"));
		String result = select.toString();
		boolean ok = result.startsWith(lines(FROM_SALES, "WHERE NOT EXISTS (")) && result.contains(") OR EXISTS (");
		report(ok, "NOT EXISTS first, sibling EXISTS selects reusing an alias", result, "both written, alias A twice");

		error("EXISTS select created for another select", IllegalArgumentException.class, () -> {
			QSelect other = fromSales(SQLSERVER);
			fromSales(SQLSERVER).getWhere().exists(articlesOf(other, "FURN"));
		});
		error("EXISTS select attached twice", IllegalStateException.class, () -> {
			QSelect s = fromSales(SQLSERVER);
			QSelect e = articlesOf(s, "FURN");
			QWhere w = s.getWhere();
			w.exists(e);
			w.exists(AND, e);
		});
		error("EXISTS select without FROM", IllegalStateException.class, () -> {
			QWhere w = fromSales(SQLSERVER).getWhere();
			w.exists(w.createExists());
		});
		error("EXISTS select without columns", IllegalStateException.class, () -> {
			QWhere w = fromSales(SQLSERVER).getWhere();
			QSelect e = w.createExists();
			e.from(articles, "A");
			w.exists(e);
		});
		error("Inner alias equal to an outer one", IllegalArgumentException.class, () -> {
			fromSales(SQLSERVER).getWhere().createExists().from(articles, "s");
		});
		error("Outer join added later with the alias of an attached EXISTS", IllegalArgumentException.class, () -> {
			QSelect s = fromSales(SQLSERVER);
			s.getWhere().exists(articlesOf(s, "FURN"));
			s.leftJoin(articles, "A", "S.CARTICLE = A.CARTICLE");
		});
		error("Outer join added before attaching, with an inner alias", IllegalArgumentException.class, () -> {
			QSelect s = fromSales(SQLSERVER);
			QSelect e = articlesOf(s, "FURN");
			s.leftJoin(articles, "A", "S.CARTICLE = A.CARTICLE");
			s.getWhere().exists(e);
		});
		error("Outer condition on an inner table", IllegalArgumentException.class, () -> {
			QSelect s = fromSales(SQLSERVER);
			QWhere w = s.getWhere();
			w.exists(articlesOf(s, "FURN"));
			w.value(AND, "A.CBUSINESS", EQ, "FURN");
		});
		error("Bare column in an EXISTS select", IllegalArgumentException.class, () -> {
			QSelect e = fromSales(SQLSERVER).getWhere().createExists();
			e.from(articles, "A");
			e.getWhere().value("CBUSINESS", EQ, "FURN");
		});

		select = fromSales(SQLSERVER);
		where = select.getWhere();
		where.value("S.CCOMPANY", EQ, "LCZ");
		QSelect empty = where.createExists();
		try {
			where.exists(AND, empty);
		} catch (IllegalStateException exc) {
			/* Expected. */
		}
		empty.from(articles, "A");
		empty.addColumn("1", "NREF");
		where.exists(AND, empty);
		report(select.toString().contains("[A]"), "A rejected EXISTS select can be attached once completed",
				select.toString(), "the EXISTS written");

		select = fromSales(SQLSERVER);
		where = select.getWhere();
		where.value("S.CCOMPANY", EQ, "LCZ");
		try {
			where.exists(articlesOf(select, "FURN"));
		} catch (IllegalStateException exc) {
			/* Expected, the logical operator is missing. */
		}
		String joinedResult;
		try {
			select.leftJoin(articles, "A", "S.CARTICLE = A.CARTICLE");
			joinedResult = "join added";
		} catch (IllegalArgumentException exc) {
			joinedResult = exc.getMessage();
		}
		equal("A rejected EXISTS select reserves no alias", joinedResult, "join added");
	}

	/**
	 * Checks GROUP BY, generated from the select list.
	 */
	private static void groupBy() {
		section("GROUP BY");
		QSelect select = fromSales(SQLSERVER);
		select.addColumn("S.CCOMPANY", "CCOMPANY");
		select.addColumn("S.CARTICLE", "CARTICLE");
		select.groupBy();
		sql("No aggregates: all the expressions", select, lines(
				"SELECT [S].[CCOMPANY] AS [CCOMPANY], [S].[CARTICLE] AS [CARTICLE]",
				FROM_SALES,
				"GROUP BY [S].[CCOMPANY], [S].[CARTICLE]"));

		select = joined(SQLSERVER);
		select.addColumn("S.CCOMPANY", "CCOMPANY");
		select.addColumn("A.CBUSINESS", "CBUSINESS");
		select.addColumn("SUM(S.QSALES)", "QSALES");
		select.addColumn("COUNT(*)", "NSALES");
		select.getWhere().value("S.QSALES", GT, 0);
		select.groupBy();
		sql("Aggregates left out, after WHERE", select, lines(
				"SELECT [S].[CCOMPANY] AS [CCOMPANY], [A].[CBUSINESS] AS [CBUSINESS], SUM([S].[QSALES]) AS [QSALES], COUNT(*) AS [NSALES]",
				FROM_SALES,
				JOIN_ARTICLES,
				"WHERE [S].[QSALES] > 0",
				"GROUP BY [S].[CCOMPANY], [A].[CBUSINESS]"));

		select = fromSales(SQLSERVER);
		select.groupBy();
		select.addColumn("S.CCOMPANY || '-' || S.CARTICLE", "CODE");
		select.addColumn("1", "NREF");
		select.addColumn("MAX(S.QSALES)", "QMAX");
		sql("Expression grouped, constant left out, columns added after groupBy", select, lines(
				"SELECT CONCAT([S].[CCOMPANY], N'-', [S].[CARTICLE]) AS [CODE], 1 AS [NREF], MAX([S].[QSALES]) AS [QMAX]",
				FROM_SALES,
				"GROUP BY CONCAT([S].[CCOMPANY], N'-', [S].[CARTICLE])"));

		select = fromSales(SQLSERVER);
		select.addColumn("SUM(S.QSALES)", "QSALES");
		select.groupBy();
		sql("Only aggregates: no GROUP BY", select, lines(
				"SELECT SUM([S].[QSALES]) AS [QSALES]",
				FROM_SALES));

		select = fromSales(SQLSERVER);
		QWhere where = select.getWhere();
		QSelect exists = where.createExists();
		exists.from(articles, "A");
		exists.addColumn("A.CBUSINESS", "CBUSINESS");
		exists.addColumn("S.CCOMPANY", "CCOMPANY");
		exists.addColumn("COUNT(*)", "NARTICLES");
		exists.getWhere().expr("A.CARTICLE", EQ, "S.CARTICLE");
		exists.groupBy();
		select.addColumn("S.CARTICLE", "CARTICLE");
		where.exists(exists);
		sql("EXISTS select grouped: outer columns left out", select, lines(
				"SELECT [S].[CARTICLE] AS [CARTICLE]",
				FROM_SALES,
				"WHERE EXISTS (",
				"\tSELECT [A].[CBUSINESS] AS [CBUSINESS], [S].[CCOMPANY] AS [CCOMPANY], COUNT(*) AS [NARTICLES]",
				"\tFROM [EXPLMARG_QLT].[ARTICLES] [A]",
				"\tWHERE [A].[CARTICLE] = [S].[CARTICLE]",
				"\tGROUP BY [A].[CBUSINESS]",
				")"));

		error("Grouped twice", IllegalStateException.class, () -> {
			QSelect s = fromSales(SQLSERVER);
			s.groupBy();
			s.groupBy();
		});
		error("EXISTS select with aggregates and other expressions, not grouped", IllegalStateException.class, () -> {
			QWhere w = fromSales(SQLSERVER).getWhere();
			QSelect e = w.createExists();
			e.from(articles, "A");
			e.addColumn("A.CBUSINESS", "CBUSINESS");
			e.addColumn("COUNT(*)", "NARTICLES");
			w.exists(e);
		});

		select = fromSales(SQLSERVER);
		where = select.getWhere();
		exists = where.createExists();
		exists.from(articles, "A");
		exists.addColumn("S.CCOMPANY", "CCOMPANY");
		exists.addColumn("COUNT(*)", "NARTICLES");
		exists.getWhere().expr("A.CARTICLE", EQ, "S.CARTICLE");
		String attached;
		try {
			where.exists(exists);
			attached = "attached";
		} catch (IllegalStateException exc) {
			attached = exc.getMessage();
		}
		equal("Aggregates and outer columns only: no GROUP BY needed", attached, "attached");
	}

	/**
	 * Checks the HAVING condition.
	 */
	private static void having() {
		section("HAVING");
		QSelect select = byCompany(SQLSERVER);
		select.having("SUM(S.QSALES) > ?", new Value(1000));
		select.orderBy("QSALES DESC");
		sql("Aggregate with a placeholder, before ORDER BY", select, lines(
				"SELECT [S].[CCOMPANY] AS [CCOMPANY], SUM([S].[QSALES]) AS [QSALES], SUM([S].[MCOST]) AS [MCOST]",
				FROM_SALES,
				"WHERE [S].[NYEAR] = 2026",
				"GROUP BY [S].[CCOMPANY]",
				"HAVING SUM([S].[QSALES]) > 1000",
				"ORDER BY [QSALES] DESC"));

		select = byCompany(SQLSERVER);
		select.having("sum(qsales) * 2 > SUM(S.MCOST) OR SUM(S.QSALES) - SUM(S.MCOST) > 0");
		sql("Arithmetic around aggregates, matched ignoring case and qualification", select, lines(
				"SELECT [S].[CCOMPANY] AS [CCOMPANY], SUM([S].[QSALES]) AS [QSALES], SUM([S].[MCOST]) AS [MCOST]",
				FROM_SALES,
				"WHERE [S].[NYEAR] = 2026",
				"GROUP BY [S].[CCOMPANY]",
				"HAVING SUM([S].[QSALES]) * 2 > SUM([S].[MCOST]) OR SUM([S].[QSALES]) - SUM([S].[MCOST]) > 0"));

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
			s.having("SUM(S.MCOST) > 0");
		});
	}

	/**
	 * Checks the ORDER BY clause.
	 */
	private static void orderBy() {
		section("ORDER BY");
		QSelect select = byCompany(SQLSERVER);
		select.orderBy("  qsales   desc ", "ccompany ASC");
		sql("Aliases ignoring case, ASC not written, spaces trimmed", select, lines(
				"SELECT [S].[CCOMPANY] AS [CCOMPANY], SUM([S].[QSALES]) AS [QSALES], SUM([S].[MCOST]) AS [MCOST]",
				FROM_SALES,
				"WHERE [S].[NYEAR] = 2026",
				"GROUP BY [S].[CCOMPANY]",
				"ORDER BY [QSALES] DESC, [CCOMPANY]"));

		error("Unknown alias", IllegalArgumentException.class, () -> {
			byCompany(SQLSERVER).orderBy("NDAY");
		});
		error("Wrong direction", IllegalArgumentException.class, () -> {
			byCompany(SQLSERVER).orderBy("CCOMPANY DOWN");
		});
		error("Too many words", IllegalArgumentException.class, () -> {
			byCompany(SQLSERVER).orderBy("CCOMPANY DESC NULLS");
		});
		error("Column repeated", IllegalArgumentException.class, () -> {
			byCompany(SQLSERVER).orderBy("CCOMPANY", "ccompany DESC");
		});
		error("Blank segment", IllegalArgumentException.class, () -> {
			byCompany(SQLSERVER).orderBy("CCOMPANY", "  ");
		});
		error("No segments", IllegalArgumentException.class, () -> {
			byCompany(SQLSERVER).orderBy();
		});
		error("Ordered twice", IllegalStateException.class, () -> {
			QSelect s = byCompany(SQLSERVER);
			s.orderBy("CCOMPANY");
			s.orderBy("QSALES");
		});
	}

	/**
	 * Checks a select with all the clauses written for Oracle.
	 */
	private static void oracle() {
		section("Oracle");
		QSelect select = joined(ORACLE);
		select.addColumn("S.CCOMPANY", "CCOMPANY");
		select.addColumn("A.CBUSINESS || '-' || A.CPTYPE", "CODE");
		select.addColumn("SUM(S.QSALES)", "QSALES");
		QWhere where = select.getWhere();
		where.value("S.CCOMPANY", EQ, "LCZ");
		where.value(AND, "A.TOBSOLETE", GE, new Value(LocalDate.of(2026, 1, 1)));
		where.group(OR);
		where.value("S.QSALES", LT, 0.5);
		select.groupBy();
		select.having("SUM(S.QSALES) BETWEEN ? AND ?", new Value(100), new Value(5000));
		select.orderBy("CCOMPANY DESC");
		sql("Join, concatenation, groups, a date, GROUP BY, HAVING and ORDER BY", select, lines(
				"SELECT \"S\".\"CCOMPANY\" AS \"CCOMPANY\", (\"A\".\"CBUSINESS\" || N'-' || \"A\".\"CPTYPE\") AS \"CODE\", SUM(\"S\".\"QSALES\") AS \"QSALES\"",
				"FROM \"EXPLMARG_QLT\".\"WC_SALES\" \"S\"",
				"LEFT JOIN \"EXPLMARG_QLT\".\"ARTICLES\" \"A\" ON \"S\".\"CARTICLE\" = \"A\".\"CARTICLE\"",
				"WHERE (\"S\".\"CCOMPANY\" = N'LCZ' AND \"A\".\"TOBSOLETE\" >= DATE '2026-01-01')",
				"OR \"S\".\"QSALES\" < 0.5",
				"GROUP BY \"S\".\"CCOMPANY\", (\"A\".\"CBUSINESS\" || N'-' || \"A\".\"CPTYPE\")",
				"HAVING SUM(\"S\".\"QSALES\") BETWEEN 100 AND 5000",
				"ORDER BY \"CCOMPANY\" DESC"));
	}

	/**
	 * Runs selects on the local SQL Server and compares their rows with
	 * hand-written queries.
	 */
	private static void runs() {
		section("Runs on SQL Server");
		connect();
		QSelect select = new QSelect(SQLSERVER);
		select.from(pieces, "P");
		select.innerJoin(businesses, "B", "P.CBUSINESS = B.CBUSINESS");
		select.addColumn("B.CDIVISION", "CDIVISION");
		select.addColumn("COUNT(*)", "NPIECES");
		QWhere where = select.getWhere();
		where.value("P.CPTYPE", GE, "A");
		where.in(AND_NOT, "P.CUNIT", new Value("XX"), new Value("YY"));
		where.group(OR);
		where.cond("P.CUNIT IS NULL");
		select.groupBy();
		select.having("COUNT(*) > ?", new Value(1));
		select.orderBy("CDIVISION");
		sameRows("Join, groups, NOT IN, IS NULL, GROUP BY, HAVING and ORDER BY", select, lines(
				"SELECT B.CDIVISION, COUNT(*) FROM EXPLMARG_QLT.TPIECES P",
				"INNER JOIN EXPLMARG_QLT.BUSINESSES B ON P.CBUSINESS = B.CBUSINESS",
				"WHERE (P.CPTYPE >= 'A' AND P.CUNIT NOT IN ('XX', 'YY')) OR P.CUNIT IS NULL OR P.CUNIT = ''",
				"GROUP BY B.CDIVISION HAVING COUNT(*) > 1 ORDER BY 1"));

		select = new QSelect(SQLSERVER);
		select.from(businesses, "B");
		select.addColumn("B.CBUSINESS", "CBUSINESS");
		where = select.getWhere();
		QSelect withPieces = where.createExists();
		withPieces.from(pieces, "P");
		withPieces.addColumn("1", "NREF");
		withPieces.getWhere().expr("P.CBUSINESS", EQ, "B.CBUSINESS");
		where.exists(withPieces);
		QSelect withSpares = where.createExists();
		withSpares.from(pieces, "P");
		withSpares.addColumn("1", "NREF");
		QWhere sparesWhere = withSpares.getWhere();
		sparesWhere.expr("P.CBUSINESS", EQ, "B.CBUSINESS");
		sparesWhere.value(AND, "P.ISPAREPART", EQ, "Y");
		where.exists(AND_NOT, withSpares);
		select.orderBy("CBUSINESS");
		sameRows("EXISTS and NOT EXISTS, reusing an alias", select, lines(
				"SELECT B.CBUSINESS FROM EXPLMARG_QLT.BUSINESSES B",
				"WHERE B.CBUSINESS IN (SELECT CBUSINESS FROM EXPLMARG_QLT.TPIECES)",
				"AND B.CBUSINESS NOT IN (SELECT CBUSINESS FROM EXPLMARG_QLT.TPIECES WHERE ISPAREPART = 'Y')",
				"ORDER BY 1"));

		select = widest(SQLSERVER, pieces, "S.CPTYPE");
		select.getWhere().cond("1 = 0");
		String columns;
		try (DBConnection cn = SQLSERVER.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(select.toString())) {
			int count = rs.getMetaData().getColumnCount();
			columns = String.valueOf(count);
		} catch (SQLException exc) {
			columns = exc.getMessage();
		}
		equal("The widest select executed", columns, "4096");
	}

	/**
	 * Runs the checks.
	 *
	 * @param db True to also run selects on the local SQL Server.
	 */
	public static void run(boolean db) {
		Check.start("TestQSelect");
		sales = table("WC_SALES");
		articles = table("ARTICLES");
		businesses = table("BUSINESSES");
		pieces = table("TPIECES");
		joins();
		columns();
		where();
		exists();
		groupBy();
		having();
		orderBy();
		oracle();
		if (db) {
			runs();
		}
	}

	/**
	 * Entry point.
	 *
	 * @param args Pass "db" to also run selects on the local SQL Server.
	 */
	public static void main(String[] args) {
		boolean db = args.length > 0 && args[0].equals("db");
		run(db);
		summary();
		System.exit(0);
	}
}
