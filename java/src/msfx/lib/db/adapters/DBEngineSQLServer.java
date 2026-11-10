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

package msfx.lib.db.adapters;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

import msfx.lib.db.DBEngine;
import msfx.lib.md.Column;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import msfx.lib.md.Value;

/**
 * SQL Server adapter, for the Microsoft JDBC driver (mssql-jdbc).
 * <p>
 * The connection string is the part of the URL after "jdbc:sqlserver://", for
 * instance "host:1433;databaseName=name".
 * <p>
 * Type mappings: BOOLEAN as VARCHAR(1), DECIMAL as NUMERIC(p,s), DOUBLE as
 * FLOAT(53), LONG as BIGINT, INTEGER as INT, DATE as DATE, TIME as TIME(7),
 * DATETIME as DATETIME2(7), STRING as NVARCHAR(n) or NVARCHAR(MAX), BINARY as
 * VARBINARY(n) or VARBINARY(MAX), and OBJECT as NVARCHAR(MAX). A length of -1,
 * or greater than the limit of the variable type, maps to the MAX variant.
 * <p>
 * Strings are NVARCHAR, so string parameters are sent as Unicode, the driver
 * default. Do not set sendStringParametersAsUnicode to false: parameters would
 * be sent in the database code page, and characters out of it would be stored
 * as '?'.
 *
 * @author Miquel Sas
 */
public class DBEngineSQLServer extends DBEngine {

	/**
	 * Maximum length of NVARCHAR(n), in characters (byte-pairs). Longer strings
	 * go to NVARCHAR(MAX).
	 */
	private static final int MAX_NVARCHAR = 4000;
	/**
	 * Maximum length of VARBINARY(n), in bytes. Longer binaries go to
	 * VARBINARY(MAX).
	 */
	private static final int MAX_VARBINARY = 8000;
	/**
	 * Format of DATETIME literals, accepted by DATETIME2 whatever the language
	 * settings of the session.
	 */
	private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSS");

	/**
	 * Constructor. Sets the default driver properties, which can be changed with
	 * {@link #setDriverProperty(String, String)} before connecting.
	 */
	public DBEngineSQLServer() {
		super(Adapter.SQLSERVER);
		/*
		 * Default properties to enhance performance.
		 */
		setDriverProperty("encrypt", "false");
		setDriverProperty("trustServerCertificate", "false");
		setDriverProperty("useBulkCopyForBatchInsert", "true");
		// Keep idle connections alive, needed for instance in Azure, whose timeouts
		// cut idle connections. Tested against SQL Server on Azure IaaS.
		setDriverProperty("tcpKeepAlive", "true");
		setDriverProperty("KeepAliveTime", "1000");
		setDriverProperty("KeepAliveInterval", "1000");
		setDriverProperty("responseBuffering", "adaptive");
	}

	/**
	 * Quotes a name with square brackets, which SQL Server always accepts,
	 * whatever the QUOTED_IDENTIFIER setting of the session. A closing bracket
	 * in the name is escaped by doubling it.
	 *
	 * @param str The name.
	 * @return The name between square brackets.
	 * @throws IllegalArgumentException If the name is null.
	 */
	@Override
	public String quote(String str) {
		if (str == null) {
			throw new IllegalArgumentException("Name can not be null");
		}
		StringBuilder name = new StringBuilder();
		name.append("[");
		name.append(str.replace("]", "]]"));
		name.append("]");
		return name.toString();
	}

	/**
	 * SQL Server stores empty strings, so for strings the condition also accepts
	 * them. Comparisons ignore trailing spaces, so strings of spaces are accepted
	 * too. The expression is not wrapped in functions, so indexes can be used.
	 * The expression is written twice for strings.
	 */
	@Override
	public String isNull(String expr, Types type) {
		StringBuilder cond = new StringBuilder();
		if (type == Types.STRING) {
			cond.append("(");
			cond.append(expr);
			cond.append(" IS NULL OR ");
			cond.append(expr);
			cond.append(" = '')");
		} else {
			cond.append(expr);
			cond.append(" IS NULL");
		}
		return cond.toString();
	}

	/**
	 * For strings, the condition also rejects empty strings and strings of
	 * spaces. A null compared with anything is never true, so the comparison
	 * alone rejects nulls too.
	 */
	@Override
	public String isNotNull(String expr, Types type) {
		StringBuilder cond = new StringBuilder();
		cond.append(expr);
		if (type == Types.STRING) {
			cond.append(" <> ''");
		} else {
			cond.append(" IS NOT NULL");
		}
		return cond.toString();
	}

	@Override
	protected String getURL(String cnString) {
		StringBuilder url = new StringBuilder();
		url.append("jdbc:sqlserver://");
		url.append(cnString);
		return url.toString();
	}

	/**
	 * The default value is written as a named constraint, DF_table_column, so that
	 * it can be dropped by name before dropping the column: SQL Server does not
	 * drop a column that has a default constraint, and would otherwise give it a
	 * generated name.
	 */
	@Override
	public String getColumnDefSuffix(Column col) {
		StringBuilder suffix = new StringBuilder();
		Value value = col.getDefaultValue();
		if (value != null && !value.isNull()) {
			Table table = col.getTable();
			if (table == null) {
				StringBuilder msg = new StringBuilder();
				msg.append("Column ");
				msg.append(col.getName());
				msg.append(" does not belong to a table");
				throw new IllegalArgumentException(msg.toString());
			}
			suffix.append("CONSTRAINT ");
			suffix.append(quote(getDefaultConstraintName(table.getName(), col.getName())));
			suffix.append(" DEFAULT ");
			suffix.append(literal(col.getType(), value));
		}
		if (!col.isNullable()) {
			if (!suffix.isEmpty()) {
				suffix.append(" ");
			}
			suffix.append("NOT NULL");
		}
		return suffix.toString();
	}

	/**
	 * Returns the name of the default constraint of a column, DF_table_column, the
	 * one shared rule to create and to drop it.
	 *
	 * @param table  The name of the table.
	 * @param column The name of the column.
	 * @return The name of the default constraint, not quoted.
	 */
	public static String getDefaultConstraintName(String table, String column) {
		StringBuilder name = new StringBuilder();
		name.append("DF_");
		name.append(table);
		name.append("_");
		name.append(column);
		return name.toString();
	}

	/**
	 * @param type  The type of the column.
	 * @param value The value, not null.
	 * @return The value as an SQL literal. Booleans are stored as '1' and '0',
	 *         as the driver converts them.
	 */
	private static String literal(Types type, Value value) {
		StringBuilder literal = new StringBuilder();
		switch (type) {
			case BOOLEAN -> literal.append(value.getBoolean() ? "'1'" : "'0'");
			case DECIMAL -> literal.append(value.getDecimal().toPlainString());
			case DOUBLE -> literal.append(BigDecimal.valueOf(value.getDouble()).toPlainString());
			case INTEGER, LONG -> literal.append(value.getLong());
			case STRING -> {
				literal.append("N'");
				literal.append(value.getString().replace("'", "''"));
				literal.append("'");
			}
			case DATE -> {
				literal.append("'");
				literal.append(value.getDate());
				literal.append("'");
			}
			case TIME -> {
				literal.append("'");
				literal.append(value.getTime());
				literal.append("'");
			}
			case DATETIME -> {
				literal.append("'");
				literal.append(DATETIME.format(value.getDateTime()));
				literal.append("'");
			}
			case BINARY, OBJECT -> throw new UnsupportedOperationException("Not implemented yet");
		}
		return literal.toString();
	}

	@Override
	public String getColumnType(Column col) {
		Types type = col.getType();
		StringBuilder def = new StringBuilder();
		switch (type) {
			case BOOLEAN:
				def.append("VARCHAR(1)");
				break;
			case DECIMAL:
				def.append("NUMERIC(");
				def.append(col.getLength());
				def.append(",");
				def.append(col.getDecimals());
				def.append(")");
				break;
			case DOUBLE:
				def.append("FLOAT(53)");
				break;
			case LONG:
				def.append("BIGINT");
				break;
			case INTEGER:
				def.append("INT");
				break;
			case DATE:
				def.append("DATE");
				break;
			case TIME:
				def.append("TIME(7)");
				break;
			case DATETIME:
				def.append("DATETIME2(7)");
				break;
			case STRING:
				def.append("NVARCHAR(");
				if (col.getLength() > 0 && col.getLength() <= MAX_NVARCHAR) {
					def.append(col.getLength());
				} else {
					def.append("MAX");
				}
				def.append(")");
				break;
			case BINARY:
				def.append("VARBINARY(");
				if (col.getLength() > 0 && col.getLength() <= MAX_VARBINARY) {
					def.append(col.getLength());
				} else {
					def.append("MAX");
				}
				def.append(")");
				break;
			case OBJECT:
				def.append("NVARCHAR(MAX)");
				break;
		}
		return def.toString();
	}
}
