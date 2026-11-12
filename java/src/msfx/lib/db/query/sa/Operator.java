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

import java.util.HashMap;
import java.util.Map;

/**
 * Operators written with symbols in hand-written expressions. Operators written
 * with words, like AND or LIKE, are {@link Keyword}s.
 * <p>
 * The symbols are those of our own dialect, the same for every database. The
 * writer translates those that differ, like the concatenation, written
 * <code>||</code> and translated to <code>+</code> in SQL Server.
 *
 * @author Miquel Sas
 */
public enum Operator {

	/** Equal. */
	EQUAL("="),
	/** Not equal. The form <code>!=</code> is not accepted. */
	NOT_EQUAL("<>"),
	/** Greater than. */
	GREATER(">"),
	/** Greater than or equal. */
	GREATER_EQUAL(">="),
	/** Less than. */
	LESS("<"),
	/** Less than or equal. */
	LESS_EQUAL("<="),
	/** Addition, or sign. */
	PLUS("+"),
	/** Subtraction, or sign. */
	MINUS("-"),
	/** Multiplication. */
	MULTIPLY("*"),
	/** Division. */
	DIVIDE("/"),
	/** Concatenation of strings. */
	CONCAT("||");

	/**
	 * Map of operators by symbol.
	 */
	private static final Map<String, Operator> BY_SYMBOL = new HashMap<>();
	static {
		for (Operator operator : values()) {
			BY_SYMBOL.put(operator.symbol, operator);
		}
	}

	/**
	 * Returns the operator written with the symbol.
	 *
	 * @param symbol The symbol.
	 * @return The operator, or null if the symbol is not an operator.
	 */
	public static Operator of(String symbol) {
		return BY_SYMBOL.get(symbol);
	}

	/**
	 * The symbol.
	 */
	private final String symbol;

	/**
	 * Constructor.
	 *
	 * @param symbol The symbol.
	 */
	private Operator(String symbol) {
		this.symbol = symbol;
	}

	/**
	 * Returns the symbol.
	 *
	 * @return The symbol.
	 */
	public String getSymbol() {
		return symbol;
	}

	/**
	 * Returns whether the operator is a comparison, that gives a condition.
	 *
	 * @return A boolean.
	 */
	public boolean isComparison() {
		boolean comparison = switch (this) {
			case EQUAL:
			case NOT_EQUAL:
			case GREATER:
			case GREATER_EQUAL:
			case LESS:
			case LESS_EQUAL:
				yield true;
			case PLUS:
			case MINUS:
			case MULTIPLY:
			case DIVIDE:
			case CONCAT:
				yield false;
		};
		return comparison;
	}
}
