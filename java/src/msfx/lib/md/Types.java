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
		return switch (this) {
			case DATE, TIME, DATETIME, STRING, BINARY, OBJECT -> true;
			default -> false;
		};
	}
	/**
	 * @return True if the type is a number, false otherwise.
	 */
	public boolean isNumber() {
		return switch (this) {
			case DECIMAL, DOUBLE, INTEGER, LONG -> true;
			default -> false;
		};
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
		throw new IllegalArgumentException("Invalid value for this type: " + this);
	}

	public static Value getDefaultValue(Types type, int decimals) {
		return switch (type) {
			case BOOLEAN -> new Value(false);
			case DECIMAL -> new Value(new BigDecimal(Strings.defaultStringNumber(decimals)));
			case DOUBLE -> new Value((double) 0);
			case INTEGER -> new Value(0);
			case LONG -> new Value((long) 0);
			case DATE -> new Value((LocalDate) null);
			case TIME -> new Value((LocalTime) null);
			case DATETIME -> new Value((LocalDateTime) null);
			case STRING -> new Value("");
			case BINARY -> new Value(new byte[0]);
			case OBJECT -> new Value(new JSONObject());
		};
	}

}
