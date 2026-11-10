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

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBEngine;
import msfx.lib.db.DBStatement;
import msfx.lib.md.Table;

/**
 * Root of query classes.
 *
 * @author Miquel Sas
 */
public abstract class QBase {

    /**
     * Database engine the query is built for and executed against. It quotes
     * names and writes the parts of the SQL that differ by database. All the
     * parts of a query, like the select, from and where, must have the same
     * engine, so that they are written for the same database.
     */
    private final DBEngine db;

    /**
     * Constructor.
     *
     * @param db The database engine the query is built for and executed against.
     * @throws NullPointerException If the engine is null.
     */
    public QBase(DBEngine db) {
        Objects.requireNonNull(db, "DBEngine can not be null");
        this.db = db;
    }

    /**
     * @return The database engine the query is built for and executed against.
     */
    public DBEngine getDBEngine() {
        return db;
    }

    /**
     * @param table The table.
     * @return The name of the table, qualified with its schema when it has one,
     *         each part quoted.
     */
    protected String getQualifiedName(Table table) {
        StringBuilder name = new StringBuilder();
        String schema = table.getSchema();
        if (schema != null) {
            name.append(db.quote(schema));
            name.append(".");
        }
        name.append(db.quote(table.getName()));
        return name.toString();
    }

    /**
     * Executes a statement that returns no rows, with a connection of the pool.
     *
     * @param sql The SQL statement.
     * @return The number of rows affected, or 0 for statements like DDL.
     * @throws SQLException If a database access error occurs.
     */
    protected int executeUpdate(String sql) throws SQLException {
        try (DBConnection cn = db.getConnection(); DBStatement st = cn.createStatement()) {
            return st.executeUpdate(sql);
        }
    }
}
