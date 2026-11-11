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
import java.util.List;

import msfx.lib.db.DBEngine;
import msfx.lib.db.query.sa.Function;
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

	/**
	 * Translations that differ from the name of the function:
	 * <ul>
	 * <li>LENGTH: COALESCE(LENGTH(s), 0), because empty strings are null.</li>
	 * <li>SUBSTRING: SUBSTR. LEFT(s, n): SUBSTR(s, 1, n).</li>
	 * <li>RIGHT(s, n): SUBSTR(s, GREATEST(LENGTH(s) - (n) + 1, 1)), because
	 * SUBSTR(s, -n) gives null when n is greater than the length.</li>
	 * <li>POSITION: INSTR. CEILING: CEIL. TRUNCATE: TRUNC.</li>
	 * <li>CURRENT_DATE: TRUNC(SYSDATE). CURRENT_TIMESTAMP: SYSTIMESTAMP.</li>
	 * <li>YEAR, MONTH, DAY: EXTRACT. HOUR, MINUTE, SECOND: EXTRACT from the value
	 * cast to TIMESTAMP, seconds without fraction.</li>
	 * <li>ADD_DAYS(d, n): ((d) + NUMTODSINTERVAL(n, 'DAY')), which keeps the
	 * fraction of timestamps.</li>
	 * <li>DAYS_BETWEEN(d1, d2): (TRUNC(d2) - TRUNC(d1)).</li>
	 * <li>TO_DATE: with the ISO format.</li>
	 * <li>TO_TIMESTAMP: TO_TIMESTAMP(REPLACE(s, 'T', ' '), 'YYYY-MM-DD
	 * HH24:MI:SS.FF'). Trailing elements missing in the string, like the
	 * seconds, are taken as zero.</li>
	 * </ul>
	 * RIGHT writes its string argument twice.
	 */
	@Override
	public String getFunctionSQL(Function function, List<String> arguments) {
		String sql = switch (function) {
			case COUNT:
			case SUM:
			case AVG:
			case MIN:
			case MAX:
			case UPPER:
			case LOWER:
			case TRIM:
			case LTRIM:
			case RTRIM:
			case REPLACE:
			case LPAD:
			case RPAD:
			case ABS:
			case SIGN:
			case SQRT:
			case FLOOR:
			case ROUND:
			case MOD:
			case POWER:
			case COALESCE:
			case NULLIF:
			case ADD_MONTHS:
			case TO_NUMBER:
				yield getCallSQL(function.name(), arguments);
			case LENGTH:
				yield getCallSQL("COALESCE", List.of(getCallSQL("LENGTH", arguments), "0"));
			case SUBSTRING:
				yield getCallSQL("SUBSTR", arguments);
			case LEFT:
				yield getCallSQL("SUBSTR", List.of(arguments.get(0), "1", arguments.get(1)));
			case RIGHT: {
				String s = arguments.get(0);
				StringBuilder start = new StringBuilder();
				start.append(getCallSQL("LENGTH", List.of(s)));
				start.append(" - ");
				start.append(getOperandSQL(arguments.get(1)));
				start.append(" + 1");
				yield getCallSQL("SUBSTR", List.of(s, getCallSQL("GREATEST", List.of(start.toString(), "1"))));
			}
			case POSITION:
				yield getCallSQL("INSTR", arguments);
			case CEILING:
				yield getCallSQL("CEIL", arguments);
			case TRUNCATE:
				yield getCallSQL("TRUNC", arguments);
			case CURRENT_DATE:
				yield "TRUNC(SYSDATE)";
			case CURRENT_TIMESTAMP:
				yield "SYSTIMESTAMP";
			case YEAR:
			case MONTH:
			case DAY:
				yield getExtractSQL(function.name(), arguments.get(0));
			case HOUR:
			case MINUTE:
				yield getExtractSQL(function.name(), getTimestampSQL(arguments.get(0)));
			case SECOND:
				yield getCallSQL("FLOOR", List.of(getExtractSQL("SECOND", getTimestampSQL(arguments.get(0)))));
			case ADD_DAYS: {
				StringBuilder b = new StringBuilder();
				b.append("(");
				b.append(getOperandSQL(arguments.get(0)));
				b.append(" + ");
				b.append(getCallSQL("NUMTODSINTERVAL", List.of(arguments.get(1), "'DAY'")));
				b.append(")");
				yield b.toString();
			}
			case DAYS_BETWEEN: {
				StringBuilder b = new StringBuilder();
				b.append("(");
				b.append(getCallSQL("TRUNC", List.of(arguments.get(1))));
				b.append(" - ");
				b.append(getCallSQL("TRUNC", List.of(arguments.get(0))));
				b.append(")");
				yield b.toString();
			}
			case TO_STRING:
				throw new UnsupportedOperationException("Not implemented yet");
			case TO_DATE:
				yield getCallSQL("TO_DATE", List.of(arguments.get(0), "'YYYY-MM-DD'"));
			case TO_TIMESTAMP:
				yield getCallSQL("TO_TIMESTAMP", List.of(getCallSQL("REPLACE", List.of(arguments.get(0), "'T'", "' '")), "'YYYY-MM-DD HH24:MI:SS.FF'"));
		};
		return sql;
	}

	/**
	 * @param field The field: YEAR, MONTH, DAY, HOUR, MINUTE or SECOND.
	 * @param expr  The date or timestamp expression.
	 * @return EXTRACT(field FROM expr).
	 */
	private static String getExtractSQL(String field, String expr) {
		StringBuilder b = new StringBuilder();
		b.append("EXTRACT(");
		b.append(field);
		b.append(" FROM ");
		b.append(expr);
		b.append(")");
		return b.toString();
	}

	/**
	 * @param expr The date or timestamp expression.
	 * @return CAST(expr AS TIMESTAMP), needed to extract hours, minutes and
	 *         seconds from dates.
	 */
	private static String getTimestampSQL(String expr) {
		StringBuilder b = new StringBuilder();
		b.append("CAST(");
		b.append(expr);
		b.append(" AS TIMESTAMP)");
		return b.toString();
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
			case BOOLEAN:
				literal.append(value.getBoolean() ? "'1'" : "'0'");
				break;
			case DECIMAL:
				literal.append(value.getDecimal().toPlainString());
				break;
			case DOUBLE:
				literal.append(BigDecimal.valueOf(value.getDouble()).toPlainString());
				break;
			case INTEGER:
			case LONG:
				literal.append(value.getLong());
				break;
			case STRING:
				literal.append("N'");
				literal.append(value.getString().replace("'", "''"));
				literal.append("'");
				break;
			case DATE:
				literal.append("DATE '");
				literal.append(value.getDate());
				literal.append("'");
				break;
			case TIME:
				throw new UnsupportedOperationException("TIME columns not yet supported in Oracle");
			case DATETIME:
				literal.append("TIMESTAMP '");
				literal.append(DATETIME.format(value.getDateTime()));
				literal.append("'");
				break;
			case BINARY:
			case OBJECT:
				throw new UnsupportedOperationException("Not implemented yet");
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
