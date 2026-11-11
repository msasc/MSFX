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

package msfx.lib.md;

import msfx.lib.util.Strings;
import msfx.lib.util.json.JSONObject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Data types supported and mapped within the supported database engine adapters.
 *
 * @author Miquel Sas
 */
public enum Types {
	BOOLEAN,
	DECIMAL,
	DOUBLE,
	INTEGER,
	LONG,
	DATE,
	TIME,
	DATETIME,
	STRING,
	BINARY,
	OBJECT;

	/**
	 * @return True if the type is a boolean, false otherwise.
	 */
	public boolean isBoolean() {
		return this == BOOLEAN;
	}
	/**
	 * @return True if the type is a decimal, false otherwise.
	 */
	public boolean isDecimal() {
		return this == DECIMAL;
	}
	/**
	 * @return True if the type is a double, false otherwise.
	 */
	public boolean isDouble() {
		return this == DOUBLE;
	}
	/**
	 * @return True if the type is an integer, false otherwise.
	 */
	public boolean isInteger() {
		return this == INTEGER;
	}
	/**
	 * @return True if the type is a long, false otherwise.
	 */
	public boolean isLong() {
		return this == LONG;
	}
	/**
	 * @return True if the type is a date, false otherwise.
	 */
	public boolean isDate() {
		return this == DATE;
	}
	/**
	 * @return True if the type is a time, false otherwise.
	 */
	public boolean isTime() {
		return this == TIME;
	}
	/**
	 * @return True if the type is a date-time, false otherwise.
	 */
	public boolean isDateTime() {
		return this == DATETIME;
	}
	/**
	 * @return True if the type is a string, false otherwise.
	 */
	public boolean isString() {
		return this == STRING;
	}
	/**
	 * @return True if the type is a binary, false otherwise.
	 */
	public boolean isBinary() {
		return this == BINARY;
	}
	/**
	 * @return True if the type is an object (JSONObject), false otherwise.
	 */
	public boolean isObject() {
		return this == OBJECT;
	}

	/**
	 * @return True if the type is nullable, false otherwise.
	 */
	public boolean isNullable() {
		boolean nullable = switch (this) {
			case DATE:
			case TIME:
			case DATETIME:
			case BINARY:
			case OBJECT:
				yield true;
			case BOOLEAN:
			case DECIMAL:
			case DOUBLE:
			case INTEGER:
			case LONG:
			case STRING:
				yield false;
		};
		return nullable;
	}
	/**
	 * @return True if the type is a number, false otherwise.
	 */
	public boolean isNumber() {
		boolean number = switch (this) {
			case DECIMAL:
			case DOUBLE:
			case INTEGER:
			case LONG:
				yield true;
			case BOOLEAN:
			case STRING:
			case DATE:
			case TIME:
			case DATETIME:
			case BINARY:
			case OBJECT:
				yield false;
		};
		return number;
	}

	/**
	 * @param v The value to accept.
	 * @return The accepted value.
	 * @throws NullPointerException     if the value is null.
	 * @throws IllegalArgumentException if the value is not compatible with this type.
	 */
	public Value accept(Value v) {
		if (v == null) {
			throw new NullPointerException("value can not be null");
		}
		if (isBoolean() && v.isBoolean()) {
			return v;
		}
		if (isNumber() && v.isNumber()) {
			if (isDecimal()) {
				return new Value(v.getDecimal());
			}
			if (isDouble()) {
				return new Value(v.getDouble());
			}
			if (isInteger()) {
				return new Value(v.getInteger());
			}
			if (isLong()) {
				return new Value(v.getLong());
			}
		}
		if (isString() && v.isString()) {
			return v;
		}
		if (isDate() && v.isDate()) {
			return v;
		}
		if (isTime() && v.isTime()) {
			return v;
		}
		if (isDateTime() && v.isDateTime()) {
			return v;
		}
		if (isBinary() && v.isBinary()) {
			return v;
		}
		if (isObject() && v.isObject()) {
			return v;
		}
		StringBuilder msg = new StringBuilder();
		msg.append("Invalid value for this type: ");
		msg.append(this);
		throw new IllegalArgumentException(msg.toString());
	}

	/**
	 * Returns the default value of a type, used when a column has no explicit
	 * default value:
	 * <ul>
	 * <li>BOOLEAN: false.</li>
	 * <li>DECIMAL: zero with the given number of decimals, e.g. 0.00 for 2.</li>
	 * <li>DOUBLE, INTEGER, LONG: zero.</li>
	 * <li>STRING: an empty string.</li>
	 * <li>DATE, TIME, DATETIME, BINARY, OBJECT: a null value of the type.</li>
	 * </ul>
	 * Numbers and booleans never default to null, so that they can be defined
	 * as NOT NULL. The nullable types default to null; for BINARY and OBJECT,
	 * normally stored as BLOB and CLOB, this avoids initializing large objects
	 * that have no content. A new value is returned on each call, so it can be
	 * modified freely.
	 *
	 * @param type     The type.
	 * @param decimals The number of decimals, used only for DECIMAL. Zero or
	 *                 negative means no decimals.
	 * @return The default value.
	 * @throws NullPointerException If the type is null.
	 */
	public static Value getDefaultValue(Types type, int decimals) {
		Value value = switch (type) {
			case BOOLEAN:
				yield new Value(false);
			case DECIMAL:
				yield new Value(new BigDecimal(Strings.defaultStringNumber(decimals)));
			case DOUBLE:
				yield new Value((double) 0);
			case INTEGER:
				yield new Value(0);
			case LONG:
				yield new Value((long) 0);
			case DATE:
				yield new Value((LocalDate) null);
			case TIME:
				yield new Value((LocalTime) null);
			case DATETIME:
				yield new Value((LocalDateTime) null);
			case STRING:
				yield new Value("");
			case BINARY:
				yield new Value((byte[]) null);
			case OBJECT:
				yield new Value((JSONObject) null);
		};
		return value;
	}

}
