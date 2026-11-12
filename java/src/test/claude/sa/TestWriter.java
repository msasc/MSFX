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

package test.claude.sa;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import msfx.lib.db.DBEngine;
import msfx.lib.db.adapters.DBEngineOracle;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.db.query.sa.Analysis;
import msfx.lib.db.query.sa.Clause;
import msfx.lib.db.query.sa.Node;
import msfx.lib.db.query.sa.Parser;
import msfx.lib.db.query.sa.SyntaxException;
import msfx.lib.db.query.sa.Validator;
import msfx.lib.db.query.sa.Writer;
import msfx.lib.md.Column;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import msfx.lib.md.Value;

/**
 * Tests the {@link Writer}: the SQL written for SQL Server and Oracle, without
 * connecting to any database.
 *
 * @author Miquel Sas
 */
public class TestWriter {

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
	 * Analyzes an expression and writes it for both databases.
	 *
	 * @param clause     The clause.
	 * @param tables     The tables in scope.
	 * @param expression The expression.
	 * @param sqlServer  The expected SQL of SQL Server.
	 * @param oracle     The expected SQL of Oracle.
	 * @param values     The values of the placeholders.
	 */
	private static void check(
			Clause clause,
			Map<String, Table> tables,
			String expression,
			String sqlServer,
			String oracle,
			Value... values) {
		System.out.print("   ");
		System.out.print(clause);
		System.out.print(" ");
		System.out.println(expression);
		Analysis analysis;
		try {
			Parser parser = new Parser(expression);
			Node root = (clause == Clause.SELECT ? parser.parseExpression() : parser.parseCondition());
			analysis = new Validator(expression, root, clause, tables, List.of(values), List.of()).validate();
		} catch (SyntaxException exc) {
			failed++;
			System.out.print("      FAIL  ");
			System.out.println(exc.getMessage());
			return;
		}
		compare("SQL Server", new Writer(SQLSERVER, analysis).write(), sqlServer);
		compare("Oracle    ", new Writer(ORACLE, analysis).write(), oracle);
	}

	/**
	 * Compares the SQL written with the expected one.
	 *
	 * @param database The name of the database.
	 * @param result   The SQL written.
	 * @param expected The expected SQL.
	 */
	private static void compare(String database, String result, String expected) {
		boolean ok = expected.equals(result);
		if (ok) {
			passed++;
		} else {
			failed++;
		}
		StringBuilder line = new StringBuilder();
		line.append(ok ? "      ok    " : "      FAIL  ");
		line.append(database);
		line.append("  ");
		line.append(result);
		if (!ok) {
			line.append("\n            expected    ");
			line.append(expected);
		}
		System.out.println(line);
	}

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {

		Table sales = new Table("SALES");
		sales.columns().add(new Column("CCOMPANY", Types.STRING, 10, -1));
		sales.columns().add(new Column("CARTICLE", Types.STRING, 40, -1));
		sales.columns().add(new Column("NYEAR", Types.INTEGER, -1, -1));
		sales.columns().add(new Column("QSALES", Types.DECIMAL, 16, 2));
		sales.columns().add(new Column("DSALE", Types.DATE, -1, -1));

		Table articles = new Table("ARTICLES");
		articles.columns().add(new Column("CARTICLE", Types.STRING, 40, -1));

		Map<String, Table> one = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		one.put("S", sales);
		Map<String, Table> two = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		two.put("S", sales);
		two.put("A", articles);

		System.out.println("== Names");
		check(Clause.ON, two, "s.carticle = a.Carticle",
				"[S].[CARTICLE] = [A].[CARTICLE]",
				"\"S\".\"CARTICLE\" = \"A\".\"CARTICLE\"");
		check(Clause.WHERE, one, "QSALES > 1500.50",
				"[S].[QSALES] > 1500.50",
				"\"S\".\"QSALES\" > 1500.50");

		System.out.println("== Placeholders");
		check(Clause.WHERE, one, "CCOMPANY = ? AND DSALE >= ?",
				"[S].[CCOMPANY] = N'LCZ' AND [S].[DSALE] >= '2026-01-01'",
				"\"S\".\"CCOMPANY\" = N'LCZ' AND \"S\".\"DSALE\" >= DATE '2026-01-01'",
				new Value("LCZ"), new Value(LocalDate.of(2026, 1, 1)));
		check(Clause.WHERE, one, "CCOMPANY = ?",
				"[S].[CCOMPANY] = N'it''s'",
				"\"S\".\"CCOMPANY\" = N'it''s'",
				new Value("it's"));
		check(Clause.WHERE, one, "-? = 5",
				"- -5 = 5",
				"- -5 = 5",
				new Value(-5));

		System.out.println("== Null checks");
		check(Clause.WHERE, one, "CCOMPANY IS NULL OR NYEAR IS NOT NULL",
				"([S].[CCOMPANY] IS NULL OR [S].[CCOMPANY] = '') OR [S].[NYEAR] IS NOT NULL",
				"\"S\".\"CCOMPANY\" IS NULL OR \"S\".\"NYEAR\" IS NOT NULL");
		check(Clause.WHERE, one, "CCOMPANY IS NOT NULL",
				"[S].[CCOMPANY] <> ''",
				"\"S\".\"CCOMPANY\" IS NOT NULL");

		System.out.println("== Concatenation and functions");
		check(Clause.SELECT, one, "CCOMPANY || '-' || NYEAR",
				"CONCAT([S].[CCOMPANY], N'-', [S].[NYEAR])",
				"(\"S\".\"CCOMPANY\" || N'-' || \"S\".\"NYEAR\")");
		check(Clause.SELECT, one, "COUNT(DISTINCT CARTICLE)",
				"COUNT(DISTINCT [S].[CARTICLE])",
				"COUNT(DISTINCT \"S\".\"CARTICLE\")");
		check(Clause.SELECT, one, "COUNT(*)",
				"COUNT(*)",
				"COUNT(*)");
		check(Clause.WHERE, one, "LENGTH(CCOMPANY) > 2 AND NOT (NYEAR IN (2024, 2025))",
				"COALESCE(LEN(([S].[CCOMPANY]) + N'x') - 1, 0) > 2 AND NOT ([S].[NYEAR] IN (2024, 2025))",
				"COALESCE(LENGTH(\"S\".\"CCOMPANY\"), 0) > 2 AND NOT (\"S\".\"NYEAR\" IN (2024, 2025))");

		System.out.println("== CASE");
		check(Clause.SELECT, one, "CASE WHEN QSALES > 1000 THEN 'HIGH' ELSE '' END",
				"CASE WHEN [S].[QSALES] > 1000 THEN N'HIGH' ELSE N'' END",
				"CASE WHEN \"S\".\"QSALES\" > 1000 THEN N'HIGH' ELSE N'' END");

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
