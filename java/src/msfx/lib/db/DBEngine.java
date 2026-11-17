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
import msfx.lib.db.query.sa.Function;
import msfx.lib.md.Catalog;
import msfx.lib.md.Column;
import msfx.lib.md.DataType;
import msfx.lib.md.Types;
import msfx.lib.md.Value;
import msfx.lib.util.Numbers;
import msfx.lib.util.Properties;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Database engine that represents the back-end system to which we connect through a JDBC driver.
 * <p>
 * The engine has a list of schemas, defined by the application with {@link #addSchema(String)}, all
 * in the connected database, so their tables can be joined. The order of the list is the order in
 * which tables are searched by name.
 * <p>
 * The engine has a {@link Catalog} of the domains and tables of its schemas. The adapter installs
 * its providers when instantiated, and the application may add its own.
 * <p>
 * The engine keeps two groups of settings:
 * <ul>
 * <li>The connection settings (URL, user, password), set by
 * {@link #connectTo(String, String, String)} and only used internally to open connections.</li>
 * <li>The driver properties, any additional property the JDBC driver accepts. Each adapter sets its
 * defaults, and the application can change them with {@link #setDriverProperty(String, String)}
 * and {@link #removeDriverProperty(String)}. They take effect when a physical connection is opened,
 * so changes made once connections exist only affect new ones.</li>
 * </ul>
 *
 * @author Miquel Sas
 */
public abstract class DBEngine {

	/**
	 * Adapters implemented, one per supported database. Each adapter passes its
	 * constant to the constructor, so that queries can check the database when its
	 * syntax differs, without relying on instanceof.
	 */
	public enum Adapter {
		/** Oracle, implemented by DBEngineOracle. */
		ORACLE,
		/** SQL Server, implemented by DBEngineSQLServer. */
		SQLSERVER
	}

	/**
	 * Key of the JDBC URL. Not passed to the driver as a property.
	 */
	private static final String PROP_URL = "url";
	/**
	 * Key of the user, also the property name the drivers expect.
	 */
	private static final String PROP_USER = "user";
	/**
	 * Key of the password, also the property name the drivers expect.
	 */
	private static final String PROP_PASSWORD = "password";

	/**
	 * Adapter of this engine.
	 */
	private final Adapter adapter;
	/**
	 * Connection settings.
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
	 * Schemas, in the order tables are searched.
	 */
	private final List<String> schemas = new ArrayList<>();
	/**
	 * Lock that guards the schemas.
	 */
	private final ReentrantLock lockSchemas = new ReentrantLock();
	/**
	 * Catalog of the domains and tables of the schemas.
	 */
	private final Catalog catalog = new Catalog();
	/**
	 * Key of the database the engine is connected to, adapter, host, port and
	 * database in upper case, or null before {@link #connectTo(String, String,
	 * String)}.
	 */
	private String databaseKey;

	/**
	 * Constructor. Internally initializes the connection pool.
	 *
	 * @param adapter The adapter, set by each adapter implementation.
	 * @throws IllegalArgumentException If the adapter is null.
	 */
	@SuppressWarnings("this-escape")
	protected DBEngine(Adapter adapter) {
		checkNotNull(adapter, "Adapter");
		this.adapter = adapter;
		// The pool only keeps the reference, it opens connections when requested.
		pool = new ConnectionPool(this::openConnection);
	}

	/**
	 * @return The adapter of this engine, to check the database when its syntax
	 *         differs.
	 */
	public final Adapter getAdapter() {
		return adapter;
	}

	/**
	 * @param cnString The connection string, the engine specific part of the URL.
	 * @return The JDBC URL.
	 */
	protected abstract String getURL(String cnString);

	/**
	 * Returns the key of the database a connection string refers to:
	 * ADAPTER:HOST:PORT:DATABASE in upper case, without the user nor the driver
	 * options, since they change how the database is accessed, not which one.
	 *
	 * @param cnString The connection string, the engine specific part of the URL.
	 * @return The key.
	 * @throws IllegalArgumentException If the connection string does not name
	 *                                  the host and the database in a form the
	 *                                  adapter can read.
	 */
	protected abstract String getDatabaseKey(String cnString);

	/**
	 * Sets the connection settings, once: an engine is connected to one
	 * database. Connections are opened later, when requested. The key of the
	 * database is computed and given to the catalog, which sets it to the tables
	 * it returns.
	 *
	 * @param cnString The connection string, the engine specific part of the URL.
	 * @param user     The user, or null if the driver does not need it, for instance
	 *                 with integrated security.
	 * @param password The password, or null if the driver does not need it.
	 * @throws IllegalArgumentException If the connection string is null, or the
	 *                                  adapter can not read the host and the
	 *                                  database from it.
	 * @throws IllegalStateException    If the engine is already connected.
	 */
	public final void connectTo(String cnString, String user, String password) {
		if (databaseKey != null) {
			throw new IllegalStateException("The engine is already connected, connectTo is called once");
		}
		checkNotNull(cnString, "Connection string");
		String key = getDatabaseKey(cnString);
		props.put(PROP_URL, getURL(cnString));
		putOrRemove(PROP_USER, user);
		putOrRemove(PROP_PASSWORD, password);
		databaseKey = key;
		catalog.setDatabaseKey(key);
	}

	/**
	 * @return The key of the database the engine is connected to,
	 *         ADAPTER:HOST:PORT:DATABASE in upper case, or null if not connected
	 *         yet.
	 */
	public final String getDatabaseKey() {
		return databaseKey;
	}

	/**
	 * Adds a schema, searched after the ones already added. All the schemas are
	 * in the connected database, so their tables can be joined.
	 *
	 * @param schema The schema name.
	 * @throws IllegalArgumentException If the schema is null, or a schema that
	 *                                  differs only in case was already added.
	 */
	public final void addSchema(String schema) {
		checkNotNull(schema, "Schema");
		lockSchemas.lock();
		try {
			for (String s : schemas) {
				if (s.equalsIgnoreCase(schema)) {
					StringBuilder msg = new StringBuilder();
					msg.append("Duplicate schema ");
					msg.append(schema);
					throw new IllegalArgumentException(msg.toString());
				}
			}
			schemas.add(schema);
		} finally {
			lockSchemas.unlock();
		}
	}
	/**
	 * @return A copy of the schemas, in the order tables are searched.
	 */
	public final List<String> getSchemas() {
		lockSchemas.lock();
		try {
			return List.copyOf(schemas);
		} finally {
			lockSchemas.unlock();
		}
	}

	/**
	 * @return The catalog of the domains and tables of the schemas, where the
	 *         adapter and the application install their providers.
	 */
	public final Catalog getCatalog() {
		return catalog;
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
	 * @throws IllegalStateException If connectTo has not been called, or the
	 *                               engine is closed.
	 */
	public DBConnection getConnection() throws SQLException {
		return pool.getConnection();
	}

	/**
	 * Sets a parameter of a prepared statement with the setter of the type of
	 * the value, or to NULL of that type for a null value. Used by the query
	 * classes and by any SQL written by hand with placeholders.
	 *
	 * @param ps    The prepared statement.
	 * @param index The parameter index, starting at 1.
	 * @param value The value.
	 * @throws SQLException If a database access error occurs.
	 */
	public final void setValue(DBPreparedStatement ps, int index, Value value) throws SQLException {
		if (value.isNull()) {
			ps.setNull(index, value.getType());
			return;
		}
		switch (value.getType()) {
			case BOOLEAN:
				ps.setBoolean(index, value.getBoolean());
				break;
			case DECIMAL:
				ps.setDecimal(index, value.getDecimal());
				break;
			case DOUBLE:
				ps.setDouble(index, value.getDouble());
				break;
			case INTEGER:
				ps.setInteger(index, value.getInteger());
				break;
			case LONG:
				ps.setLong(index, value.getLong());
				break;
			case DATE:
				ps.setDate(index, value.getDate());
				break;
			case TIME:
				ps.setTime(index, value.getTime());
				break;
			case DATETIME:
				ps.setDateTime(index, value.getDateTime());
				break;
			case STRING:
				ps.setString(index, value.getString());
				break;
			case BINARY:
				ps.setBinary(index, value.getBinary());
				break;
			case OBJECT:
				ps.setObject(index, value.getObject());
				break;
		}
	}

	/**
	 * Reads a column of the current row of a result set as a value of a data
	 * type, with the getter of the type, the driver converting the type of the
	 * database. The value is not modified:
	 * <ul>
	 * <li>DECIMAL values get the decimals of the data type, rounded half up.</li>
	 * <li>Null numbers and booleans are zero and false, null strings are
	 * empty.</li>
	 * <li>Booleans are read from 1/0, Y/N or T/F, and objects parsed from their
	 * JSON text.</li>
	 * </ul>
	 * Used by the query classes and by any SQL written by hand. Each column is
	 * read once, streamed columns can not be read again.
	 *
	 * @param rs       The result set, positioned on the row.
	 * @param index    The column index, starting at 1.
	 * @param dataType The data type of the value.
	 * @return The value.
	 * @throws SQLException If a database access error occurs, or an object is
	 *                      not valid JSON.
	 */
	public final Value getValue(DBResultSet rs, int index, DataType dataType) throws SQLException {
		Value value = switch (dataType.type()) {
			case BOOLEAN:
				yield new Value(rs.getBoolean(index));
			case DECIMAL:
				yield new Value(Numbers.toScale(rs.getDecimal(index), dataType.decimals()));
			case DOUBLE:
				yield new Value(rs.getDouble(index));
			case INTEGER:
				yield new Value(rs.getInteger(index));
			case LONG:
				yield new Value(rs.getLong(index));
			case DATE:
				yield new Value(rs.getDate(index));
			case TIME:
				yield new Value(rs.getTime(index));
			case DATETIME:
				yield new Value(rs.getDateTime(index));
			case STRING: {
				String string = rs.getString(index);
				yield new Value(string != null ? string : "");
			}
			case BINARY:
				yield new Value(rs.getBinary(index));
			case OBJECT:
				yield new Value(rs.getObject(index));
		};
		return value;
	}

	/**
	 * Closes the engine, to be called when the application ends: closes the free
	 * connections of the pool, and the connections in use when their users close
	 * them. Once closed, the engine gives no more connections. Closing a closed
	 * engine has no effect.
	 */
	public void close() {
		pool.close();
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
			StringBuilder msg = new StringBuilder();
			msg.append(name);
			msg.append(" can not be null");
			throw new IllegalArgumentException(msg.toString());
		}
	}

	/**
	 * @param col The column.
	 * @return The database type of the column, like NVARCHAR(40), without the
	 *         other properties of its definition.
	 * @see #getColumnDefSuffix(Column)
	 */
	public abstract String getColumnType(Column col);

	/**
	 * Returns the properties that follow the type in the definition of a column,
	 * when creating a table or adding a column: the default value and NOT NULL,
	 * in this order, separated by a space. The default value is written only when
	 * it is not null, and NOT NULL only when the column is not nullable, except
	 * where the database does not allow it, like empty strings in Oracle.
	 * <p>
	 * When adding a column to a table with rows, the existing rows get the
	 * default value.
	 *
	 * @param col The column, that must belong to a table.
	 * @return The properties, or an empty string if there are none.
	 * @throws IllegalArgumentException If the database needs the table of the
	 *                                  column and the column has none.
	 */
	public abstract String getColumnDefSuffix(Column col);

	/**
	 * Quotes a name, so that it is not confused with a reserved word and can
	 * contain any character the database accepts. Names are always quoted, so
	 * they must have exactly the case the database stores: quoted names are
	 * case-sensitive in databases like Oracle, which store unquoted names in upper
	 * case.
	 * <p>
	 * Quotes a single part: a qualified name, like schema.table or alias.column,
	 * is built by quoting each part and joining them with a dot.
	 *
	 * @param str The name of a schema, table, column or index, or an alias.
	 * @return The name properly quoted for this database.
	 * @throws IllegalArgumentException If the name is null, or has characters
	 *                                  this database does not accept in a quoted
	 *                                  name.
	 */
	public abstract String quote(String str);

	/**
	 * Returns the condition that checks whether an expression is null. Databases
	 * like Oracle store empty strings as null, so for strings the condition also
	 * accepts empty strings in databases that do not, and queries give the same
	 * results in all of them.
	 *
	 * @param expr The expression, a quoted column name or any SQL expression. It
	 *             is written as is, so it must never come from user input.
	 * @param type The type of the expression.
	 * @return The condition, that can be combined with AND and OR as is.
	 */
	public abstract String isNull(String expr, Types type);

	/**
	 * Returns the condition that checks whether an expression is not null, the
	 * opposite of {@link #isNull(String, Types)}: for strings, the condition also
	 * rejects empty strings.
	 *
	 * @param expr The expression, a quoted column name or any SQL expression. It
	 *             is written as is, so it must never come from user input.
	 * @param type The type of the expression.
	 * @return The condition, that can be combined with AND and OR as is.
	 */
	public abstract String isNotNull(String expr, Types type);

	/**
	 * Returns a value written as an SQL literal of this database, calling the
	 * method of its type.
	 *
	 * @param value The value.
	 * @return The literal.
	 * @throws IllegalArgumentException      If the value is null: a null has no
	 *                                       literal, and comparing with it never
	 *                                       matches; use IS NULL.
	 * @throws UnsupportedOperationException If the value is a binary or a JSON
	 *                                       object.
	 */
	public final String getLiteralSQL(Value value) {
		if (value == null || value.isNull()) {
			throw new IllegalArgumentException("A null value has no literal, use IS NULL");
		}
		String literal = switch (value.getType()) {
			case BOOLEAN:
				yield getLiteralSQL(value.getBoolean());
			case DECIMAL:
				yield getLiteralSQL(value.getDecimal());
			case DOUBLE:
				yield getLiteralSQL(value.getDouble());
			case INTEGER:
			case LONG:
				yield getLiteralSQL(value.getLong());
			case DATE:
				yield getLiteralSQL(value.getDate());
			case TIME:
				yield getLiteralSQL(value.getTime());
			case DATETIME:
				yield getLiteralSQL(value.getDateTime());
			case STRING:
				yield getLiteralSQL(value.getString());
			case BINARY:
			case OBJECT:
				throw new UnsupportedOperationException("Not implemented yet");
		};
		return literal;
	}

	/**
	 * Returns an integer number as an SQL literal, the same in every database.
	 *
	 * @param number The number.
	 * @return The literal.
	 */
	public final String getLiteralSQL(long number) {
		return Long.toString(number);
	}

	/**
	 * Returns a decimal number as an SQL literal, without exponent, the same in
	 * every database.
	 *
	 * @param number The number.
	 * @return The literal.
	 */
	public final String getLiteralSQL(BigDecimal number) {
		return number.toPlainString();
	}

	/**
	 * Returns a double as an SQL literal, without exponent, the same in every
	 * database.
	 *
	 * @param number The number.
	 * @return The literal.
	 */
	public final String getLiteralSQL(double number) {
		return BigDecimal.valueOf(number).toPlainString();
	}

	/**
	 * Returns a boolean as an SQL literal, as it is stored by this database.
	 *
	 * @param bool The boolean.
	 * @return The literal.
	 */
	public abstract String getLiteralSQL(boolean bool);

	/**
	 * Returns a string as an SQL literal of this database, with the quotes it
	 * contains escaped.
	 *
	 * @param str The string, not null.
	 * @return The literal.
	 */
	public abstract String getLiteralSQL(String str);

	/**
	 * Returns a date as an SQL literal of this database.
	 *
	 * @param date The date.
	 * @return The literal.
	 */
	public abstract String getLiteralSQL(LocalDate date);

	/**
	 * Returns a time as an SQL literal of this database.
	 *
	 * @param time The time.
	 * @return The literal.
	 * @throws UnsupportedOperationException If the database has no time type.
	 */
	public abstract String getLiteralSQL(LocalTime time);

	/**
	 * Returns a date and time as an SQL literal of this database.
	 *
	 * @param dateTime The date and time.
	 * @return The literal.
	 */
	public abstract String getLiteralSQL(LocalDateTime dateTime);

	/**
	 * Returns the concatenation of two or more strings in the form of this
	 * database. A null operand is taken as an empty string, as Oracle does.
	 *
	 * @param operands The operands, already written in SQL.
	 * @return The SQL expression, enclosed in parentheses or written as a
	 *         function call, so that it can be used as is in any expression.
	 */
	public abstract String getConcatSQL(List<String> operands);

	/**
	 * Returns the maximum number of columns of a SELECT in this database, checked
	 * when columns are added to a select.
	 *
	 * @return The maximum number of columns.
	 */
	public abstract int getMaxSelectColumns();

	/**
	 * Returns a function call in the form of this database, with the behavior
	 * described in {@link Function}.
	 * <p>
	 * The arguments are already written in SQL and their number has already been
	 * validated. The single argument of an aggregate may be <code>*</code> or
	 * start with <code>DISTINCT</code>, as in COUNT(*) or COUNT(DISTINCT x).
	 * Translations written with operators are enclosed in parentheses, as are the
	 * arguments they use as operands, so the result can be used as is in any
	 * expression.
	 *
	 * @param function  The function.
	 * @param arguments The arguments, written in SQL.
	 * @return The SQL expression.
	 */
	public abstract String getFunctionSQL(Function function, List<String> arguments);

	/**
	 * Returns a function call written as name(argument, ...).
	 *
	 * @param name      The name of the function in this database.
	 * @param arguments The arguments, written in SQL.
	 * @return The SQL expression.
	 */
	protected final String getCallSQL(String name, List<String> arguments) {
		StringBuilder call = new StringBuilder();
		call.append(name);
		call.append("(");
		for (int i = 0; i < arguments.size(); i++) {
			if (i > 0) {
				call.append(", ");
			}
			call.append(arguments.get(i));
		}
		call.append(")");
		return call.toString();
	}

	/**
	 * Returns an expression enclosed in parentheses, to be used as the operand of
	 * an operator whatever its own operators are.
	 *
	 * @param expr The expression, written in SQL.
	 * @return The expression enclosed in parentheses.
	 */
	protected final String getOperandSQL(String expr) {
		StringBuilder operand = new StringBuilder();
		operand.append("(");
		operand.append(expr);
		operand.append(")");
		return operand.toString();
	}
}
