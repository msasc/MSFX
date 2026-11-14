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

import java.util.List;
import java.util.Objects;

import msfx.lib.db.DBEngine;
import msfx.lib.md.Table;

/**
 * FROM clause of a statement: the master table and its alias, or the union of
 * the wrapped selects and their alias.
 * <p>
 * Created only by its statement.
 *
 * @author Miquel Sas
 */
public class QFrom extends QBase {

	/**
	 * The master table, or null if the clause wraps selects.
	 */
	private final Table table;
	/**
	 * The alias of the master table, or null if it has none, as usual in
	 * deletes. Always set when the clause wraps selects.
	 */
	private final String alias;
	/**
	 * The wrapped selects, or null if the clause has a master table.
	 */
	private final List<QSelect> selects;
	/**
	 * True if the wrapped selects are joined by UNION ALL, false for UNION.
	 */
	private final boolean all;

	/**
	 * Constructor of the clause with a master table.
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
		this.selects = null;
		this.all = false;
	}

	/**
	 * Constructor of the clause that wraps the union of selects, already
	 * validated by the wrapping select.
	 *
	 * @param db      The database engine.
	 * @param selects The wrapped selects.
	 * @param all     True for UNION ALL, false for UNION.
	 * @param alias   The alias.
	 * @throws NullPointerException If the engine, the selects or the alias is
	 *                              null.
	 */
	QFrom(DBEngine db, List<QSelect> selects, boolean all, String alias) {
		super(db);
		this.table = null;
		this.alias = Objects.requireNonNull(alias, "Alias can not be null");
		this.selects = List.copyOf(selects);
		this.all = all;
	}

	/**
	 * Returns the clause: FROM table [alias], or the wrapped selects joined by
	 * UNION or UNION ALL, indented within parentheses, and the alias. The alias
	 * is written without AS, which Oracle does not accept for tables.
	 *
	 * @return The FROM clause.
	 */
	String toSQL() {
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		sql.append("FROM ");
		if (selects == null) {
			sql.append(getQualifiedName(table));
		} else {
			sql.append("(\n");
			for (int i = 0; i < selects.size(); i++) {
				if (i > 0) {
					sql.append(all ? "\tUNION ALL\n" : "\tUNION\n");
				}
				for (String line : selects.get(i).toString().split("\n")) {
					sql.append("\t");
					sql.append(line);
					sql.append("\n");
				}
			}
			sql.append(")");
		}
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
