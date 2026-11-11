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
 * ALTER TABLE statement that creates the primary key of a table, as defined in
 * the table.
 * <p>
 * The columns of the primary key must be NOT NULL in the database and have no
 * null values. Columns created by this library already are: they are never
 * nullable in the definition, and are created NOT NULL, except STRING columns in
 * Oracle, which Oracle sets NOT NULL itself when the primary key is added.
 * Columns created otherwise, or holding null values, make the database reject
 * the statement: this class does not change data.
 *
 * @author Miquel Sas
 */
public class QCreatePrimaryKey extends QBase {

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
	public QCreatePrimaryKey(DBEngine db, Table table) {
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
	 * Returns the SQL statement: ALTER TABLE table ADD CONSTRAINT name PRIMARY KEY
	 * (columns). In SQL Server, CLUSTERED is written, to state that the primary key
	 * is the clustered index of the table, although it is the default. The name is
	 * not qualified with the schema: the constraint belongs to the schema of the
	 * table.
	 *
	 * @return The SQL statement.
	 */
	private String toSQL() {
		DBEngine db = getDBEngine();
		Index pk = table.getPrimaryKey();
		StringBuilder sql = new StringBuilder();
		sql.append("ALTER TABLE ");
		sql.append(getQualifiedName(table));
		sql.append(" ADD CONSTRAINT ");
		sql.append(db.quote(pk.getName()));
		String primaryKey = switch (db.getAdapter()) {
			case SQLSERVER:
				yield " PRIMARY KEY CLUSTERED (";
			case ORACLE:
				yield " PRIMARY KEY (";
		};
		sql.append(primaryKey);
		List<Column> columns = pk.getColumns();
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
