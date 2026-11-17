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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import msfx.lib.md.Table;

/**
 * The tables a statement registers, by alias, and the tables it sees: its own
 * and those of its parent scope, the statement whose WHERE clause holds it as
 * an EXISTS select. Aliases are unique ignoring case in the whole tree of
 * scopes, so that an inner alias never hides an outer one.
 * <p>
 * Created only by its statement, and shared with its parts.
 *
 * @author Miquel Sas
 */
class QScope {

	/**
	 * The parent scope, or null for a statement that is not an EXISTS select.
	 */
	private final QScope parent;
	/**
	 * Tables registered by the statement, by alias. Aliases are unique ignoring
	 * case, and keep the case of their first spelling.
	 */
	private final Map<String, Table> tables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	/**
	 * Aliases used by the EXISTS selects attached to the statement or to its
	 * inner selects. The statement can not use them, since its expressions do
	 * not see their tables.
	 */
	private final Set<String> reserved = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

	/**
	 * Constructor.
	 *
	 * @param parent The parent scope, or null.
	 */
	QScope(QScope parent) {
		this.parent = parent;
	}

	/**
	 * Returns the parent scope.
	 *
	 * @return The parent scope, or null.
	 */
	QScope getParent() {
		return parent;
	}

	/**
	 * Checks whether the statement has registered no table yet.
	 *
	 * @return True if there are no tables of its own.
	 */
	boolean isEmpty() {
		return tables.isEmpty();
	}

	/**
	 * Checks a table and its alias before registering them.
	 *
	 * @param table The table.
	 * @param alias The alias.
	 * @throws NullPointerException     If the table or the alias is null.
	 * @throws IllegalArgumentException If the alias is blank, or is already used
	 *                                  by this scope, a parent one or an attached
	 *                                  EXISTS select, compared ignoring case.
	 */
	void checkTable(Table table, String alias) {
		Objects.requireNonNull(table, "Table can not be null");
		Objects.requireNonNull(alias, "Alias can not be null");
		if (alias.isBlank()) {
			throw new IllegalArgumentException("Alias can not be blank");
		}
		Map<String, Table> visible = resolve();
		if (visible.containsKey(alias)) {
			StringBuilder msg = new StringBuilder();
			msg.append("Alias ");
			msg.append(alias);
			msg.append(" is already used by table ");
			msg.append(visible.get(alias).getName());
			throw new IllegalArgumentException(msg.toString());
		}
		if (reserved.contains(alias)) {
			StringBuilder msg = new StringBuilder();
			msg.append("Alias ");
			msg.append(alias);
			msg.append(" is already used by an EXISTS select");
			throw new IllegalArgumentException(msg.toString());
		}
	}

	/**
	 * Registers a table, already checked.
	 *
	 * @param alias The alias.
	 * @param table The table.
	 */
	void put(String alias, Table table) {
		tables.put(alias, table);
	}

	/**
	 * Returns the tables registered by the statement.
	 *
	 * @return The tables, by alias, unmodifiable.
	 */
	Map<String, Table> getTables() {
		return Collections.unmodifiableMap(tables);
	}

	/**
	 * Returns the tables the expressions of the statement can use: its own and
	 * those of the parent scopes.
	 *
	 * @return The tables, by alias, a new map on each call.
	 */
	Map<String, Table> resolve() {
		Map<String, Table> visible = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		if (parent != null) {
			Map<String, Table> parentTables = parent.resolve();
			visible.putAll(parentTables);
		}
		visible.putAll(tables);
		return visible;
	}

	/**
	 * Returns the aliases this scope takes in the tree: those of its tables and
	 * those reserved by its EXISTS selects.
	 *
	 * @return The aliases, a new set on each call.
	 */
	Set<String> getAliases() {
		Set<String> aliases = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		aliases.addAll(tables.keySet());
		aliases.addAll(reserved);
		return aliases;
	}

	/**
	 * Reserves the aliases of an attached EXISTS select in this scope and the
	 * parent ones.
	 *
	 * @param aliases The aliases.
	 */
	void reserve(Set<String> aliases) {
		for (QScope scope = this; scope != null; scope = scope.parent) {
			scope.reserved.addAll(aliases);
		}
	}

	/**
	 * Returns the aliases of the tables registered and the reserved ones.
	 *
	 * @return The aliases.
	 */
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("tables ");
		b.append(tables.keySet());
		b.append(", reserved ");
		b.append(reserved);
		return b.toString();
	}
}
