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
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.md.Column;
import msfx.lib.md.Table;

/**
 * ALTER TABLE statement that drops a column. The column must be in the table,
 * either defined by the application or read from the database metadata.
 * <p>
 * Indexes and the primary key that contain the column must be dropped before:
 * SQL Server rejects the statement otherwise.
 *
 * @author Miquel Sas
 */
public class QDropColumn extends QBase {

	/**
	 * The table to alter.
	 */
	private final Table table;
	/**
	 * The column to drop.
	 */
	private final Column column;

	/**
	 * Constructor.
	 *
	 * @param db     The database engine.
	 * @param table  The table to alter.
	 * @param column The column to drop.
	 * @throws NullPointerException     If the engine, the table or the column is
	 *                                  null.
	 * @throws IllegalArgumentException If the column is not in the table.
	 */
	public QDropColumn(DBEngine db, Table table, Column column) {
		super(db);
		this.table = Objects.requireNonNull(table, "Table can not be null");
		this.column = Objects.requireNonNull(column, "Column can not be null");
		if (!table.columns().columns().contains(column)) {
			StringBuilder msg = new StringBuilder();
			msg.append("Column ");
			msg.append(column.getName());
			msg.append(" is not in table ");
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
	 * Returns the SQL statement: ALTER TABLE table DROP COLUMN column, with this
	 * difference:
	 * <ul>
	 * <li>SQL Server: a column with a default constraint can not be dropped, so
	 * the constraint DF_table_column is dropped in the same statement, if it
	 * exists. Defaults created otherwise, with other names, make the database
	 * reject the statement.</li>
	 * </ul>
	 *
	 * @return The SQL statement.
	 */
	private String toSQL() {
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		sql.append("ALTER TABLE ");
		sql.append(getQualifiedName(table));
		String drop = switch (db.getAdapter()) {
			case SQLSERVER: {
				StringBuilder b = new StringBuilder();
				b.append(" DROP CONSTRAINT IF EXISTS ");
				b.append(db.quote(DBEngineSQLServer.getDefaultConstraintName(table.getName(), column.getName())));
				b.append(", COLUMN ");
				yield b.toString();
			}
			case ORACLE:
				yield " DROP COLUMN ";
		};
		sql.append(drop);
		sql.append(db.quote(column.getName()));
		return sql.toString();
	}
}
