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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import msfx.lib.db.query.sa.Analysis;
import msfx.lib.db.query.sa.Clause;
import msfx.lib.db.query.sa.Node;
import msfx.lib.db.query.sa.Parser;
import msfx.lib.db.query.sa.SyntaxException;
import msfx.lib.db.query.sa.Validator;
import msfx.lib.md.Column;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import msfx.lib.md.Value;

/**
 * Tests the {@link Validator}: the columns resolved in valid expressions, and
 * the position of the errors in invalid ones, for each clause.
 *
 * @author Miquel Sas
 */
public class TestValidator {

	/**
	 * Number of checks that failed.
	 */
	private static int failed = 0;
	/**
	 * Number of checks that passed.
	 */
	private static int passed = 0;
	/**
	 * The validated select list, for HAVING.
	 */
	private static List<Analysis> selectList = List.of();

	/**
	 * Returns the scope of the tables, by alias, ignoring case as QSelect does.
	 *
	 * @param entries Pairs of alias and table.
	 * @return The scope.
	 */
	private static Map<String, Table> scope(Object... entries) {
		Map<String, Table> tables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (int i = 0; i < entries.length; i += 2) {
			tables.put((String) entries[i], (Table) entries[i + 1]);
		}
		return tables;
	}

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
		return new Validator(expression, root, clause, tables, List.of(values), selectList).validate();
	}

	/**
	 * Returns the columns resolved, as alias.COLUMN in the order of the tree.
	 *
	 * @param analysis The analysis.
	 * @return The list, or "no columns".
	 */
	private static String resolved(Analysis analysis) {
		List<String> list = new ArrayList<>();
		collect(analysis, analysis.getRoot(), list);
		return list.isEmpty() ? "no columns" : String.join(", ", list);
	}

	/**
	 * Collects the columns resolved in a node and its children.
	 *
	 * @param analysis The analysis.
	 * @param node     The node.
	 * @param list     The list to fill.
	 */
	private static void collect(Analysis analysis, Node node, List<String> list) {
		if (node.getKind() == Node.Kind.COLUMN) {
			StringBuilder b = new StringBuilder();
			b.append(analysis.getAlias(node));
			b.append(".");
			b.append(analysis.getColumn(node).getName());
			list.add(b.toString());
		}
		for (Node child : node.getChildren()) {
			collect(analysis, child, list);
		}
	}

	/**
	 * Validates an expression that must be accepted, and compares the columns
	 * resolved.
	 *
	 * @param clause     The clause.
	 * @param tables     The tables in scope.
	 * @param expression The expression.
	 * @param expected   The expected columns.
	 * @param values     The values of the placeholders.
	 */
	private static void ok(Clause clause, Map<String, Table> tables, String expression, String expected, Value... values) {
		String result;
		try {
			result = resolved(analyze(clause, tables, expression, values));
		} catch (SyntaxException exc) {
			StringBuilder b = new StringBuilder();
			b.append("error: ");
			b.append(exc.getMessage().lines().findFirst().orElse(""));
			result = b.toString();
		}
		report(expected.equals(result), clause, expression, result, expected);
	}

	/**
	 * Validates an expression that must be rejected at the expected position.
	 *
	 * @param clause     The clause.
	 * @param tables     The tables in scope.
	 * @param expression The expression.
	 * @param position   The expected position of the error, from zero.
	 * @param values     The values of the placeholders.
	 */
	private static void error(Clause clause, Map<String, Table> tables, String expression, int position, Value... values) {
		String result;
		boolean ok;
		try {
			result = resolved(analyze(clause, tables, expression, values));
			ok = false;
		} catch (SyntaxException exc) {
			StringBuilder b = new StringBuilder();
			b.append(exc.getPosition());
			b.append(": ");
			b.append(exc.getMessage().lines().findFirst().orElse(""));
			result = b.toString();
			ok = exc.getPosition() == position;
		}
		report(ok, clause, expression, result, String.valueOf(position));
	}

	/**
	 * Prints the result of a check and counts it.
	 *
	 * @param ok         Whether the check passed.
	 * @param clause     The clause.
	 * @param expression The expression.
	 * @param result     The result.
	 * @param expected   The expected result.
	 */
	private static void report(boolean ok, Clause clause, String expression, String result, String expected) {
		if (ok) {
			passed++;
		} else {
			failed++;
		}
		StringBuilder line = new StringBuilder();
		line.append(ok ? "   ok    " : "   FAIL  ");
		line.append(clause);
		line.append(" ");
		if (expression.length() > 70) {
			line.append(expression, 0, 70);
			line.append("...");
		} else {
			line.append(expression);
		}
		line.append("  ->  ");
		line.append(result);
		if (!ok) {
			line.append("\n         expected: ");
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
		sales.columns().add(new Column("QCOST", Types.DECIMAL, 16, 2));
		sales.columns().add(new Column("DSALE", Types.DATE, -1, -1));

		Table articles = new Table("ARTICLES");
		articles.columns().add(new Column("CARTICLE", Types.STRING, 40, -1));
		articles.columns().add(new Column("CBUSINESS", Types.STRING, 12, -1));
		articles.columns().add(new Column("CPTYPE", Types.STRING, 40, -1));

		Map<String, Table> one = scope("S", sales);
		Map<String, Table> two = scope("S", sales, "A", articles);

		System.out.println("== ON");
		ok(Clause.ON, two, "S.CARTICLE = A.CARTICLE", "S.CARTICLE, A.CARTICLE");
		ok(Clause.ON, two, "s.carticle = a.Carticle", "S.CARTICLE, A.CARTICLE");
		error(Clause.ON, two, "S.CARTICLE = X.CARTICLE", 13);
		error(Clause.ON, two, "S.CARTICLE = A.CNOTHING", 13);
		error(Clause.ON, two, "CARTICLE = A.CARTICLE", 0);
		error(Clause.ON, one, "S.CARTICLE = A.CARTICLE", 13);
		error(Clause.ON, two, "SUM(S.QSALES) > 0", 0);

		System.out.println("== WHERE");
		ok(Clause.WHERE, one, "CCOMPANY = ? AND NYEAR = ?", "S.CCOMPANY, S.NYEAR", new Value("LCZ"), new Value(2026));
		ok(Clause.WHERE, two, "A.CBUSINESS IN ('ACRI', 'BACE') AND S.DSALE IS NOT NULL", "A.CBUSINESS, S.DSALE");
		ok(Clause.WHERE, one, "1 = 1", "no columns");
		error(Clause.WHERE, one, "CCOMPANY = ?", 11);
		error(Clause.WHERE, one, "CCOMPANY = ?", 12, new Value("LCZ"), new Value("XXX"));
		error(Clause.WHERE, one, "COUNT(*) > 1", 0);
		error(Clause.WHERE, one, "UPPER(CNAME) = 'X'", 6);

		/* IN with 1000 and 1001 values. */
		for (int count : new int[] { 1000, 1001 }) {
			StringBuilder in = new StringBuilder();
			in.append("NYEAR IN (");
			int position = -1;
			for (int i = 1; i <= count; i++) {
				if (i > 1) {
					in.append(", ");
				}
				if (i == 1001) {
					position = in.length();
				}
				in.append(i);
			}
			in.append(")");
			if (count == 1000) {
				ok(Clause.WHERE, one, in.toString(), "S.NYEAR");
			} else {
				error(Clause.WHERE, one, in.toString(), position);
			}
		}

		/* CASE nested 10 and 11 levels. */
		for (int levels : new int[] { 10, 11 }) {
			StringBuilder nested = new StringBuilder();
			int position = -1;
			for (int i = 1; i <= levels; i++) {
				if (i == 11) {
					position = nested.length();
				}
				nested.append("CASE WHEN NYEAR = 1 THEN ");
			}
			nested.append("0");
			for (int i = 1; i <= levels; i++) {
				nested.append(" ELSE 0 END");
			}
			nested.append(" = 0");
			if (levels == 10) {
				ok(Clause.WHERE, one, nested.toString(), "S.NYEAR, S.NYEAR, S.NYEAR, S.NYEAR, S.NYEAR, S.NYEAR, S.NYEAR, S.NYEAR, S.NYEAR, S.NYEAR");
			} else {
				error(Clause.WHERE, one, nested.toString(), position);
			}
		}

		System.out.println("== SELECT");
		ok(Clause.SELECT, one, "SUM(QSALES)", "S.QSALES");
		ok(Clause.SELECT, one, "(SUM(QSALES * 2))", "S.QSALES");
		ok(Clause.SELECT, one, "CCOMPANY || '-' || CARTICLE", "S.CCOMPANY, S.CARTICLE");
		error(Clause.SELECT, one, "SUM(QSALES) / 100", 0);
		error(Clause.SELECT, one, "ROUND(SUM(QSALES), 2)", 6);
		error(Clause.SELECT, one, "SUM(QSALES) + SUM(QCOST)", 0);
		error(Clause.SELECT, one, "SUM(COUNT(*))", 4);

		System.out.println("== HAVING");
		List<Analysis> list = new ArrayList<>();
		list.add(analyze(Clause.SELECT, one, "CCOMPANY"));
		list.add(analyze(Clause.SELECT, one, "SUM(QSALES)"));
		list.add(analyze(Clause.SELECT, one, "COUNT(*)"));
		list.add(analyze(Clause.SELECT, one, "SUM(S.QCOST)"));
		list.add(analyze(Clause.SELECT, one, "COUNT(DISTINCT CARTICLE)"));
		selectList = list;
		ok(Clause.HAVING, one, "SUM(QSALES) > 0 AND COUNT(*) > 100", "S.QSALES");
		ok(Clause.HAVING, one, "sum(s.qsales) - SUM(QCOST) > 0", "S.QSALES, S.QCOST");
		ok(Clause.HAVING, one, "SUM(QSALES) * 2 > SUM((QCOST))", "S.QSALES, S.QCOST");
		ok(Clause.HAVING, one, "COUNT(DISTINCT S.CARTICLE) > 5", "S.CARTICLE");
		ok(Clause.HAVING, one, "SUM(QSALES) BETWEEN 1 AND 10", "S.QSALES");
		error(Clause.HAVING, one, "CCOMPANY = 'LCZ' AND SUM(QSALES) > 0", 0);
		error(Clause.HAVING, one, "SUM(QCOST * 2) > 0", 0);
		error(Clause.HAVING, one, "COUNT(CARTICLE) > 5", 0);
		error(Clause.HAVING, one, "ROUND(SUM(QSALES), 2) > 0", 6);
		error(Clause.HAVING, one, "AVG(QSALES) > 0", 0);

		System.out.println("== Message");
		try {
			analyze(Clause.HAVING, one, "SUM(QSALES) > 0 AND CCOMPANY = 'LCZ'");
		} catch (SyntaxException exc) {
			System.out.println(exc.getMessage());
		}

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
