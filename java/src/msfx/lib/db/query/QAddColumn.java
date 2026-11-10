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
import msfx.lib.md.Column;
import msfx.lib.md.Table;

/**
 * ALTER TABLE statement that adds a column. The column must already be in the
 * definition of the table, so the definition and the database end up matching.
 *
 * @author Miquel Sas
 */
public class QAddColumn extends QBase {

	/**
	 * The table to alter.
	 */
	private final Table table;
	/**
	 * The column to add.
	 */
	private final Column column;

	/**
	 * Constructor.
	 *
	 * @param db     The database engine.
	 * @param table  The table to alter.
	 * @param column The column to add.
	 * @throws NullPointerException     If the engine, the table or the column is
	 *                                  null.
	 * @throws IllegalArgumentException If the column is not in the definition of
	 *                                  the table.
	 */
	public QAddColumn(DBEngine db, Table table, Column column) {
		super(db);
		this.table = Objects.requireNonNull(table, "Table can not be null");
		this.column = Objects.requireNonNull(column, "Column can not be null");
		if (!table.columns().columns().contains(column)) {
			StringBuilder msg = new StringBuilder();
			msg.append("Column ");
			msg.append(column.getName());
			msg.append(" is not in the definition of table ");
			msg.append(table.getName());
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
	 *         ALTER TABLE table ADD column type suffix, where the suffix has the
	 *         default value and NOT NULL when they apply. Existing rows get the
	 *         default value.
	 */
	private String toSQL() {
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		sql.append("ALTER TABLE ");
		sql.append(getQualifiedName(table));
		sql.append(" ADD ");
		sql.append(db.quote(column.getName()));
		sql.append(" ");
		sql.append(db.getColumnType(column));
		String suffix = db.getColumnDefSuffix(column);
		if (!suffix.isEmpty()) {
			sql.append(" ");
			sql.append(suffix);
		}
		return sql.toString();
	}
}
