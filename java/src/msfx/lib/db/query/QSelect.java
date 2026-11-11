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

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import msfx.lib.db.DBEngine;
import msfx.lib.md.Table;

/**
 * SELECT statement.
 *
 * @author Miquel Sas
 */
public class QSelect extends QBase {

	/**
	 * Tables used, by alias. Aliases are unique ignoring case, and keep the case
	 * of their first spelling. Filled by the FROM clause, the joins and the
	 * EXISTS clauses of the WHERE part.
	 */
	private final Map<String, Table> tables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	/**
	 * FROM clause, with the master table and its alias, or null while it is not
	 * set.
	 */
	private QFrom from;

	/**
	 * Constructor.
	 *
	 * @param db The database engine.
	 * @throws NullPointerException If the engine is null.
	 */
	public QSelect(DBEngine db) {
		super(db);
	}

	/**
	 * Sets the FROM clause with the master table, and registers the table.
	 *
	 * @param table The master table.
	 * @param alias The alias, or null to use the name of the table.
	 * @throws NullPointerException     If the table is null.
	 * @throws IllegalArgumentException If the alias is blank.
	 * @throws IllegalStateException    If the FROM clause is already set.
	 */
	public void from(Table table, String alias) {
		if (from != null) {
			throw new IllegalStateException("The FROM clause is already set");
		}
		String name = addTable(table, alias);
		from = new QFrom(getDBEngine(), table, name);
	}

	/**
	 * Adds a table used by the statement. Called by the FROM clause, the joins
	 * and the EXISTS clauses.
	 *
	 * @param table The table.
	 * @param alias The alias, or null to use the name of the table.
	 * @return The alias the table is registered with.
	 * @throws NullPointerException     If the table is null.
	 * @throws IllegalArgumentException If the alias is blank, or is already used,
	 *                                  compared ignoring case.
	 */
	private String addTable(Table table, String alias) {
		Objects.requireNonNull(table, "Table can not be null");
		String name = (alias != null ? alias : table.getName());
		if (name.isBlank()) {
			throw new IllegalArgumentException("Alias can not be blank");
		}
		if (tables.containsKey(name)) {
			StringBuilder msg = new StringBuilder();
			msg.append("Alias ");
			msg.append(name);
			msg.append(" is already used by table ");
			msg.append(tables.get(name).getName());
			throw new IllegalArgumentException(msg.toString());
		}
		tables.put(name, table);
		return name;
	}
}
