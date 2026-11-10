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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import msfx.lib.db.DBEngine;
import msfx.lib.md.Table;

/**
 * FROM part of a statement: the tables involved, each one with its alias. The
 * first table is the main table of the statement, the following ones are added
 * by joins. It is shared by all the parts of the statement, that validate their
 * column references against it.
 * <p>
 * Created only by its statement.
 *
 * @author Miquel Sas
 */
public class QFrom extends QBase {

	/**
	 * A table involved in the statement and its alias.
	 *
	 * @param table The table.
	 * @param alias The alias, the name of the table when not set explicitly.
	 */
	public record Entry(Table table, String alias) {
	}

	/**
	 * Tables and aliases, the main table first.
	 */
	private final List<Entry> entries = new ArrayList<>();

	/**
	 * Constructor.
	 *
	 * @param db    The database engine.
	 * @param table The main table.
	 * @param alias The alias, or null to use the name of the table.
	 * @throws NullPointerException     If the engine or the table is null.
	 * @throws IllegalArgumentException If the alias is duplicated.
	 */
	QFrom(DBEngine db, Table table, String alias) {
		super(db);
		add(table, alias);
	}

	/**
	 * Adds a table. Aliases must be unique ignoring case, so a table involved
	 * more than once needs explicit aliases.
	 *
	 * @param table The table.
	 * @param alias The alias, or null to use the name of the table.
	 * @return The entry added.
	 * @throws NullPointerException     If the table is null.
	 * @throws IllegalArgumentException If the alias is duplicated.
	 */
	Entry add(Table table, String alias) {
		Objects.requireNonNull(table, "Table can not be null");
		String name = (alias != null ? alias : table.getName());
		for (Entry entry : entries) {
			if (entry.alias().equalsIgnoreCase(name)) {
				StringBuilder msg = new StringBuilder();
				msg.append("Duplicate alias ");
				msg.append(name);
				throw new IllegalArgumentException(msg.toString());
			}
		}
		Entry entry = new Entry(table, name);
		entries.add(entry);
		return entry;
	}

	/**
	 * @return The tables and aliases, the main table first.
	 */
	public List<Entry> getEntries() {
		return List.copyOf(entries);
	}
}
