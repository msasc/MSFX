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

import msfx.lib.db.query.sa.Node;
import msfx.lib.db.query.sa.Parser;
import msfx.lib.db.query.sa.SyntaxException;

/**
 * Tests the {@link Parser}: the trees of valid expressions, written fully
 * parenthesized by {@link Node#toString()}, and the position of the errors in
 * invalid ones.
 *
 * @author Miquel Sas
 */
public class TestParser {

	/**
	 * Number of checks that failed.
	 */
	private static int failed = 0;
	/**
	 * Number of checks that passed.
	 */
	private static int passed = 0;

	/**
	 * Parses the expression as a condition or as a value.
	 *
	 * @param expression The expression.
	 * @param condition  Whether to parse it as a condition.
	 * @return The root node.
	 */
	private static Node parse(String expression, boolean condition) {
		Parser parser = new Parser(expression);
		return condition ? parser.parseCondition() : parser.parseExpression();
	}

	/**
	 * Parses the expression and compares its tree with the expected one.
	 *
	 * @param expression The expression.
	 * @param condition  Whether to parse it as a condition.
	 * @param expected   The expected tree.
	 */
	private static void tree(String expression, boolean condition, String expected) {
		String result;
		try {
			result = parse(expression, condition).toString();
		} catch (SyntaxException exc) {
			StringBuilder b = new StringBuilder();
			b.append("error: ");
			b.append(exc.getMessage());
			result = b.toString();
		}
		report(expected.equals(result), expression, result, expected);
	}

	/**
	 * Parses the expression and checks that it fails at the expected position.
	 *
	 * @param expression The expression.
	 * @param condition  Whether to parse it as a condition.
	 * @param position   The expected position of the error, from zero.
	 */
	private static void error(String expression, boolean condition, int position) {
		String result;
		boolean ok;
		try {
			result = parse(expression, condition).toString();
			ok = false;
		} catch (SyntaxException exc) {
			StringBuilder b = new StringBuilder();
			b.append(exc.getPosition());
			b.append(": ");
			b.append(exc.getMessage().lines().findFirst().orElse(""));
			result = b.toString();
			ok = exc.getPosition() == position;
		}
		report(ok, expression, result, String.valueOf(position));
	}

	/**
	 * Prints the result of a check and counts it.
	 *
	 * @param ok         Whether the check passed.
	 * @param expression The expression.
	 * @param result     The result.
	 * @param expected   The expected result.
	 */
	private static void report(boolean ok, String expression, String result, String expected) {
		if (ok) {
			passed++;
		} else {
			failed++;
		}
		StringBuilder line = new StringBuilder();
		line.append(ok ? "   ok    " : "   FAIL  ");
		line.append(expression);
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

		System.out.println("== Logical precedence");
		tree("S.CARTICLE = A.CARTICLE", true, "(S.CARTICLE = A.CARTICLE)");
		tree("A = 1 AND B = 2 OR C = 3", true, "(((A = 1) AND (B = 2)) OR (C = 3))");
		tree("A = 1 OR B = 2 AND C = 3", true, "((A = 1) OR ((B = 2) AND (C = 3)))");
		tree("A = 1 AND B = 2 AND C = 3", true, "((A = 1) AND (B = 2) AND (C = 3))");
		tree("NOT A = 1 AND B = 2", true, "((NOT (A = 1)) AND (B = 2))");
		tree("NOT NOT A = 1", true, "(NOT (NOT (A = 1)))");
		tree("(A = 1 OR B = 2) AND C = 3", true, "((((A = 1) OR (B = 2))) AND (C = 3))");

		System.out.println("== Arithmetic precedence");
		tree("A + B * C > 10", true, "((A + (B * C)) > 10)");
		tree("(A + B) * C > 10", true, "((((A + B)) * C) > 10)");
		tree("A - B - C = 0", true, "(((A - B) - C) = 0)");
		tree("-A * 2 < -1", true, "(((-A) * 2) < (-1))");
		tree("A || '-' || B = 'X'", true, "(((A || '-') || B) = 'X')");
		tree("(A + 1)", false, "((A + 1))");
		tree("12.50", false, "12.50");

		System.out.println("== Predicates");
		tree("A IS NULL", true, "(A IS NULL)");
		tree("A IS NOT NULL", true, "(A IS NOT NULL)");
		tree("S.CBUSINESS IN ('A', 'B', ?)", true, "(S.CBUSINESS IN ('A', 'B', ?0))");
		tree("A NOT IN (1, 2)", true, "(A NOT IN (1, 2))");
		tree("NOT A IN (1)", true, "(NOT (A IN (1)))");
		tree("A BETWEEN 1 AND 2 AND B = 3", true, "((A BETWEEN 1 AND 2) AND (B = 3))");
		tree("A NOT BETWEEN ? AND ?", true, "(A NOT BETWEEN ?0 AND ?1)");
		tree("CARTICLE LIKE 'A325%'", true, "(CARTICLE LIKE 'A325%')");
		tree("A NOT LIKE 'x' || B", true, "(A NOT LIKE ('x' || B))");
		tree("A = ? AND B IN (?, ?)", true, "((A = ?0) AND (B IN (?1, ?2)))");
		tree("A = 'it''s'", true, "(A = 'it''s')");
		tree("s.end = 1", true, "(s.end = 1)");

		System.out.println("== Functions");
		tree("SUM(QSALES) > 0 AND COUNT(*) > 100", true, "((SUM(QSALES) > 0) AND (COUNT(*) > 100))");
		tree("NULLIF(TRIM(IPRODAS), '') IS NULL", true, "(NULLIF(TRIM(IPRODAS), '') IS NULL)");
		tree("CURRENT_DATE() > S.DSALE", true, "(CURRENT_DATE() > S.DSALE)");
		tree("SUM(A.QTY * A.PRICE)", false, "SUM((A.QTY * A.PRICE))");
		tree("count(distinct S.CARTICLE)", false, "COUNT(DISTINCT S.CARTICLE)");
		tree("COALESCE(A, B, C, 0)", false, "COALESCE(A, B, C, 0)");
		tree("LPAD(A.CODE, 5, '0')", false, "LPAD(A.CODE, 5, '0')");

		System.out.println("== CASE");
		tree("CASE WHEN A = 1 THEN 'X' WHEN A = 2 THEN 'Y' ELSE '' END", false,
				"CASE WHEN (A = 1) THEN 'X' WHEN (A = 2) THEN 'Y' ELSE '' END");
		tree("CASE WHEN A = 1 THEN CASE WHEN B = 1 THEN 1 ELSE 2 END ELSE 0 END", false,
				"CASE WHEN (A = 1) THEN CASE WHEN (B = 1) THEN 1 ELSE 2 END ELSE 0 END");
		tree("CASE WHEN A = 1 AND B IS NULL THEN 'X' ELSE '' END = 'X'", true,
				"(CASE WHEN ((A = 1) AND (B IS NULL)) THEN 'X' ELSE '' END = 'X')");

		System.out.println("== Real expression: cost criteria");
		StringBuilder cost = new StringBuilder();
		cost.append("(CASE WHEN ICOSTORIG = 'ASSEM' AND ICOSTLIST = 'Y' THEN 'Cost-List' ");
		cost.append("WHEN ICOSTORIG = 'ASSEM' AND IASSEMEXC = 'Y' THEN 'Assem-Exc' ");
		cost.append("WHEN ICOSTORIG = 'ASSEM' AND IPRODAS = 'Y' THEN 'Prod-Cost' ");
		cost.append("WHEN ICOSTORIG = 'ASSEM' AND NULLIF(TRIM(IPRODAS),'') IS NULL ");
		cost.append("AND NULLIF(TRIM(IASSEMEXC),'') IS NULL AND NULLIF(TRIM(ICOSTLIST),'') IS NULL ");
		cost.append("AND ICOSTAVGF = 'Y' THEN 'Cost-Avg' ");
		String[] origins = { "PROD", "PURCHIC", "PURCH3RD", "MIX", "NO ORIG" };
		for (String origin : origins) {
			cost.append("WHEN ICOSTORIG = '");
			cost.append(origin);
			cost.append("' AND ICOSTLIST = 'Y' THEN 'Cost-List' ");
			cost.append("WHEN ICOSTORIG = '");
			cost.append(origin);
			cost.append("' AND NULLIF(TRIM(ICOSTLIST),'') IS NULL AND ICOSTAVGF = 'Y' THEN 'Cost-Avg' ");
		}
		cost.append("ELSE '' END)");
		String costTree;
		boolean costOk;
		try {
			Node node = new Parser(cost.toString()).parseExpression();
			costOk = node.getKind() == Node.Kind.GROUP
					&& node.getChildren().get(0).getKind() == Node.Kind.CASE
					&& node.getChildren().get(0).getChildren().size() == 29;
			costTree = "GROUP of CASE with 14 WHEN branches and ELSE";
		} catch (SyntaxException exc) {
			costOk = false;
			costTree = exc.getMessage();
		}
		report(costOk, "cost criteria CASE", costTree, "GROUP of CASE with 14 WHEN branches and ELSE");

		System.out.println("== Errors in conditions");
		error("", true, 0);
		error("A", true, 0);
		error("NOT A", true, 4);
		error("A = 1 AND B", true, 10);
		error("A = 1 AND", true, 9);
		error("A = B = C", true, 6);
		error("A = 1 B = 2", true, 6);
		error("A = NULL", true, 4);
		error("A IN ()", true, 6);
		error("A IN (1, 2", true, 10);
		error("A NOT = 1", true, 6);
		error("A BETWEEN 1 OR 2", true, 12);
		error("(A = 1", true, 6);
		error("(A = 1) + 2 > 0", true, 0);
		error("A > (B = 1)", true, 4);
		error("A.* = 1", true, 2);
		error("FOO(A) = 1", true, 0);
		error("UPPER(DISTINCT A) = 'X'", true, 6);
		error("SUM(*) > 0", true, 4);
		error("COUNT(DISTINCT *) > 0", true, 15);
		error("LEFT(A) = 'X'", true, 6);
		error("LEFT(A, 1, 2) = 'X'", true, 11);
		error("CURRENT_DATE(A) > B", true, 13);

		System.out.println("== Errors in values");
		error("A = 1", false, 0);
		error("CASE A WHEN 1 THEN 2 ELSE 0 END", false, 5);
		error("CASE WHEN A = 1 THEN 2 END", false, 23);
		error("CASE WHEN A THEN 1 ELSE 0 END", false, 10);
		error("CASE WHEN A = 1 THEN 2 ELSE 0", false, 29);
		error("COALESCE(A > 1, 0)", false, 9);
		error("SUM(A", false, 5);
		error("COUNT(DISTINCT)", false, 14);

		System.out.println("== Message");
		try {
			new Parser("S.QSALES > 0 AND S.CCOMPANY = NULL").parseCondition();
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
