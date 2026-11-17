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

import java.util.List;

import msfx.lib.db.query.sa.Lexer;
import msfx.lib.db.query.sa.Node;
import msfx.lib.db.query.sa.Parser;
import msfx.lib.db.query.sa.SyntaxException;
import msfx.lib.db.query.sa.Token;
import test.claude.Check;

/**
 * Tests the {@link Lexer} and the {@link Parser}: the tokens and the trees of
 * valid expressions, the trees written fully parenthesized by
 * {@link Node#toString()}, and the position of the errors in invalid ones.
 *
 * @author Miquel Sas
 */
public class TestParser {

	/**
	 * Splits the expression and compares its tokens, written with
	 * {@link Token#toString()} and separated by spaces, with the expected ones.
	 *
	 * @param expression The expression.
	 * @param expected   The expected tokens.
	 */
	private static void tokens(String expression, String expected) {
		String result;
		try {
			List<Token> tokens = new Lexer(expression).tokenize();
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < tokens.size(); i++) {
				if (i > 0) {
					b.append(" ");
				}
				b.append(tokens.get(i));
			}
			result = b.toString();
		} catch (SyntaxException exc) {
			StringBuilder b = new StringBuilder();
			b.append("error: ");
			b.append(exc.getMessage());
			result = b.toString();
		}
		equal(expression, result, expected);
	}

	/**
	 * Splits the expression and checks that it fails at the expected position,
	 * from zero.
	 *
	 * @param expression The expression.
	 * @param position   The expected position of the error.
	 */
	private static void lexerError(String expression, int position) {
		String result;
		boolean ok;
		try {
			new Lexer(expression).tokenize();
			result = "no error";
			ok = false;
		} catch (SyntaxException exc) {
			result = describe(exc);
			ok = exc.getPosition() == position;
		}
		report(ok, expression, result, String.valueOf(position));
	}

	/**
	 * Parses the expression as a condition or as a value.
	 *
	 * @param expression The expression.
	 * @param condition  Whether to parse it as a condition.
	 * @return The root node.
	 */
	private static Node parse(String expression, boolean condition) {
		Parser parser = new Parser(expression);
		Node root = condition ? parser.parseCondition() : parser.parseExpression();
		return root;
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
		equal(expression, result, expected);
	}

	/**
	 * Parses the expression and checks that it fails at the expected position.
	 *
	 * @param expression The expression.
	 * @param condition  Whether to parse it as a condition.
	 * @param position   The expected position of the error, from zero.
	 */
	private static void parserError(String expression, boolean condition, int position) {
		String result;
		boolean ok;
		try {
			result = parse(expression, condition).toString();
			ok = false;
		} catch (SyntaxException exc) {
			result = describe(exc);
			ok = exc.getPosition() == position;
		}
		report(ok, expression, result, String.valueOf(position));
	}

	/**
	 * Describes a syntax error: its position and the first line of its message.
	 *
	 * @param exc The error.
	 * @return The description.
	 */
	private static String describe(SyntaxException exc) {
		String first = exc.getMessage().lines().findFirst().orElse("");
		StringBuilder b = new StringBuilder();
		b.append(exc.getPosition());
		b.append(": ");
		b.append(first);
		return b.toString();
	}

	/**
	 * Checks the lexer.
	 */
	private static void lexer() {
		section("Lexer: names, keywords and punctuation");
		tokens("A.CARTICLE", "NAME:A PUNCTUATION:. NAME:CARTICLE");
		tokens("_X1 año", "NAME:_X1 NAME:año");
		tokens("a and not b", "NAME:a KEYWORD:and KEYWORD:not NAME:b");
		tokens("A.END", "NAME:A PUNCTUATION:. NAME:END");
		tokens("A . end", "NAME:A PUNCTUATION:. NAME:end");
		tokens("LPAD(A.CODE, 5, '0')", "NAME:LPAD PUNCTUATION:( NAME:A PUNCTUATION:. NAME:CODE PUNCTUATION:, NUMBER:5 PUNCTUATION:, STRING:0 PUNCTUATION:)");
		tokens("COUNT(*)", "NAME:COUNT PUNCTUATION:( OPERATOR:* PUNCTUATION:)");
		tokens("A.*", "NAME:A PUNCTUATION:. OPERATOR:*");
		tokens("   ", "");

		section("Lexer: numbers, strings, parameters and operators");
		tokens("12 12.50 0.5", "NUMBER:12 NUMBER:12.50 NUMBER:0.5");
		tokens("-12", "OPERATOR:- NUMBER:12");
		tokens("'it''s'", "STRING:it's");
		tokens("''", "STRING:");
		tokens("'a -- b; [c]'", "STRING:a -- b; [c]");
		tokens("A = ? AND B = ?", "NAME:A OPERATOR:= PARAMETER:? KEYWORD:AND NAME:B OPERATOR:= PARAMETER:?");
		tokens("a<=b a<>b a<b", "NAME:a OPERATOR:<= NAME:b NAME:a OPERATOR:<> NAME:b NAME:a OPERATOR:< NAME:b");
		tokens("a>=b a>b a=b", "NAME:a OPERATOR:>= NAME:b NAME:a OPERATOR:> NAME:b NAME:a OPERATOR:= NAME:b");
		tokens("a||b", "NAME:a OPERATOR:|| NAME:b");
		tokens("a+b-c*d/e", "NAME:a OPERATOR:+ NAME:b OPERATOR:- NAME:c OPERATOR:* NAME:d OPERATOR:/ NAME:e");
		tokens("a - -b", "NAME:a OPERATOR:- OPERATOR:- NAME:b");

		section("Lexer: errors");
		lexerError("A = 1;", 5);
		lexerError("A -- comment", 2);
		lexerError("A /* comment */", 2);
		lexerError("\"A\" = 1", 0);
		lexerError("[A] = 1", 0);
		lexerError("`A` = 1", 0);
		lexerError("A != 1", 2);
		lexerError("A | B", 2);
		lexerError("A = 'abc", 4);
		lexerError("12.", 3);
		lexerError("12.a", 3);
		lexerError("12AB", 0);
		lexerError("1.2.3", 0);
		lexerError("A % 2", 2);
		lexerError("A = #", 4);
	}

	/**
	 * Checks the parser.
	 */
	private static void parser() {
		section("Parser: logical precedence");
		tree("S.CARTICLE = A.CARTICLE", true, "(S.CARTICLE = A.CARTICLE)");
		tree("A = 1 AND B = 2 OR C = 3", true, "(((A = 1) AND (B = 2)) OR (C = 3))");
		tree("A = 1 OR B = 2 AND C = 3", true, "((A = 1) OR ((B = 2) AND (C = 3)))");
		tree("A = 1 AND B = 2 AND C = 3", true, "((A = 1) AND (B = 2) AND (C = 3))");
		tree("NOT A = 1 AND B = 2", true, "((NOT (A = 1)) AND (B = 2))");
		tree("NOT NOT A = 1", true, "(NOT (NOT (A = 1)))");
		tree("(A = 1 OR B = 2) AND C = 3", true, "((((A = 1) OR (B = 2))) AND (C = 3))");

		section("Parser: arithmetic precedence");
		tree("A + B * C > 10", true, "((A + (B * C)) > 10)");
		tree("(A + B) * C > 10", true, "((((A + B)) * C) > 10)");
		tree("A - B - C = 0", true, "(((A - B) - C) = 0)");
		tree("-A * 2 < -1", true, "(((-A) * 2) < (-1))");
		tree("A || '-' || B = 'X'", true, "(((A || '-') || B) = 'X')");
		tree("(A + 1)", false, "((A + 1))");
		tree("12.50", false, "12.50");

		section("Parser: predicates");
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

		section("Parser: functions and CASE");
		tree("SUM(QSALES) > 0 AND COUNT(*) > 100", true, "((SUM(QSALES) > 0) AND (COUNT(*) > 100))");
		tree("NULLIF(TRIM(IPRODAS), '') IS NULL", true, "(NULLIF(TRIM(IPRODAS), '') IS NULL)");
		tree("CURRENT_DATE() > S.DSALE", true, "(CURRENT_DATE() > S.DSALE)");
		tree("SUM(A.QTY * A.PRICE)", false, "SUM((A.QTY * A.PRICE))");
		tree("count(distinct S.CARTICLE)", false, "COUNT(DISTINCT S.CARTICLE)");
		tree("COALESCE(A, B, C, 0)", false, "COALESCE(A, B, C, 0)");
		tree("CASE WHEN A = 1 THEN 'X' WHEN A = 2 THEN 'Y' ELSE '' END", false,
				"CASE WHEN (A = 1) THEN 'X' WHEN (A = 2) THEN 'Y' ELSE '' END");
		tree("CASE WHEN A = 1 THEN CASE WHEN B = 1 THEN 1 ELSE 2 END ELSE 0 END", false,
				"CASE WHEN (A = 1) THEN CASE WHEN (B = 1) THEN 1 ELSE 2 END ELSE 0 END");
		tree("CASE WHEN A = 1 AND B IS NULL THEN 'X' ELSE '' END = 'X'", true,
				"(CASE WHEN ((A = 1) AND (B IS NULL)) THEN 'X' ELSE '' END = 'X')");

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
		report(costOk, "Real expression: cost criteria CASE", costTree, "GROUP of CASE with 14 WHEN branches and ELSE");

		section("Parser: errors in conditions");
		parserError("", true, 0);
		parserError("A", true, 0);
		parserError("NOT A", true, 4);
		parserError("A = 1 AND B", true, 10);
		parserError("A = 1 AND", true, 9);
		parserError("A = B = C", true, 6);
		parserError("A = 1 B = 2", true, 6);
		parserError("A = NULL", true, 4);
		parserError("A IN ()", true, 6);
		parserError("A IN (1, 2", true, 10);
		parserError("A NOT = 1", true, 6);
		parserError("A BETWEEN 1 OR 2", true, 12);
		parserError("(A = 1", true, 6);
		parserError("(A = 1) + 2 > 0", true, 0);
		parserError("A > (B = 1)", true, 4);
		parserError("A.* = 1", true, 2);
		parserError("FOO(A) = 1", true, 0);
		parserError("UPPER(DISTINCT A) = 'X'", true, 6);
		parserError("SUM(*) > 0", true, 4);
		parserError("COUNT(DISTINCT *) > 0", true, 15);
		parserError("LEFT(A) = 'X'", true, 6);
		parserError("LEFT(A, 1, 2) = 'X'", true, 11);
		parserError("CURRENT_DATE(A) > B", true, 13);

		section("Parser: errors in values");
		parserError("A = 1", false, 0);
		parserError("CASE A WHEN 1 THEN 2 ELSE 0 END", false, 5);
		parserError("CASE WHEN A = 1 THEN 2 END", false, 23);
		parserError("CASE WHEN A THEN 1 ELSE 0 END", false, 10);
		parserError("CASE WHEN A = 1 THEN 2 ELSE 0", false, 29);
		parserError("COALESCE(A > 1, 0)", false, 9);
		parserError("SUM(A", false, 5);
		parserError("COUNT(DISTINCT)", false, 14);
	}

	/**
	 * Runs the checks.
	 */
	public static void run() {
		Check.start("TestParser");
		lexer();
		parser();
	}

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {
		run();
		summary();
	}
}
