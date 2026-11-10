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

import msfx.lib.db.DBEngine;
import msfx.lib.md.Table;

/**
 * UPDATE statement on a single table.
 *
 * @author Miquel Sas
 */
public class QUpdate extends QBase {

	/**
	 * The table updated, as the only table involved.
	 */
	private final QFrom from;
	/**
	 * WHERE conditions.
	 */
	private final QWhere where;

	/**
	 * Constructor.
	 *
	 * @param db    The database engine.
	 * @param table The table to update.
	 * @throws NullPointerException If the engine or the table is null.
	 */
	public QUpdate(DBEngine db, Table table) {
		super(db);
		from = new QFrom(db, table, null);
		where = new QWhere(from);
	}

	/**
	 * @return The table updated, as the only table involved.
	 */
	public QFrom getFrom() {
		return from;
	}

	/**
	 * @return The WHERE conditions.
	 */
	public QWhere getWhere() {
		return where;
	}

	/**
	 * Executes the statement.
	 *
	 * @return The number of rows updated.
	 * @throws SQLException If a database access error occurs.
	 */
	public int execute() throws SQLException {
		throw new UnsupportedOperationException("Not implemented yet");
	}
}
