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

import static test.claude.Check.*;
import static test.claude.Fixture.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.query.sa.Analysis;
import msfx.lib.db.query.sa.Clause;
import msfx.lib.db.query.sa.Function;
import msfx.lib.db.query.sa.Node;
import msfx.lib.db.query.sa.Parser;
import msfx.lib.db.query.sa.SyntaxException;
import msfx.lib.db.query.sa.Validator;
import msfx.lib.db.query.sa.Writer;
import msfx.lib.md.Column;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import msfx.lib.md.Value;
import test.claude.Check;

/**
 * Tests the {@link Writer}: the SQL written for SQL Server and Oracle on the
 * CMA tables WC_SALES S and ARTICLES A. With "db", runs written expressions and
 * the SQL Server translations of the functions on the local SQL Server, with
 * SELECTs of literals only: no table is read or changed.
 *
 * @author Miquel Sas
 */
public class TestWriter {

	/**
	 * Parses and validates an expression.
	 *
	 * @param clause     The clause.
	 * @param tables     The tables in scope.
	 * @param expression The expression.
	 * @param values     The values of the placeholders.
	 * @return The analysis.
	 */
	private static Analysis analyze(Clause clause, Map<String, Table> tables, String expression, Value... values) {
		Parser parser = new Parser(expression);
		Node root = (clause == Clause.SELECT ? parser.parseExpression() : parser.parseCondition());
		Validator validator = new Validator(expression, root, clause, tables, List.of(values), List.of());
		Analysis analysis = validator.validate();
		return analysis;
	}

	/**
	 * Analyzes an expression and checks the SQL written for both databases.
	 *
	 * @param clause     The clause.
	 * @param tables     The tables in scope.
	 * @param expression The expression.
	 * @param sqlServer  The expected SQL of SQL Server.
	 * @param oracle     The expected SQL of Oracle.
	 * @param values     The values of the placeholders.
	 */
	private static void write(Clause clause, Map<String, Table> tables, String expression, String sqlServer, String oracle,
			Value... values) {
		StringBuilder title = new StringBuilder();
		title.append(clause);
		title.append(" ");
		title.append(expression);
		Analysis analysis;
		try {
			analysis = analyze(clause, tables, expression, values);
		} catch (SyntaxException exc) {
			report(false, title.toString(), exc.getMessage(), sqlServer);
			return;
		}
		String written = new Writer(SQLSERVER, analysis).write();
		StringBuilder oracleTitle = new StringBuilder();
		oracleTitle.append(title);
		oracleTitle.append(", Oracle");
		equal(title.toString(), written, sqlServer);
		written = new Writer(ORACLE, analysis).write();
		equal(oracleTitle.toString(), written, oracle);
	}

	/**
	 * Selects an expression written by the writer over a row of literals on SQL
	 * Server, and checks the result: a condition as CASE WHEN condition THEN 1
	 * ELSE 0 END, a value as it is.
	 *
	 * @param tables     The table of the row, as S.
	 * @param from       The FROM clause with the row.
	 * @param clause     WHERE for a condition, SELECT for a value.
	 * @param expression The expression.
	 * @param expected   The expected result, read as a string.
	 * @param values     The values of the placeholders.
	 */
	private static void select(Map<String, Table> tables, String from, Clause clause, String expression, String expected,
			Value... values) {
		String written;
		try {
			Analysis analysis = analyze(clause, tables, expression, values);
			written = new Writer(SQLSERVER, analysis).write();
		} catch (SyntaxException exc) {
			report(false, expression, exc.getMessage(), expected);
			return;
		}
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT ");
		if (clause == Clause.SELECT) {
			sql.append(written);
		} else {
			sql.append("CASE WHEN ");
			sql.append(written);
			sql.append(" THEN 1 ELSE 0 END");
		}
		sql.append(from);
		String result = run(sql.toString());
		StringBuilder shown = new StringBuilder();
		shown.append(result);
		shown.append("\n");
		shown.append(written);
		report(expected.equals(result), expression, shown.toString(), expected);
	}

	/**
	 * Selects a function applied to the arguments on SQL Server and checks the
	 * result.
	 *
	 * @param function  The function.
	 * @param expected  The expected result, read as a string.
	 * @param arguments The arguments, written in SQL.
	 */
	private static void function(Function function, String expected, String... arguments) {
		String expr = SQLSERVER.getFunctionSQL(function, List.of(arguments));
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT ");
		sql.append(expr);
		String result = run(sql.toString());
		StringBuilder title = new StringBuilder();
		title.append(function);
		title.append(" ");
		title.append(List.of(arguments));
		StringBuilder shown = new StringBuilder();
		shown.append(result);
		shown.append("\n");
		shown.append(expr);
		report(expected.equals(result), title.toString(), shown.toString(), expected);
	}

	/**
	 * Runs a query of one row and one column on SQL Server.
	 *
	 * @param sql The query.
	 * @return The value read as a string, or the error.
	 */
	private static String run(String sql) {
		try (DBConnection cn = SQLSERVER.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql)) {
			rs.next();
			String value = rs.getString(1);
			return value;
		} catch (SQLException exc) {
			StringBuilder error = new StringBuilder();
			error.append("error: ");
			error.append(exc.getMessage());
			return error.toString();
		}
	}

	/**
	 * Checks the SQL written for both databases.
	 */
	private static void written() {
		Map<String, Table> one = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		one.put("S", table("WC_SALES"));
		Map<String, Table> two = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		two.putAll(one);
		two.put("A", table("ARTICLES"));

		section("Names");
		write(Clause.ON, two, "s.carticle = a.Carticle",
				"[S].[CARTICLE] = [A].[CARTICLE]",
				"\"S\".\"CARTICLE\" = \"A\".\"CARTICLE\"");
		write(Clause.WHERE, one, "QSALES > 1500.50",
				"[S].[QSALES] > 1500.50",
				"\"S\".\"QSALES\" > 1500.50");

		section("Placeholders");
		write(Clause.WHERE, two, "S.CCOMPANY = ? AND A.TOBSOLETE >= ?",
				"[S].[CCOMPANY] = N'LCZ' AND [A].[TOBSOLETE] >= '2026-01-01'",
				"\"S\".\"CCOMPANY\" = N'LCZ' AND \"A\".\"TOBSOLETE\" >= DATE '2026-01-01'",
				new Value("LCZ"), new Value(LocalDate.of(2026, 1, 1)));
		write(Clause.WHERE, one, "CCOMPANY = ?",
				"[S].[CCOMPANY] = N'it''s'",
				"\"S\".\"CCOMPANY\" = N'it''s'",
				new Value("it's"));
		write(Clause.WHERE, one, "-? = 5",
				"- -5 = 5",
				"- -5 = 5",
				new Value(-5));

		section("Null checks");
		write(Clause.WHERE, one, "CCOMPANY IS NULL OR NYEAR IS NOT NULL",
				"([S].[CCOMPANY] IS NULL OR [S].[CCOMPANY] = '') OR [S].[NYEAR] IS NOT NULL",
				"\"S\".\"CCOMPANY\" IS NULL OR \"S\".\"NYEAR\" IS NOT NULL");
		write(Clause.WHERE, one, "CCOMPANY IS NOT NULL",
				"[S].[CCOMPANY] <> ''",
				"\"S\".\"CCOMPANY\" IS NOT NULL");

		section("Concatenation, functions and CASE");
		write(Clause.SELECT, one, "CCOMPANY || '-' || NYEAR",
				"CONCAT([S].[CCOMPANY], N'-', [S].[NYEAR])",
				"(\"S\".\"CCOMPANY\" || N'-' || \"S\".\"NYEAR\")");
		write(Clause.SELECT, one, "COUNT(DISTINCT CARTICLE)",
				"COUNT(DISTINCT [S].[CARTICLE])",
				"COUNT(DISTINCT \"S\".\"CARTICLE\")");
		write(Clause.SELECT, one, "COUNT(*)",
				"COUNT(*)",
				"COUNT(*)");
		write(Clause.WHERE, one, "LENGTH(CCOMPANY) > 2 AND NOT (NYEAR IN (2024, 2025))",
				"COALESCE(LEN(([S].[CCOMPANY]) + N'x') - 1, 0) > 2 AND NOT ([S].[NYEAR] IN (2024, 2025))",
				"COALESCE(LENGTH(\"S\".\"CCOMPANY\"), 0) > 2 AND NOT (\"S\".\"NYEAR\" IN (2024, 2025))");
		write(Clause.SELECT, one, "CASE WHEN QSALES > 1000 THEN 'HIGH' ELSE '' END",
				"CASE WHEN [S].[QSALES] > 1000 THEN N'HIGH' ELSE N'' END",
				"CASE WHEN \"S\".\"QSALES\" > 1000 THEN N'HIGH' ELSE N'' END");
	}

	/**
	 * Runs written expressions over a row of literals on SQL Server. The table
	 * of the row is made by hand: it only describes the literals.
	 */
	private static void expressions() {
		Table row = new Table("ROW");
		row.columns().add(new Column("CCOMPANY", Types.STRING, 10, -1));
		row.columns().add(new Column("CARTICLE", Types.STRING, 40, -1));
		row.columns().add(new Column("NYEAR", Types.INTEGER, -1, -1));
		row.columns().add(new Column("QSALES", Types.DECIMAL, 16, 2));
		row.columns().add(new Column("QCOST", Types.DECIMAL, 16, 2));
		row.columns().add(new Column("DSALE", Types.DATE, -1, -1));
		row.columns().add(new Column("CNOTE", Types.STRING, 40, -1));
		Map<String, Table> tables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		tables.put("S", row);
		StringBuilder b = new StringBuilder();
		b.append(" FROM (SELECT N'LCZ' AS [CCOMPANY], N'A325-X' AS [CARTICLE], 2026 AS [NYEAR], ");
		b.append("CAST(1500.50 AS DECIMAL(16, 2)) AS [QSALES], CAST(900.25 AS DECIMAL(16, 2)) AS [QCOST], ");
		b.append("CAST('2026-11-11' AS DATE) AS [DSALE], N'' AS [CNOTE]) AS [S]");
		String from = b.toString();

		section("Conditions run on SQL Server");
		select(tables, from, Clause.WHERE, "CCOMPANY = ? AND NYEAR = ?", "1", new Value("LCZ"), new Value(2026));
		select(tables, from, Clause.WHERE, "s.ccompany = 'LCZ'", "1");
		select(tables, from, Clause.WHERE, "CARTICLE LIKE 'A325%'", "1");
		select(tables, from, Clause.WHERE, "CNOTE IS NULL", "1");
		select(tables, from, Clause.WHERE, "CNOTE IS NOT NULL", "0");
		select(tables, from, Clause.WHERE, "CCOMPANY IS NOT NULL", "1");
		select(tables, from, Clause.WHERE, "DSALE = ?", "1", new Value(LocalDate.of(2026, 11, 11)));
		select(tables, from, Clause.WHERE, "DSALE BETWEEN TO_DATE('2026-01-01') AND TO_DATE('2026-12-31')", "1");
		select(tables, from, Clause.WHERE, "QSALES - QCOST > 600", "1");
		select(tables, from, Clause.WHERE, "NOT (NYEAR IN (2024, 2025))", "1");
		select(tables, from, Clause.WHERE, "LENGTH(CCOMPANY || '-' || CARTICLE) = 10", "1");
		select(tables, from, Clause.WHERE, "NYEAR - -1 = 2027", "1");
		select(tables, from, Clause.WHERE, "-? = 5", "1", new Value(-5));
		select(tables, from, Clause.WHERE, "? = 'it''s'", "1", new Value("it's"));
		select(tables, from, Clause.WHERE, "CASE WHEN QSALES > 1000 THEN 'HIGH' ELSE 'LOW' END = 'HIGH'", "1");

		section("Values run on SQL Server");
		select(tables, from, Clause.SELECT, "CCOMPANY || '/' || NYEAR", "LCZ/2026");
		select(tables, from, Clause.SELECT, "CCOMPANY || CNOTE || '.'", "LCZ.");
		select(tables, from, Clause.SELECT, "QSALES * 2", "3001.00");
		select(tables, from, Clause.SELECT, "SUM(QSALES)", "1500.50");
		select(tables, from, Clause.SELECT, "COUNT(DISTINCT CARTICLE)", "1");
		select(tables, from, Clause.SELECT, "LPAD(CCOMPANY, 6, '0')", "000LCZ");
		select(tables, from, Clause.SELECT, "CASE WHEN CNOTE IS NULL THEN 'EMPTY' ELSE CNOTE END", "EMPTY");
	}

	/**
	 * Runs the SQL Server translations of the functions over literals.
	 */
	private static void functions() {
		section("Functions run on SQL Server: strings");
		function(Function.LENGTH, "5", "N'ab   '");
		function(Function.LENGTH, "3", "N'   '");
		function(Function.LENGTH, "0", "N''");
		function(Function.LENGTH, "0", "CAST(NULL AS NVARCHAR(10))");
		function(Function.LEFT, "ab", "N'abcd'", "2");
		function(Function.RIGHT, "abcd", "N'abcd'", "9");
		function(Function.POSITION, "3", "N'abcd'", "N'cd'");
		function(Function.POSITION, "0", "N'abcd'", "N'x'");
		function(Function.LPAD, "000ab", "N'ab'", "5", "N'0'");
		function(Function.LPAD, "abc", "N'abcdef'", "3", "N'0'");
		function(Function.LPAD, "   ab", "N'ab'", "5");
		function(Function.RPAD, "ab000", "N'ab'", "5", "N'0'");
		function(Function.RPAD, "abc", "N'abcdef'", "3", "N'0'");
		function(Function.RPAD, "ab   ", "N'ab'", "5");

		section("Functions run on SQL Server: numbers, dates and conversions");
		function(Function.ROUND, "3.000", "2.567");
		function(Function.ROUND, "2.570", "2.567", "2");
		function(Function.TRUNCATE, "2.000", "2.567");
		function(Function.TRUNCATE, "2.560", "2.567", "2");
		function(Function.MOD, "1", "7", "2 * 3");
		function(Function.CURRENT_DATE, LocalDate.now().toString());
		function(Function.HOUR, "13", "CAST('2026-11-11T13:45:30' AS DATETIME2)");
		function(Function.SECOND, "30", "CAST('2026-11-11T13:45:30.7' AS DATETIME2)");
		function(Function.ADD_DAYS, "2026-11-13", "CAST('2026-11-11' AS DATE)", "2");
		function(Function.ADD_MONTHS, "2027-01-11", "CAST('2026-11-11' AS DATE)", "2");
		function(Function.DAYS_BETWEEN, "3", "CAST('2026-11-11T23:00:00' AS DATETIME2)", "CAST('2026-11-14T01:00:00' AS DATETIME2)");
		function(Function.TO_NUMBER, "12.5000000000", "N'12.5'");
		function(Function.TO_DATE, "2026-11-11", "N'2026-11-11'");
		function(Function.TO_TIMESTAMP, "2026-11-11 13:45:30.1230000", "N'2026-11-11T13:45:30.123'");
		function(Function.TO_TIMESTAMP, "2026-11-11 13:45:30.0000000", "N'2026-11-11T13:45:30'");
		function(Function.TO_TIMESTAMP, "2026-11-11 13:45:00.0000000", "N'2026-11-11T13:45'");
		function(Function.TO_TIMESTAMP, "2026-11-11 13:45:00.0000000", "N'2026-11-11 13:45'");
		function(Function.TO_TIMESTAMP, "2026-11-11 13:45:30.1230000", "N'2026-11-11 13:45:30.123'");
	}

	/**
	 * Runs the checks.
	 *
	 * @param db True to also run on the local SQL Server.
	 */
	public static void run(boolean db) {
		Check.start("TestWriter");
		written();
		if (db) {
			connect();
			expressions();
			functions();
		}
	}

	/**
	 * Entry point.
	 *
	 * @param args Pass "db" to also run on the local SQL Server.
	 */
	public static void main(String[] args) {
		boolean db = args.length > 0 && args[0].equals("db");
		run(db);
		summary();
		System.exit(0);
	}
}
