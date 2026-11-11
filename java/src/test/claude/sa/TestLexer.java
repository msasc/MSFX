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

import java.util.List;

import msfx.lib.db.query.sa.Lexer;
import msfx.lib.db.query.sa.SyntaxException;
import msfx.lib.db.query.sa.Token;

/**
 * Tests the {@link Lexer}: the tokens of valid expressions, and the position
 * of the errors in invalid ones.
 *
 * @author Miquel Sas
 */
public class TestLexer {

	/**
	 * Number of checks that failed.
	 */
	private static int failed = 0;
	/**
	 * Number of checks that passed.
	 */
	private static int passed = 0;

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
		report(expected.equals(result), expression, result, expected);
	}

	/**
	 * Splits the expression and checks that it fails at the expected position,
	 * from zero.
	 *
	 * @param expression The expression.
	 * @param position   The expected position of the error.
	 */
	private static void error(String expression, int position) {
		String result;
		boolean ok;
		try {
			new Lexer(expression).tokenize();
			result = "no error";
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

		System.out.println("== Names, keywords and punctuation");
		tokens("A.CARTICLE", "NAME:A PUNCTUATION:. NAME:CARTICLE");
		tokens("CARTICLE", "NAME:CARTICLE");
		tokens("_X1 año", "NAME:_X1 NAME:año");
		tokens("a and not b", "NAME:a KEYWORD:and KEYWORD:not NAME:b");
		tokens("A.END", "NAME:A PUNCTUATION:. NAME:END");
		tokens("A . end", "NAME:A PUNCTUATION:. NAME:end");
		tokens("LPAD(A.CODE, 5, '0')", "NAME:LPAD PUNCTUATION:( NAME:A PUNCTUATION:. NAME:CODE PUNCTUATION:, NUMBER:5 PUNCTUATION:, STRING:0 PUNCTUATION:)");
		tokens("COUNT(*)", "NAME:COUNT PUNCTUATION:( OPERATOR:* PUNCTUATION:)");
		tokens("A.*", "NAME:A PUNCTUATION:. OPERATOR:*");
		tokens("", "");
		tokens("   ", "");

		System.out.println("== Numbers, strings and parameters");
		tokens("12 12.50 0.5", "NUMBER:12 NUMBER:12.50 NUMBER:0.5");
		tokens("-12", "OPERATOR:- NUMBER:12");
		tokens("'it''s'", "STRING:it's");
		tokens("''", "STRING:");
		tokens("'a -- b; [c]'", "STRING:a -- b; [c]");
		tokens("A = ? AND B = ?", "NAME:A OPERATOR:= PARAMETER:? KEYWORD:AND NAME:B OPERATOR:= PARAMETER:?");

		System.out.println("== Operators");
		tokens("a<=b", "NAME:a OPERATOR:<= NAME:b");
		tokens("a<>b", "NAME:a OPERATOR:<> NAME:b");
		tokens("a<b", "NAME:a OPERATOR:< NAME:b");
		tokens("a>=b a>b a=b", "NAME:a OPERATOR:>= NAME:b NAME:a OPERATOR:> NAME:b NAME:a OPERATOR:= NAME:b");
		tokens("a||b", "NAME:a OPERATOR:|| NAME:b");
		tokens("a+b-c*d/e", "NAME:a OPERATOR:+ NAME:b OPERATOR:- NAME:c OPERATOR:* NAME:d OPERATOR:/ NAME:e");
		tokens("a - -b", "NAME:a OPERATOR:- OPERATOR:- NAME:b");

		System.out.println("== Errors");
		error("A = 1;", 5);
		error("A -- comment", 2);
		error("A /* comment */", 2);
		error("\"A\" = 1", 0);
		error("[A] = 1", 0);
		error("`A` = 1", 0);
		error("A != 1", 2);
		error("A | B", 2);
		error("A = 'abc", 4);
		error("12.", 3);
		error("12.a", 3);
		error("12AB", 0);
		error("1.2.3", 0);
		error("A % 2", 2);
		error("A = #", 4);

		System.out.println("== Message");
		try {
			new Lexer("A.PRICE != 0").tokenize();
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
