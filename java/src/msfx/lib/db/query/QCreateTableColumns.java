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
import java.util.List;
import java.util.Objects;

import msfx.lib.db.DBEngine;
import msfx.lib.md.Column;
import msfx.lib.md.Table;

/**
 * CREATE TABLE statement that creates a table with its columns only, as defined
 * in the table. The primary key is created by {@link QCreatePrimaryKey} and the
 * secondary indexes by {@link QCreateIndex}, or all of them at once by
 * {@link QCreateTable}.
 * <p>
 * Creating the table without indexes is also the fastest way to load it with
 * many rows, creating the indexes after the load.
 *
 * @author Miquel Sas
 */
public class QCreateTableColumns extends QBase {

	/**
	 * The table to create.
	 */
	private final Table table;

	/**
	 * Constructor.
	 *
	 * @param db    The database engine.
	 * @param table The table to create.
	 * @throws NullPointerException     If the engine or the table is null.
	 * @throws IllegalArgumentException If the table has no columns.
	 */
	public QCreateTableColumns(DBEngine db, Table table) {
		super(db);
		this.table = Objects.requireNonNull(table, "Table can not be null");
		if (table.columns().isEmpty()) {
			StringBuilder msg = new StringBuilder();
			msg.append("Table ");
			msg.append(table.getName());
			msg.append(" has no columns");
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
	 *         CREATE TABLE table (column type suffix, ...), where each column is
	 *         defined as in {@link QAddColumn}.
	 */
	private String toSQL() {
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		sql.append("CREATE TABLE ");
		sql.append(getQualifiedName(table));
		sql.append(" (");
		List<Column> columns = table.columns().columns();
		for (int i = 0; i < columns.size(); i++) {
			Column column = columns.get(i);
			if (i > 0) {
				sql.append(", ");
			}
			sql.append(db.quote(column.getName()));
			sql.append(" ");
			sql.append(db.getColumnType(column));
			String suffix = db.getColumnDefSuffix(column);
			if (!suffix.isEmpty()) {
				sql.append(" ");
				sql.append(suffix);
			}
		}
		sql.append(")");
		return sql.toString();
	}
}
