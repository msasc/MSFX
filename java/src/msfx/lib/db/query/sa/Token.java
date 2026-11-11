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

/**
 * A token of a hand-written expression, as read by the {@link Lexer}.
 *
 * @author Miquel Sas
 */
public class Token {

	/**
	 * Kinds of tokens.
	 */
	public enum Kind {
		/** A column, an alias or a function name: the parser decides which. */
		NAME,
		/** A {@link Keyword}. */
		KEYWORD,
		/** An {@link Operator}. */
		OPERATOR,
		/** A number: digits with an optional decimal point and decimals. */
		NUMBER,
		/** A string between single quotes. */
		STRING,
		/** A parameter, <code>?</code>. */
		PARAMETER,
		/** A parenthesis, a comma or a dot. */
		PUNCTUATION
	}

	/**
	 * The kind of token.
	 */
	private final Kind kind;
	/**
	 * The text of the token as written, except for strings, that hold their
	 * value: without the enclosing quotes and with doubled quotes made single.
	 */
	private final String text;
	/**
	 * The position of the first character of the token in the expression, from
	 * zero.
	 */
	private final int position;
	/**
	 * The keyword, only for keywords.
	 */
	private final Keyword keyword;
	/**
	 * The operator, only for operators.
	 */
	private final Operator operator;

	/**
	 * Constructor.
	 *
	 * @param kind     The kind of token.
	 * @param text     The text, or the value for strings.
	 * @param position The position of the token in the expression.
	 * @param keyword  The keyword, or null if it is not a keyword.
	 * @param operator The operator, or null if it is not an operator.
	 */
	Token(Kind kind, String text, int position, Keyword keyword, Operator operator) {
		this.kind = kind;
		this.text = text;
		this.position = position;
		this.keyword = keyword;
		this.operator = operator;
	}

	/**
	 * Returns the kind of token.
	 *
	 * @return The kind.
	 */
	public Kind getKind() {
		return kind;
	}

	/**
	 * Returns the text as written, or the value for strings.
	 *
	 * @return The text.
	 */
	public String getText() {
		return text;
	}

	/**
	 * Returns the position of the token in the expression.
	 *
	 * @return The position, from zero.
	 */
	public int getPosition() {
		return position;
	}

	/**
	 * Returns the keyword.
	 *
	 * @return The keyword, or null if the token is not a keyword.
	 */
	public Keyword getKeyword() {
		return keyword;
	}

	/**
	 * Returns the operator.
	 *
	 * @return The operator, or null if the token is not an operator.
	 */
	public Operator getOperator() {
		return operator;
	}

	/**
	 * Returns the kind and the text, as in NAME:ARTICLE, to read in tests and
	 * while debugging.
	 *
	 * @return A string representation.
	 */
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(kind);
		b.append(":");
		b.append(text);
		return b.toString();
	}
}
