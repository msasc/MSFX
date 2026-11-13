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

import java.util.Objects;

import msfx.lib.db.DBEngine;
import msfx.lib.md.Table;

/**
 * FROM clause of a statement: the master table and its alias.
 * <p>
 * Created only by its statement.
 *
 * @author Miquel Sas
 */
public class QFrom extends QBase {

	/**
	 * The master table.
	 */
	private final Table table;
	/**
	 * The alias of the master table, or null if it has none, as usual in
	 * deletes.
	 */
	private final String alias;

	/**
	 * Constructor.
	 *
	 * @param db    The database engine.
	 * @param table The master table.
	 * @param alias The alias, or null for none.
	 * @throws NullPointerException     If the engine or the table is null.
	 * @throws IllegalArgumentException If the alias is blank.
	 */
	QFrom(DBEngine db, Table table, String alias) {
		super(db);
		this.table = Objects.requireNonNull(table, "Table can not be null");
		if (alias != null && alias.isBlank()) {
			throw new IllegalArgumentException("Alias can not be blank");
		}
		this.alias = alias;
	}

	/**
	 * Returns the clause: FROM table [alias]. The alias is written only when set,
	 * without AS, which Oracle does not accept for tables.
	 *
	 * @return The FROM clause.
	 */
	String toSQL() {
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		sql.append("FROM ");
		sql.append(getQualifiedName(table));
		if (alias != null) {
			sql.append(" ");
			sql.append(db.quote(alias));
		}
		return sql.toString();
	}

	/**
	 * Returns the clause, as {@link #toSQL()} writes it.
	 *
	 * @return The FROM clause.
	 */
	@Override
	public String toString() {
		return toSQL();
	}
}
