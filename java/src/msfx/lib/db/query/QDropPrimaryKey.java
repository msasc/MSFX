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

import java.sql.SQLException;
import java.util.Objects;

import msfx.lib.db.DBEngine;
import msfx.lib.md.Table;

/**
 * ALTER TABLE statement that drops the primary key of a table, as defined in
 * the table, either by the application or read from the database metadata.
 * <p>
 * The index of the primary key is dropped with the constraint. Foreign keys
 * that reference the primary key must be dropped before: the database rejects
 * the statement otherwise.
 *
 * @author Miquel Sas
 */
public class QDropPrimaryKey extends QBase {

	/**
	 * The table, that defines the primary key.
	 */
	private final Table table;

	/**
	 * Constructor.
	 *
	 * @param db    The database engine.
	 * @param table The table, that defines the primary key.
	 * @throws NullPointerException     If the engine or the table is null.
	 * @throws IllegalArgumentException If the table has no primary key.
	 */
	public QDropPrimaryKey(DBEngine db, Table table) {
		super(db);
		this.table = Objects.requireNonNull(table, "Table can not be null");
		if (table.getPrimaryKey() == null) {
			StringBuilder msg = new StringBuilder();
			msg.append("Table ");
			msg.append(table.getName());
			msg.append(" has no primary key");
			throw new IllegalArgumentException(msg.toString());
		}
	}

	/**
	 * Executes the statement.
	 *
	 * @throws SQLException If a database access error occurs.
	 */
	public void execute() throws SQLException {
		executeUpdate(toSQL());
	}

	/**
	 * @return The SQL statement, with the same syntax in all the databases:
	 *         ALTER TABLE table DROP CONSTRAINT name.
	 */
	private String toSQL() {
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		sql.append("ALTER TABLE ");
		sql.append(getQualifiedName(table));
		sql.append(" DROP CONSTRAINT ");
		sql.append(db.quote(table.getPrimaryKey().getName()));
		return sql.toString();
	}

	/**
	 * Returns the SQL statement, as {@link #execute()} runs it.
	 *
	 * @return The SQL statement.
	 */
	@Override
	public String toString() {
		return toSQL();
	}
}
