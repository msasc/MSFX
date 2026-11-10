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
import msfx.lib.md.Types;
import msfx.lib.md.Value;

/**
 * Oracle adapter, for the Oracle thin JDBC driver (ojdbc11).
 * <p>
 * The connection string is the part of the URL after "jdbc:oracle:thin:@", for
 * instance "//host:1521/service".
 * <p>
 * Type mappings: BOOLEAN as VARCHAR(1), DECIMAL as NUMBER(p,s), DOUBLE as
 * BINARY_DOUBLE, LONG and INTEGER as NUMBER(19,0) and NUMBER(10,0), DATE as
 * DATE, DATETIME as TIMESTAMP(7), STRING as NVARCHAR2(n) or CLOB, BINARY as
 * RAW(n) or BLOB, and OBJECT as CLOB. TIME is not supported yet, Oracle has no
 * TIME type. A length of -1, or greater than the limit of the variable type,
 * maps to CLOB or BLOB.
 *
 * @author Miquel Sas
 */
public class DBEngineOracle extends DBEngine {

	/**
	 * Maximum length of NVARCHAR2, in characters: 4000 bytes with the default
	 * MAX_STRING_SIZE = STANDARD, which is 2000 characters with the default
	 * national character set AL16UTF16. Longer strings go to CLOB.
	 */
	private static final int MAX_NVARCHAR2 = 2000;
	/**
	 * Maximum length of RAW, in bytes, with MAX_STRING_SIZE = STANDARD. Longer
	 * binaries go to BLOB.
	 */
	private static final int MAX_RAW = 2000;
	/**
	 * Format of TIMESTAMP literals.
	 */
	private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS");

	/**
	 * Constructor. Sets the default driver properties, which can be changed with
	 * {@link #setDriverProperty(String, String)} before connecting.
	 */
	public DBEngineOracle() {
		super(Adapter.ORACLE);
		/*
		 * Default driver properties to enhance performance.
		 */
		setDriverProperty("remarksReporting", "false");
	}

	/**
	 * Quotes a name with double quotes, the SQL standard. Oracle does not accept
	 * a double quote inside a quoted name, not even doubled, so such a name is
	 * rejected.
	 *
	 * @param str The name.
	 * @return The name between double quotes.
	 * @throws IllegalArgumentException If the name is null or contains a double
	 *                                  quote.
	 */
	@Override
	public String quote(String str) {
		if (str == null) {
			throw new IllegalArgumentException("Name can not be null");
		}
		if (str.indexOf('"') >= 0) {
			StringBuilder msg = new StringBuilder();
			msg.append("Oracle names can not contain double quotes: ");
			msg.append(str);
			throw new IllegalArgumentException(msg.toString());
		}
		StringBuilder name = new StringBuilder();
		name.append("\"");
		name.append(str);
		name.append("\"");
		return name.toString();
	}

	/**
	 * Oracle stores empty strings as null, so IS NULL already accepts them for
	 * all types. Strings of spaces are not null.
	 */
	@Override
	public String isNull(String expr, Types type) {
		StringBuilder cond = new StringBuilder();
		cond.append(expr);
		cond.append(" IS NULL");
		return cond.toString();
	}

	/**
	 * Oracle stores empty strings as null, so IS NOT NULL already rejects them
	 * for all types.
	 */
	@Override
	public String isNotNull(String expr, Types type) {
		StringBuilder cond = new StringBuilder();
		cond.append(expr);
		cond.append(" IS NOT NULL");
		return cond.toString();
	}

	@Override
	protected String getURL(String cnString) {
		StringBuilder url = new StringBuilder();
		url.append("jdbc:oracle:thin:@");
		url.append(cnString);
		return url.toString();
	}

	/**
	 * Oracle stores empty strings as null, so a STRING column never gets NOT NULL,
	 * and gets a default value only when it is not empty. Columns of a primary
	 * key are NOT NULL anyway, by the primary key constraint.
	 */
	@Override
	public String getColumnDefSuffix(Column col) {
		StringBuilder suffix = new StringBuilder();
		boolean string = (col.getType() == Types.STRING);
		Value value = col.getDefaultValue();
		if (value != null && !value.isNull() && !(string && value.getString().isEmpty())) {
			suffix.append("DEFAULT ");
			suffix.append(literal(col.getType(), value));
		}
		if (!col.isNullable() && !string) {
			if (!suffix.isEmpty()) {
				suffix.append(" ");
			}
			suffix.append("NOT NULL");
		}
		return suffix.toString();
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
				literal.append("DATE '");
				literal.append(value.getDate());
				literal.append("'");
			}
			case TIME -> throw new UnsupportedOperationException("TIME columns not yet supported in Oracle");
			case DATETIME -> {
				literal.append("TIMESTAMP '");
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
				def.append("NUMBER(");
				def.append(col.getLength());
				def.append(",");
				def.append(col.getDecimals());
				def.append(")");
				break;
			case DOUBLE:
				def.append("BINARY_DOUBLE");
				break;
			case LONG:
				def.append("NUMBER(19,0)");
				break;
			case INTEGER:
				def.append("NUMBER(10,0)");
				break;
			case DATE:
				def.append("DATE");
				break;
			case TIME:
				// Oracle has no TIME type, the mapping is pending a decision.
				throw new UnsupportedOperationException("TIME columns not yet supported in Oracle");
			case DATETIME:
				def.append("TIMESTAMP(7)");
				break;
			case STRING:
				if (col.getLength() > 0 && col.getLength() <= MAX_NVARCHAR2) {
					def.append("NVARCHAR2(");
					def.append(col.getLength());
					def.append(")");
				} else {
					def.append("CLOB");
				}
				break;
			case BINARY:
				if (col.getLength() > 0 && col.getLength() <= MAX_RAW) {
					def.append("RAW(");
					def.append(col.getLength());
					def.append(")");
				} else {
					def.append("BLOB");
				}
				break;
			case OBJECT:
				def.append("CLOB");
				break;
		}
		return def.toString();
	}
}
