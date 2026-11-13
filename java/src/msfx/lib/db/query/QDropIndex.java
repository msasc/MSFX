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
import msfx.lib.md.Index;
import msfx.lib.md.Table;

/**
 * DROP INDEX statement, for the secondary indexes of a table, either defined by
 * the application or read from the database metadata. The primary key is
 * dropped by {@link QDropPrimaryKey}.
 *
 * @author Miquel Sas
 */
public class QDropIndex extends QBase {

	/**
	 * The table of the index.
	 */
	private final Table table;
	/**
	 * The index to drop.
	 */
	private final Index index;

	/**
	 * Constructor.
	 *
	 * @param db    The database engine.
	 * @param table The table of the index.
	 * @param index The index to drop.
	 * @throws NullPointerException     If the engine, the table or the index is
	 *                                  null.
	 * @throws IllegalArgumentException If the index is the primary key, or is not
	 *                                  a secondary index of the table.
	 */
	public QDropIndex(DBEngine db, Table table, Index index) {
		super(db);
		this.table = Objects.requireNonNull(table, "Table can not be null");
		this.index = Objects.requireNonNull(index, "Index can not be null");
		if (index == table.getPrimaryKey()) {
			StringBuilder msg = new StringBuilder();
			msg.append("Index ");
			msg.append(index.getName());
			msg.append(" is the primary key of table ");
			msg.append(table.getName());
			msg.append(", drop it with QDropPrimaryKey");
			throw new IllegalArgumentException(msg.toString());
		}
		if (!table.getIndexes().contains(index)) {
			StringBuilder msg = new StringBuilder();
			msg.append("Index ");
			if (index.getName() != null) {
				msg.append(index.getName());
				msg.append(" ");
			}
			msg.append("is not a secondary index of table ");
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
	 * Returns the SQL statement, that differs in each database:
	 * <ul>
	 * <li>SQL Server: DROP INDEX name ON table, since indexes belong to the
	 * table.</li>
	 * <li>Oracle: DROP INDEX schema.name, since indexes belong to the schema, as
	 * created by {@link QCreateIndex}.</li>
	 * </ul>
	 *
	 * @return The SQL statement.
	 */
	private String toSQL() {
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		sql.append("DROP INDEX ");
		String drop = switch (db.getAdapter()) {
			case SQLSERVER: {
				StringBuilder b = new StringBuilder();
				b.append(db.quote(index.getName()));
				b.append(" ON ");
				b.append(getQualifiedName(table));
				yield b.toString();
			}
			case ORACLE: {
				StringBuilder b = new StringBuilder();
				if (table.getSchema() != null) {
					b.append(db.quote(table.getSchema()));
					b.append(".");
				}
				b.append(db.quote(index.getName()));
				yield b.toString();
			}
		};
		sql.append(drop);
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
