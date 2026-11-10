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
import msfx.lib.md.Index;
import msfx.lib.md.Table;

/**
 * CREATE INDEX statement, for the secondary indexes of a table. The index must
 * already be in the definition of the table, so the definition and the database
 * end up matching. The primary key is created by {@link QCreatePrimaryKey}, and
 * unique indexes are not accepted, since by design secondary indexes are never
 * unique.
 *
 * @author Miquel Sas
 */
public class QCreateIndex extends QBase {

	/**
	 * The table of the index.
	 */
	private final Table table;
	/**
	 * The index to create.
	 */
	private final Index index;

	/**
	 * Constructor.
	 *
	 * @param db    The database engine.
	 * @param table The table of the index.
	 * @param index The index to create.
	 * @throws NullPointerException     If the engine, the table or the index is
	 *                                  null.
	 * @throws IllegalArgumentException If the index is the primary key, is
	 *                                  unique, or is not a secondary index in
	 *                                  the definition of the table.
	 */
	public QCreateIndex(DBEngine db, Table table, Index index) {
		super(db);
		this.table = Objects.requireNonNull(table, "Table can not be null");
		this.index = Objects.requireNonNull(index, "Index can not be null");
		if (index == table.getPrimaryKey()) {
			StringBuilder msg = new StringBuilder();
			msg.append("Index ");
			msg.append(index.getName());
			msg.append(" is the primary key of table ");
			msg.append(table.getName());
			msg.append(", create it with QCreatePrimaryKey");
			throw new IllegalArgumentException(msg.toString());
		}
		if (index.isUnique()) {
			StringBuilder msg = new StringBuilder();
			msg.append("Index ");
			msg.append(index.getName());
			msg.append(" is unique, secondary indexes can not be unique");
			throw new IllegalArgumentException(msg.toString());
		}
		if (!table.getIndexes().contains(index)) {
			StringBuilder msg = new StringBuilder();
			msg.append("Index ");
			if (index.getName() != null) {
				msg.append(index.getName());
				msg.append(" ");
			}
			msg.append("is not in the definition of table ");
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
	 * Returns the SQL statement: CREATE INDEX name ON table (columns), with these
	 * differences:
	 * <ul>
	 * <li>SQL Server: NONCLUSTERED is written, to state that secondary indexes
	 * are never the clustered index, although it is the default.</li>
	 * <li>Oracle: indexes belong to the schema, not to the table, so the name is
	 * qualified with the schema of the table. Otherwise the index would be created
	 * in the schema of the connected user.</li>
	 * </ul>
	 *
	 * @return The SQL statement.
	 */
	private String toSQL() {
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		sql.append(switch (db.getAdapter()) {
			case SQLSERVER -> "CREATE NONCLUSTERED INDEX ";
			case ORACLE -> "CREATE INDEX ";
		});
		boolean schemaIndexes = switch (db.getAdapter()) {
			case ORACLE -> true;
			case SQLSERVER -> false;
		};
		if (schemaIndexes && table.getSchema() != null) {
			sql.append(db.quote(table.getSchema()));
			sql.append(".");
		}
		sql.append(db.quote(index.getName()));
		sql.append(" ON ");
		sql.append(getQualifiedName(table));
		sql.append(" (");
		List<Column> columns = index.getColumns();
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append(db.quote(columns.get(i).getName()));
		}
		sql.append(")");
		return sql.toString();
	}
}
