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
 * Creates a table as defined, in three steps, each one its own statement:
 * <ol>
 * <li>The table with its columns, by {@link QCreateTableColumns}.</li>
 * <li>The primary key, if defined, by {@link QCreatePrimaryKey}.</li>
 * <li>The secondary indexes, by {@link QCreateIndex}.</li>
 * </ol>
 * If a step fails, the steps already executed are not undone: the table may
 * remain created without its primary key or some indexes.
 *
 * @author Miquel Sas
 */
public class QCreateTable extends QBase {

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
	public QCreateTable(DBEngine db, Table table) {
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
	 * Executes the statements of the three steps, in order.
	 *
	 * @throws SQLException If a database access error occurs.
	 */
	public void execute() throws SQLException {
		DBEngine db = getDBEngine();
		new QCreateTableColumns(db, table).execute();
		if (table.getPrimaryKey() != null) {
			new QCreatePrimaryKey(db, table).execute();
		}
		for (Index index : table.getIndexes()) {
			new QCreateIndex(db, table, index).execute();
		}
	}
}
