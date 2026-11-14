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
 * Logical operators that link a condition or a group of conditions to the
 * previous ones, and NOT to negate the first condition of a group. Meant to be
 * used through a static import, to write AND_NOT rather than
 * Logical.AND_NOT.
 *
 * @author Miquel Sas
 */
public enum Logical {
	/**
	 * AND.
	 */
	AND("AND"),
	/**
	 * AND NOT.
	 */
	AND_NOT("AND NOT"),
	/**
	 * OR.
	 */
	OR("OR"),
	/**
	 * OR NOT.
	 */
	OR_NOT("OR NOT"),
	/**
	 * NOT, only for the first condition of a group, that has no previous
	 * condition to link to.
	 */
	NOT("NOT");

	/**
	 * The operator as written in SQL.
	 */
	private final String op;

	/**
	 * Constructor.
	 *
	 * @param op The operator as written in SQL.
	 */
	private Logical(String op) {
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
