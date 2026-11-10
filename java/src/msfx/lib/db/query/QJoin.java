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

import msfx.lib.md.Table;

/**
 * Join of a table to the statement. Adds the table to the tables of the
 * statement, and has the ON conditions, that can reference the columns of all
 * the tables added so far.
 * <p>
 * Created only by its statement.
 *
 * @author Miquel Sas
 */
public class QJoin extends QBase {

	/**
	 * Joined table and its alias.
	 */
	private final QFrom.Entry entry;
	/**
	 * ON conditions.
	 */
	private final QWhere on;

	/**
	 * Constructor. Adds the table to the tables of the statement.
	 *
	 * @param from  The tables of the statement.
	 * @param table The joined table.
	 * @param alias The alias, or null to use the name of the table.
	 * @throws NullPointerException     If the table is null.
	 * @throws IllegalArgumentException If the alias is duplicated.
	 */
	QJoin(QFrom from, Table table, String alias) {
		super(from.getDBEngine());
		this.entry = from.add(table, alias);
		this.on = new QWhere(from);
	}

	/**
	 * @return The joined table and its alias.
	 */
	public QFrom.Entry getEntry() {
		return entry;
	}

	/**
	 * @return The ON conditions.
	 */
	public QWhere getOn() {
		return on;
	}
}
