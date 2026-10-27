/*
 * Copyright (c) 2026 Miquel Sas.
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

package msfx.lib.util.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static msfx.lib.util.json.JSONTypes.*;

/**
 * An entry value of the JSONObject internal map, or an item of a JSONArray.
 *
 * @author Miquel Sas
 */
public class JSONEntry {

	/**
	 * Type.
	 */
	final JSONTypes type;
	/**
	 * Value.
	 */
	final Object value;

	/**
	 * Constructor of an entry of type NULL.
	 */
	public JSONEntry() {
		type = NULL;
		value = null;
	}

	/**
	 * @param value A non-null {@link JSONObject}.
	 */
	public JSONEntry(JSONObject value) {
		if (value == null) throw new NullPointerException();
		this.type = OBJECT;
		this.value = value;
	}

	/**
	 * @param value A non-null {@link JSONArray}.
	 */
	public JSONEntry(JSONArray value) {
		if (value == null) throw new NullPointerException();
		this.type = ARRAY;
		this.value = value;
	}

	/**
	 * @param value A non-null {@link String}.
	 */
	public JSONEntry(String value) {
		if (value == null) throw new NullPointerException();
		this.type = STRING;
		this.value = value;
	}

	/**
	 * @param value A non-null {@link Number}.
	 */
	public JSONEntry(Number value) {
		if (value == null) throw new NullPointerException();
		this.type = NUMBER;
		this.value = toDecimal(value);
	}

	/**
	 * @param value A non-null {@link Number}.
	 * @param scale The scale or number of decimals.
	 */
	public JSONEntry(Number value, int scale) {
		if (value == null) throw new NullPointerException();
		this.type = NUMBER;
		this.value = toDecimal(value).setScale(scale, RoundingMode.HALF_UP);
	}

	/**
	 * @param value A boolean.
	 */
	public JSONEntry(boolean value) {
		this.type = BOOLEAN;
		this.value = value;
	}

	/**
	 * @param value A byte array, may be null.
	 */
	public JSONEntry(byte[] value) {
		this.type = BINARY;
		this.value = value;
	}

	/**
	 * @param value A {@link LocalDate}, may be null.
	 */
	public JSONEntry(LocalDate value) {
		this.type = DATE;
		this.value = value;
	}

	/**
	 * @param value A {@link LocalTime}, may be null.
	 */
	public JSONEntry(LocalTime value) {
		this.type = TIME;
		this.value = value;
	}

	/**
	 * @param value A {@link LocalDateTime}, may be null.
	 */
	public JSONEntry(LocalDateTime value) {
		this.type = TIMESTAMP;
		this.value = value;
	}

	/**
	 * @param type  Type.
	 * @param value Value. A number may be any {@link Number} or a numeric string.
	 * @throws IllegalArgumentException If the value does not match the type.
	 */
	JSONEntry(JSONTypes type, Object value) {
		if (type == null) throw new NullPointerException("Type can not be null");

		/* Null value. Only the NULL type and extended types support it. */
		if (value == null) {
			if (type != NULL && !type.isExtended()) {
				throw new IllegalArgumentException("Invalid type " + type + " for a null value");
			}
		} else {
			boolean valid = switch (type) {
				case OBJECT -> value instanceof JSONObject;
				case ARRAY -> value instanceof JSONArray;
				case STRING -> value instanceof String;
				case NUMBER -> value instanceof Number || value instanceof String;
				case BOOLEAN -> value instanceof Boolean;
				case NULL -> false;
				case BINARY -> value instanceof byte[];
				case DATE -> value instanceof LocalDate;
				case TIME -> value instanceof LocalTime;
				case TIMESTAMP -> value instanceof LocalDateTime;
			};
			if (!valid) {
				throw new IllegalArgumentException("Invalid type " + type + " for " + value.getClass());
			}
			/* Numbers are always stored as BigDecimal. */
			if (type == NUMBER) {
				value = (value instanceof String str) ? new BigDecimal(str) : toDecimal((Number) value);
			}
		}

		this.type = type;
		this.value = value;
	}

	/**
	 * @param number The number.
	 * @return The number as a {@link BigDecimal}.
	 * @throws IllegalArgumentException If the number is NaN or infinite, which JSON can not represent.
	 */
	private static BigDecimal toDecimal(Number number) {
		if (number instanceof BigDecimal decimal) {
			return decimal;
		}
		if (number instanceof BigInteger integer) {
			return new BigDecimal(integer);
		}
		if (number instanceof Double || number instanceof Float) {
			double d = number.doubleValue();
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				throw new IllegalArgumentException("JSON numbers can not be NaN or infinite: " + number);
			}
		}
		/* toString keeps the short decimal form, e.g. 0.1f is "0.1", not 0.100000001490116... */
		return new BigDecimal(number.toString());
	}

	/**
	 * @return Whether the value is a {@link JSONObject}.
	 */
	public boolean isObject() {
		return type == OBJECT;
	}

	/**
	 * @return Whether the value is a {@link JSONArray}.
	 */
	public boolean isArray() {
		return type == ARRAY;
	}

	/**
	 * @return Whether the value is a {@link String}.
	 */
	public boolean isString() {
		return type == STRING;
	}

	/**
	 * @return Whether the value is a {@link Number}.
	 */
	public boolean isNumber() {
		return type == NUMBER;
	}

	/**
	 * @return Whether the value is a boolean.
	 */
	public boolean isBoolean() {
		return type == BOOLEAN;
	}

	/**
	 * @return Whether the type is NULL.
	 */
	public boolean isNullType() {
		return type == NULL;
	}

	/**
	 * @return Whether the value is null, either of NULL type or of a null extended type.
	 */
	public boolean isNullValue() {
		return value == null;
	}

	/**
	 * @return Whether the value is a byte[].
	 */
	public boolean isBinary() {
		return type == BINARY;
	}

	/**
	 * @return Whether the value is a {@link LocalDate}.
	 */
	public boolean isDate() {
		return type == DATE;
	}

	/**
	 * @return Whether the value is a {@link LocalTime}.
	 */
	public boolean isTime() {
		return type == TIME;
	}

	/**
	 * @return Whether the value is a {@link LocalDateTime}.
	 */
	public boolean isTimestamp() {
		return type == TIMESTAMP;
	}

	/**
	 * @return The value as a {@link JSONObject}.
	 * @throws IllegalStateException If the value is not an OBJECT.
	 */
	public JSONObject getObject() {
		if (type != OBJECT) throw new IllegalStateException("Value is not OBJECT");
		return (JSONObject) value;
	}

	/**
	 * @return The value as a {@link JSONArray}.
	 * @throws IllegalStateException If the value is not an ARRAY.
	 */
	public JSONArray getArray() {
		if (type != ARRAY) throw new IllegalStateException("Value is not ARRAY");
		return (JSONArray) value;
	}

	/**
	 * @return The value as a {@link String}.
	 * @throws IllegalStateException If the value is not a STRING.
	 */
	public String getString() {
		if (type != STRING) throw new IllegalStateException("Value is not STRING");
		return (String) value;
	}

	/**
	 * @return The value as a {@link BigDecimal}.
	 * @throws IllegalStateException If the value is not a NUMBER.
	 */
	public BigDecimal getNumber() {
		if (type != NUMBER) throw new IllegalStateException("Value is not NUMBER");
		return (BigDecimal) value;
	}

	/**
	 * @return The value as a boolean.
	 * @throws IllegalStateException If the value is not a BOOLEAN.
	 */
	public boolean getBoolean() {
		if (type != BOOLEAN) throw new IllegalStateException("Value is not BOOLEAN");
		return (boolean) value;
	}

	/**
	 * @return The value as a byte[], or null.
	 * @throws IllegalStateException If the value is not BINARY.
	 */
	public byte[] getBinary() {
		if (type != BINARY) throw new IllegalStateException("Value is not BINARY");
		return (byte[]) value;
	}

	/**
	 * @return The value as a {@link LocalDate}, or null.
	 * @throws IllegalStateException If the value is not a DATE.
	 */
	public LocalDate getDate() {
		if (type != DATE) throw new IllegalStateException("Value is not DATE");
		return (LocalDate) value;
	}

	/**
	 * @return The value as a {@link LocalTime}, or null.
	 * @throws IllegalStateException If the value is not a TIME.
	 */
	public LocalTime getTime() {
		if (type != TIME) throw new IllegalStateException("Value is not TIME");
		return (LocalTime) value;
	}

	/**
	 * @return The value as a {@link LocalDateTime}, or null.
	 * @throws IllegalStateException If the value is not a TIMESTAMP.
	 */
	public LocalDateTime getTimestamp() {
		if (type != TIMESTAMP) throw new IllegalStateException("Value is not TIMESTAMP");
		return (LocalDateTime) value;
	}

	/**
	 * @return The type.
	 */
	public JSONTypes getType() {
		return type;
	}
}
