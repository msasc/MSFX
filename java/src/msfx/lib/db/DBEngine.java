/*
 * Copyright (c) 2026 Miquel Sas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package msfx.lib.db;

import msfx.lib.db.pool.ConnectionPool;
import msfx.lib.md.Column;
import msfx.lib.util.Properties;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Database engine that represents the back-end system to which we connect through a JDBC driver.
 * <p>
 * MSFX Applications use by default 3 schemas, one for system tables, one for strictly application
 * data tables, and one for temporary tables. An application may also use a single schema for all.
 * <p>
 * The engine keeps two groups of settings:
 * <ul>
 * <li>The connection settings (URL, user, password) and the schemas, set by
 * {@link #connectTo(String, String, String, String, String, String)}. The URL, user and password
 * are only used internally to open connections.</li>
 * <li>The driver properties, any additional property the JDBC driver accepts. Each adapter sets its
 * defaults, and the application can change them with {@link #setDriverProperty(String, String)}
 * and {@link #removeDriverProperty(String)}. They take effect when a physical connection is opened,
 * so changes made once connections exist only affect new ones.</li>
 * </ul>
 *
 * @author Miquel Sas
 */
public abstract class DBEngine {

	private static final String PROP_URL = "url";
	private static final String PROP_USER = "user";
	private static final String PROP_PASSWORD = "password";
	private static final String PROP_SCHEMA_DAT = "schemaDat";
	private static final String PROP_SCHEMA_SYS = "schemaSys";
	private static final String PROP_SCHEMA_TMP = "schemaTmp";

	/**
	 * Connection settings and schemas.
	 */
	private final Properties props = new Properties();
	/**
	 * Additional driver properties, name and string value.
	 */
	private final Properties driverProps = new Properties();
	/**
	 * Connection pool associated with this engine.
	 */
	private final ConnectionPool pool;

	/**
	 * Constructor. Internally initializes the connection pool.
	 */
	@SuppressWarnings("this-escape")
	public DBEngine() {
		// The pool only keeps the reference, it opens connections when requested.
		pool = new ConnectionPool(this::openConnection);
	}

	/**
	 * @param col The column.
	 * @return The proper definition to create a column in the underlying database.
	 */
	public abstract String getColumnDef(Column col);

	/**
	 * @param cnString The connection string, the engine specific part of the URL.
	 * @return The JDBC URL.
	 */
	protected abstract String getUrl(String cnString);

	/**
	 * Sets the connection settings and the schemas. Connections are opened later,
	 * when requested.
	 *
	 * @param cnString  The connection string, the engine specific part of the URL.
	 * @param user      The user, or null if the driver does not need it, for instance
	 *                  with integrated security.
	 * @param password  The password, or null if the driver does not need it.
	 * @param schemaDat The schema for data tables.
	 * @param schemaSys The schema for system tables.
	 * @param schemaTmp The schema for temporary tables.
	 * @throws IllegalArgumentException If the connection string or a schema is null.
	 */
	public final void connectTo(
			String cnString,
			String user,
			String password,
			String schemaDat,
			String schemaSys,
			String schemaTmp) {

		checkNotNull(cnString, "Connection string");
		checkNotNull(schemaDat, "Schema DAT");
		checkNotNull(schemaSys, "Schema SYS");
		checkNotNull(schemaTmp, "Schema TMP");

		props.put(PROP_URL, getUrl(cnString));
		putOrRemove(PROP_USER, user);
		putOrRemove(PROP_PASSWORD, password);
		props.put(PROP_SCHEMA_DAT, schemaDat);
		props.put(PROP_SCHEMA_SYS, schemaSys);
		props.put(PROP_SCHEMA_TMP, schemaTmp);
	}
	/**
	 * Sets the connection settings using a single schema for data, system and
	 * temporary tables.
	 *
	 * @param cnString The connection string, the engine specific part of the URL.
	 * @param user     The user, or null if the driver does not need it.
	 * @param password The password, or null if the driver does not need it.
	 * @param schema   The schema for all tables.
	 * @throws IllegalArgumentException If the connection string or the schema is null.
	 */
	public final void connectTo(String cnString, String user, String password, String schema) {
		connectTo(cnString, user, password, schema, schema, schema);
	}

	/**
	 * Sets a driver property, overriding the adapter default if any.
	 *
	 * @param name  The property name, as the driver expects it.
	 * @param value The value.
	 * @throws IllegalArgumentException If the name or the value is null.
	 */
	public final void setDriverProperty(String name, String value) {
		checkNotNull(name, "Driver property name");
		checkNotNull(value, "Driver property value");
		driverProps.put(name, value);
	}
	/**
	 * Removes a driver property, for instance an adapter default not wanted.
	 *
	 * @param name The property name.
	 */
	public final void removeDriverProperty(String name) {
		driverProps.remove(name);
	}
	/**
	 * @param name The property name.
	 * @return The value of the driver property, or null if not set.
	 */
	public final String getDriverProperty(String name) {
		return driverProps.getString(name, null);
	}

	/**
	 * @return A suitable pooled connection.
	 * @throws SQLException If an error occurs while getting the connection.
	 * @throws IllegalStateException If connectTo has not been called.
	 */
	public DBConnection getConnection() throws SQLException {
		return pool.getConnection();
	}

	/**
	 * @return The schema for strict application tables.
	 */
	public String getSchemaDat() {
		return props.getString(PROP_SCHEMA_DAT, null);
	}
	/**
	 * @return The schema for system tables.
	 */
	public String getSchemaSys() {
		return props.getString(PROP_SCHEMA_SYS, null);
	}
	/**
	 * @return The schema for strict temporary tables.
	 */
	public String getSchemaTmp() {
		return props.getString(PROP_SCHEMA_TMP, null);
	}

	/**
	 * Opens a physical connection, passing the user, the password and the driver
	 * properties to the driver. Used by the pool.
	 *
	 * @return The new JDBC connection.
	 * @throws SQLException If an error occurs.
	 * @throws IllegalStateException If connectTo has not been called.
	 */
	private Connection openConnection() throws SQLException {
		String url = props.getString(PROP_URL, null);
		if (url == null) {
			throw new IllegalStateException("Connection settings not set, call connectTo first");
		}
		java.util.Properties info = new java.util.Properties();
		for (Object name : driverProps.keys()) {
			info.setProperty((String) name, driverProps.getString(name));
		}
		String user = props.getString(PROP_USER, null);
		if (user != null) {
			info.setProperty(PROP_USER, user);
		}
		String password = props.getString(PROP_PASSWORD, null);
		if (password != null) {
			info.setProperty(PROP_PASSWORD, password);
		}
		return DriverManager.getConnection(url, info);
	}

	/**
	 * @param key   The key.
	 * @param value The value to put, or null to remove the key.
	 */
	private void putOrRemove(String key, String value) {
		if (value == null) {
			props.remove(key);
		} else {
			props.put(key, value);
		}
	}
	/**
	 * @param value The value.
	 * @param name  The name of the value, for the error message.
	 * @throws IllegalArgumentException If the value is null.
	 */
	private static void checkNotNull(Object value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " can not be null");
		}
	}
}
