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
 * Temporary store of code that may be reused later, kept apart so that it does
 * not disturb the reading of the classes being developed. Not part of the API.
 *
 * @author Miquel Sas
 */
final class ZBack {

	/*
	 * Accepted operators, from QBase, to be used later for validations.
	 */
	protected final static String[] CONDITIONAL_OPERATORS = new String[] {
			"WHERE", "WHERE NOT", "AND", "AND NOT", "OR", "OR NOT"
	};
	protected final static String[] COMPARISON_OPERATORS = new String[] {
			"=", "<>", ">", ">=", "<", "<="
	};
	protected final static String[] IN_OPERATORS = new String[] {
			"IN", "NOT IN"
	};
	protected final static String[] EXISTS_OPERATORS = new String[] {
			"EXISTS", "NOT EXISTS"
	};

	/**
	 * Private constructor, this class is only a store.
	 */
	private ZBack() {
	}
}
