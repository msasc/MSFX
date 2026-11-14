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
package msfx.lib.db.query;

/**
 * Comparison operators of the conditions composed by the query classes. Meant
 * to be used through a static import, to write GT rather than Comparison.GT.
 *
 * @author Miquel Sas
 */
public enum Comparison {
	/**
	 * Greater than.
	 */
	GT(">"),
	/**
	 * Greater than or equal.
	 */
	GE(">="),
	/**
	 * Less than.
	 */
	LT("<"),
	/**
	 * Less than or equal.
	 */
	LE("<="),
	/**
	 * Equal.
	 */
	EQ("="),
	/**
	 * Not equal.
	 */
	NE("<>");

	/**
	 * The operator as written in SQL.
	 */
	private final String op;

	/**
	 * Constructor.
	 *
	 * @param op The operator as written in SQL.
	 */
	private Comparison(String op) {
		this.op = op;
	}

	/**
	 * Returns the operator as written in SQL.
	 *
	 * @return The operator.
	 */
	public String getOperator() {
		return op;
	}
}
