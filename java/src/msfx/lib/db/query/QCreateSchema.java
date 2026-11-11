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

/**
 * CREATE SCHEMA statement.
 * <p>
 * Supported in SQL Server only. In Oracle a schema is a user, created by the
 * administrator with its password, tablespace quota and privileges, which this
 * library does not manage.
 *
 * @author Miquel Sas
 */
public class QCreateSchema extends QBase {

	/**
	 * The name of the schema to create.
	 */
	private final String schema;

	/**
	 * Constructor.
	 *
	 * @param db     The database engine.
	 * @param schema The name of the schema to create.
	 * @throws NullPointerException          If the engine or the schema is null.
	 * @throws IllegalArgumentException      If the schema is blank.
	 * @throws UnsupportedOperationException If the database is Oracle, where a
	 *                                       schema is a user created by the
	 *                                       administrator.
	 */
	public QCreateSchema(DBEngine db, String schema) {
		super(db);
		this.schema = Objects.requireNonNull(schema, "Schema can not be null");
		if (schema.isBlank()) {
			throw new IllegalArgumentException("Schema can not be blank");
		}
		boolean supported = switch (db.getAdapter()) {
			case SQLSERVER:
				yield true;
			case ORACLE:
				yield false;
		};
		if (!supported) {
			StringBuilder msg = new StringBuilder();
			msg.append("Schema ");
			msg.append(schema);
			msg.append(" can not be created: in Oracle a schema is a user, created by the administrator");
			throw new UnsupportedOperationException(msg.toString());
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
	 * @return The SQL statement: CREATE SCHEMA schema.
	 */
	private String toSQL() {
		StringBuilder sql = new StringBuilder();
		sql.append("CREATE SCHEMA ");
		sql.append(getDBEngine().quote(schema));
		return sql.toString();
	}
}
