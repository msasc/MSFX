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

package msfx.lib.db.query.sa;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Splits a hand-written expression into its {@link Token}s.
 * <ul>
 * <li>Names start with a letter or an underscore, followed by letters, digits
 * or underscores. A name that follows a dot is always a name, even if it is
 * written like a keyword, so that alias.END is a column.</li>
 * <li>Numbers are digits with an optional decimal point followed by decimals.
 * The sign is an operator.</li>
 * <li>Strings are enclosed in single quotes, and a quote inside is written
 * twice.</li>
 * <li>Operators are read with the longest symbol that matches, so that
 * <code>&lt;=</code> is not read as <code>&lt;</code> followed by
 * <code>=</code>.</li>
 * </ul>
 * Semicolons, comments and quoted names are rejected: an expression is a single
 * piece of a statement, and names are quoted by the analyzer.
 *
 * @author Miquel Sas
 */
public class Lexer {

	/**
	 * The punctuation characters.
	 */
	private static final String PUNCTUATION = "(),.";

	/**
	 * The expression to split.
	 */
	private final String expression;
	/**
	 * The current position while splitting.
	 */
	private int position;

	/**
	 * Constructor.
	 *
	 * @param expression The expression to split.
	 * @throws NullPointerException If the expression is null.
	 */
	public Lexer(String expression) {
		this.expression = Objects.requireNonNull(expression, "Expression can not be null");
	}

	/**
	 * Returns the tokens of the expression, in order.
	 *
	 * @return The list of tokens, empty if the expression is blank.
	 * @throws SyntaxException If the expression has a character or a token that
	 *                         is not accepted.
	 */
	public List<Token> tokenize() {
		List<Token> tokens = new ArrayList<>();
		position = 0;
		skipWhitespace();
		while (position < expression.length()) {
			Token previous = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
			tokens.add(readToken(previous));
			skipWhitespace();
		}
		return tokens;
	}

	/**
	 * Skips the whitespace at the current position.
	 */
	private void skipWhitespace() {
		while (position < expression.length() && Character.isWhitespace(expression.charAt(position))) {
			position++;
		}
	}

	/**
	 * Reads the token at the current position, that is not whitespace.
	 *
	 * @param previous The previous token, or null if it is the first one.
	 * @return The token.
	 */
	private Token readToken(Token previous) {
		char c = expression.charAt(position);
		if (Character.isLetter(c) || c == '_') {
			return readWord(previous);
		}
		if (isDigit(c)) {
			return readNumber();
		}
		if (c == '\'') {
			return readString();
		}
		if (c == '?') {
			Token token = new Token(Token.Kind.PARAMETER, "?", position, null, null);
			position++;
			return token;
		}
		if (PUNCTUATION.indexOf(c) >= 0) {
			Token token = new Token(Token.Kind.PUNCTUATION, String.valueOf(c), position, null, null);
			position++;
			return token;
		}
		checkRejected();
		return readOperator();
	}

	/**
	 * Reads a name or a keyword. A word that follows a dot is always a name.
	 *
	 * @param previous The previous token, or null.
	 * @return The token.
	 */
	private Token readWord(Token previous) {
		int start = position;
		while (position < expression.length()) {
			char c = expression.charAt(position);
			if (!Character.isLetterOrDigit(c) && c != '_') {
				break;
			}
			position++;
		}
		String word = expression.substring(start, position);
		boolean afterDot = previous != null && previous.getKind() == Token.Kind.PUNCTUATION && previous.getText().equals(".");
		Keyword keyword = afterDot ? null : Keyword.of(word);
		if (keyword != null) {
			return new Token(Token.Kind.KEYWORD, word, start, keyword, null);
		}
		return new Token(Token.Kind.NAME, word, start, null, null);
	}

	/**
	 * Reads a number: digits with an optional decimal point and decimals.
	 *
	 * @return The token.
	 */
	private Token readNumber() {
		int start = position;
		skipDigits();
		if (position < expression.length() && expression.charAt(position) == '.') {
			position++;
			if (position >= expression.length() || !isDigit(expression.charAt(position))) {
				throw new SyntaxException("Decimals expected after the decimal point", expression, position);
			}
			skipDigits();
		}
		if (position < expression.length()) {
			char c = expression.charAt(position);
			if (Character.isLetter(c) || c == '_' || c == '.') {
				throw new SyntaxException("Invalid number", expression, start);
			}
		}
		return new Token(Token.Kind.NUMBER, expression.substring(start, position), start, null, null);
	}

	/**
	 * Skips the digits at the current position.
	 */
	private void skipDigits() {
		while (position < expression.length() && isDigit(expression.charAt(position))) {
			position++;
		}
	}

	/**
	 * Reads a string enclosed in single quotes, with quotes inside written twice.
	 *
	 * @return The token, with the value of the string.
	 */
	private Token readString() {
		int start = position;
		position++;
		StringBuilder value = new StringBuilder();
		while (true) {
			if (position >= expression.length()) {
				throw new SyntaxException("Unterminated string", expression, start);
			}
			char c = expression.charAt(position);
			position++;
			if (c == '\'') {
				if (position < expression.length() && expression.charAt(position) == '\'') {
					value.append('\'');
					position++;
				} else {
					break;
				}
			} else {
				value.append(c);
			}
		}
		return new Token(Token.Kind.STRING, value.toString(), start, null, null);
	}

	/**
	 * Reads an operator, with the longest symbol that matches.
	 *
	 * @return The token.
	 */
	private Token readOperator() {
		int start = position;
		if (position + 1 < expression.length()) {
			String symbol = expression.substring(position, position + 2);
			Operator operator = Operator.of(symbol);
			if (operator != null) {
				position += 2;
				return new Token(Token.Kind.OPERATOR, symbol, start, null, operator);
			}
		}
		String symbol = expression.substring(position, position + 1);
		Operator operator = Operator.of(symbol);
		if (operator != null) {
			position++;
			return new Token(Token.Kind.OPERATOR, symbol, start, null, operator);
		}
		StringBuilder msg = new StringBuilder();
		msg.append("Unexpected character '");
		msg.append(symbol);
		msg.append("'");
		throw new SyntaxException(msg.toString(), expression, start);
	}

	/**
	 * Throws the exception of the characters that are rejected on purpose, with
	 * a message that tells why.
	 */
	private void checkRejected() {
		char c = expression.charAt(position);
		char next = position + 1 < expression.length() ? expression.charAt(position + 1) : 0;
		if (c == ';') {
			throw new SyntaxException("Semicolons are not accepted, an expression is a single piece of a statement", expression, position);
		}
		if ((c == '-' && next == '-') || (c == '/' && next == '*')) {
			throw new SyntaxException("Comments are not accepted", expression, position);
		}
		if (c == '"' || c == '[' || c == '`') {
			throw new SyntaxException("Quoted names are not accepted, names are quoted by the analyzer", expression, position);
		}
		if (c == '!' && next == '=') {
			throw new SyntaxException("Operator != is not accepted, use <>", expression, position);
		}
	}

	/**
	 * Check whether a character is a decimal digit, 0 to 9.
	 *
	 * @param c The character.
	 * @return A boolean.
	 */
	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}
}
