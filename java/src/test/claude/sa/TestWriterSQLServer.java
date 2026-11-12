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

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
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
 * Runs expressions written by the {@link Writer} on the local SQL Server.
 * <p>
 * The table SALES, alias S, is a derived table of one row made of literals,
 * so no table is created, read or changed. Conditions are selected as CASE WHEN
 * condition THEN 1 ELSE 0 END, and values as they are.
 *
 * @author Miquel Sas
 */
public class TestWriterSQLServer {

	/**
	 * The database engine.
	 */
	private static final DBEngineSQLServer db = new DBEngineSQLServer();
	/**
	 * The row of SALES, as a derived table with the alias S.
	 */
	private static final String FROM = getFrom();
	/**
	 * The tables in scope.
	 */
	private static final Map<String, Table> tables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	/**
	 * Number of checks that failed.
	 */
	private static int failed = 0;
	/**
	 * Number of checks that passed.
	 */
	private static int passed = 0;

	/**
	 * Returns the FROM clause with the row of SALES.
	 *
	 * @return The FROM clause, starting with a space.
	 */
	private static String getFrom() {
		StringBuilder from = new StringBuilder();
		from.append(" FROM (SELECT N'LCZ' AS [CCOMPANY], N'A325-X' AS [CARTICLE], 2026 AS [NYEAR], ");
		from.append("CAST(1500.50 AS DECIMAL(16, 2)) AS [QSALES], CAST(900.25 AS DECIMAL(16, 2)) AS [QCOST], ");
		from.append("CAST('2026-11-11' AS DATE) AS [DSALE], N'' AS [CNOTE]) AS [S]");
		return from.toString();
	}

	/**
	 * Checks a condition: 1 if it is true for the row, 0 otherwise.
	 *
	 * @param expression The condition.
	 * @param expected   The expected result, 1 or 0.
	 * @param values     The values of the placeholders.
	 */
	private static void condition(String expression, String expected, Value... values) {
		run(Clause.WHERE, expression, expected, values);
	}

	/**
	 * Checks a value of the select list.
	 *
	 * @param expression The expression.
	 * @param expected   The expected result, read as a string.
	 * @param values     The values of the placeholders.
	 */
	private static void value(String expression, String expected, Value... values) {
		run(Clause.SELECT, expression, expected, values);
	}

	/**
	 * Analyzes and writes the expression, runs it and compares the result.
	 *
	 * @param clause     The clause.
	 * @param expression The expression.
	 * @param expected   The expected result.
	 * @param values     The values of the placeholders.
	 */
	private static void run(Clause clause, String expression, String expected, Value... values) {
		String written;
		String result;
		try {
			Parser parser = new Parser(expression);
			Node root = (clause == Clause.SELECT ? parser.parseExpression() : parser.parseCondition());
			Analysis analysis = new Validator(expression, root, clause, tables, List.of(values), List.of()).validate();
			written = new Writer(db, analysis).write();
		} catch (SyntaxException exc) {
			written = "";
			result = exc.getMessage();
			report(false, expression, written, result, expected);
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
		sql.append(FROM);
		try (DBConnection cn = db.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql.toString())) {
			rs.next();
			result = rs.getString(1);
		} catch (SQLException exc) {
			StringBuilder error = new StringBuilder();
			error.append("error: ");
			error.append(exc.getMessage());
			result = error.toString();
		}
		report(expected.equals(result), expression, written, result, expected);
	}

	/**
	 * Prints the result of a check and counts it.
	 *
	 * @param ok         Whether the check passed.
	 * @param expression The expression.
	 * @param written    The SQL written.
	 * @param result     The result.
	 * @param expected   The expected result.
	 */
	private static void report(boolean ok, String expression, String written, String result, String expected) {
		if (ok) {
			passed++;
		} else {
			failed++;
		}
		StringBuilder line = new StringBuilder();
		line.append(ok ? "   ok    " : "   FAIL  ");
		line.append(expression);
		line.append("  ->  [");
		line.append(result);
		line.append("]");
		if (!ok) {
			line.append(" expected [");
			line.append(expected);
			line.append("]");
		}
		line.append("\n            ");
		line.append(written);
		System.out.println(line);
	}

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {
		db.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");

		Table sales = new Table("SALES");
		sales.columns().add(new Column("CCOMPANY", Types.STRING, 10, -1));
		sales.columns().add(new Column("CARTICLE", Types.STRING, 40, -1));
		sales.columns().add(new Column("NYEAR", Types.INTEGER, -1, -1));
		sales.columns().add(new Column("QSALES", Types.DECIMAL, 16, 2));
		sales.columns().add(new Column("QCOST", Types.DECIMAL, 16, 2));
		sales.columns().add(new Column("DSALE", Types.DATE, -1, -1));
		sales.columns().add(new Column("CNOTE", Types.STRING, 40, -1));
		tables.put("S", sales);

		System.out.println("== Conditions");
		condition("CCOMPANY = ? AND NYEAR = ?", "1", new Value("LCZ"), new Value(2026));
		condition("s.ccompany = 'LCZ'", "1");
		condition("CARTICLE LIKE 'A325%'", "1");
		condition("CNOTE IS NULL", "1");
		condition("CNOTE IS NOT NULL", "0");
		condition("CCOMPANY IS NOT NULL", "1");
		condition("DSALE = ?", "1", new Value(LocalDate.of(2026, 11, 11)));
		condition("DSALE BETWEEN TO_DATE('2026-01-01') AND TO_DATE('2026-12-31')", "1");
		condition("QSALES - QCOST > 600", "1");
		condition("NOT (NYEAR IN (2024, 2025))", "1");
		condition("LENGTH(CCOMPANY || '-' || CARTICLE) = 10", "1");
		condition("NYEAR - -1 = 2027", "1");
		condition("-? = 5", "1", new Value(-5));
		condition("? = 'it''s'", "1", new Value("it's"));
		condition("CASE WHEN QSALES > 1000 THEN 'HIGH' ELSE 'LOW' END = 'HIGH'", "1");

		System.out.println("== Values");
		value("CCOMPANY || '/' || NYEAR", "LCZ/2026");
		value("CCOMPANY || CNOTE || '.'", "LCZ.");
		value("QSALES * 2", "3001.00");
		value("SUM(QSALES)", "1500.50");
		value("COUNT(DISTINCT CARTICLE)", "1");
		value("LPAD(CCOMPANY, 6, '0')", "000LCZ");
		value("CASE WHEN CNOTE IS NULL THEN 'EMPTY' ELSE CNOTE END", "EMPTY");

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
