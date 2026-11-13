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
 * INSERT statement into a single table.
 *
 * @author Miquel Sas
 */
public class QInsert extends QBase {

	/**
	 * The table to insert into.
	 */
	private final Table table;

	/**
	 * Constructor.
	 *
	 * @param db    The database engine.
	 * @param table The table to insert into.
	 * @throws NullPointerException If the engine or the table is null.
	 */
	public QInsert(DBEngine db, Table table) {
		super(db);
		this.table = Objects.requireNonNull(table, "Table can not be null");
	}

	/**
	 * @return The table to insert into.
	 */
	public Table getTable() {
		return table;
	}

	/**
	 * Executes the statement.
	 *
	 * @return The number of rows inserted.
	 * @throws SQLException If a database access error occurs.
	 */
	public int execute() throws SQLException {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * Returns the SQL, not generated yet.
	 *
	 * @return The SQL.
	 */
	@Override
	public String toString() {
		throw new UnsupportedOperationException("Not implemented yet");
	}
}
