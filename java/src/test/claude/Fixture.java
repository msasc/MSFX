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

package test.claude;

import java.sql.SQLException;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.adapters.DBEngineOracle;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.md.Catalog;
import msfx.lib.md.Table;
import test.db.CatalogCMA;

/**
 * What the tests share: the engines, the CMA catalog, and the local SQL
 * Server, the test database, with helpers to run written SQL on it. The
 * Oracle engine is never connected: it only writes SQL.
 *
 * @author Miquel Sas
 */
public final class Fixture {

	/**
	 * SQL Server engine, connected to the local database by {@link #connect()}.
	 */
	public static final DBEngineSQLServer SQLSERVER = new DBEngineSQLServer();
	/**
	 * Oracle engine, never connected.
	 */
	public static final DBEngineOracle ORACLE = new DBEngineOracle();
	/**
	 * The catalog of the CMA tables, created on first use.
	 */
	private static Catalog catalog = null;

	/**
	 * Private constructor, only static methods.
	 */
	private Fixture() {
	}

	/**
	 * Returns a table of the CMA catalog, in the schema of the local copy.
	 *
	 * @param name The name of the table.
	 * @return The table, a copy on each call.
	 * @throws IllegalArgumentException If the table is not in the catalog.
	 */
	public static Table table(String name) {
		if (catalog == null) {
			catalog = new Catalog();
			catalog.addProvider(new CatalogCMA());
		}
		Table table = catalog.getTable(CatalogCMA.SCHEMA, name);
		if (table == null) {
			StringBuilder msg = new StringBuilder();
			msg.append("Table ");
			msg.append(name);
			msg.append(" not in the CMA catalog");
			throw new IllegalArgumentException(msg.toString());
		}
		return table;
	}

	/**
	 * Connects the SQL Server engine to the local database, once.
	 */
	public static void connect() {
		if (SQLSERVER.getDatabaseKey() == null) {
			SQLSERVER.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");
		}
	}

	/**
	 * Reads the rows of a written query on SQL Server as text, one row per
	 * line, the columns separated by spaces.
	 *
	 * @param sql The query.
	 * @return The rows, or the message of the error.
	 */
	public static String rows(String sql) {
		StringBuilder result = new StringBuilder();
		try (DBConnection cn = SQLSERVER.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql)) {
			int count = rs.getMetaData().getColumnCount();
			while (rs.next()) {
				for (int i = 1; i <= count; i++) {
					if (i > 1) {
						result.append(" ");
					}
					result.append(rs.getString(i));
				}
				result.append("\n");
			}
		} catch (SQLException exc) {
			result.append(exc.getMessage());
		}
		return result.toString();
	}

	/**
	 * Returns the first column of the first row of a written query on SQL
	 * Server.
	 *
	 * @param sql The query.
	 * @return The value, as a string.
	 * @throws SQLException If a database access error occurs.
	 */
	public static String queryOne(String sql) throws SQLException {
		try (DBConnection cn = SQLSERVER.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql)) {
			rs.next();
			String value = rs.getString(1);
			return value;
		}
	}

	/**
	 * Executes a written statement that returns no rows on SQL Server.
	 *
	 * @param sql The statement.
	 * @throws SQLException If a database access error occurs.
	 */
	public static void execute(String sql) throws SQLException {
		try (DBConnection cn = SQLSERVER.getConnection(); DBStatement st = cn.createStatement()) {
			st.executeUpdate(sql);
		}
	}

	/**
	 * Runs a query written by the library and a hand-written one on SQL Server,
	 * and checks that they read the same rows, at least one.
	 *
	 * @param title    What is checked.
	 * @param query    The query written by the library.
	 * @param expected The hand-written query.
	 */
	public static void sameRows(String title, Object query, String expected) {
		String sql = query.toString();
		String result = rows(sql);
		String expectedRows = rows(expected);
		boolean ok = !result.isEmpty() && result.equals(expectedRows);
		StringBuilder shown = new StringBuilder();
		shown.append(result.lines().count());
		shown.append(" rows");
		Check.report(ok, title, ok ? shown.toString() : result, expectedRows);
	}
}
