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
 * DROP TABLE statement.
 * <p>
 * The primary key, the indexes and the default constraints of the table are
 * dropped with it. Foreign keys of other tables that reference it must be
 * dropped before: the database rejects the statement otherwise.
 *
 * @author Miquel Sas
 */
public class QDropTable extends QBase {

	/**
	 * The table to drop.
	 */
	private final Table table;

	/**
	 * Constructor.
	 *
	 * @param db    The database engine.
	 * @param table The table to drop.
	 * @throws NullPointerException If the engine or the table is null.
	 */
	public QDropTable(DBEngine db, Table table) {
		super(db);
		this.table = Objects.requireNonNull(table, "Table can not be null");
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
	 * Returns the SQL statement, with the same syntax in all the databases: DROP
	 * TABLE table. In Oracle the table goes to the recycle bin, from where the
	 * administrator can restore or purge it.
	 *
	 * @return The SQL statement.
	 */
	private String toSQL() {
		StringBuilder sql = new StringBuilder();
		sql.append("DROP TABLE ");
		sql.append(getQualifiedName(table));
		return sql.toString();
	}
}
