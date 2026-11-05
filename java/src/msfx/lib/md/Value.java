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

import msfx.lib.util.Binaries;
import msfx.lib.util.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A mutable value for the supported types.
 *
 * @author Miquel Sas
 */
public class Value implements Comparable<Value> {
	/**
	 * The value.
	 */
	private Object value;
	/**
	 * The type of the value.
	 */
	private final Types type;
	/**
	 * Indicates whether the value has been modified.
	 */
	private boolean modified;

	/**
	 * Constructor of a boolean value.
	 *
	 * @param v A boolean.
	 */
	public Value(boolean v) {
		value = v;
		type = Types.BOOLEAN;
		modified = false;
	}
	/**
	 * Constructor of a BigDecimal value.
	 *
	 * @param v A BigDecimal.
	 */
	public Value(BigDecimal v) {
		if (v == null) {
			throw new IllegalArgumentException("BigDecimal value cannot be null");
		}
		value = v;
		type = Types.DECIMAL;
		modified = false;
	}
	/**
	 * Constructor of a double value.
	 *
	 * @param v A double.
	 */
	public Value(double v) {
		value = v;
		type = Types.DOUBLE;
		modified = false;
	}
	/**
	 * Constructor of an integer value.
	 *
	 * @param v An integer.
	 */
	public Value(int v) {
		value = v;
		type = Types.INTEGER;
		modified = false;
	}
	/**
	 * Constructor of a long value.
	 *
	 * @param v A long.
	 */
	public Value(long v) {
		value = v;
		type = Types.LONG;
		modified = false;
	}
	/**
	 * Constructor of a LocalDate value.
	 *
	 * @param v A LocalDate.
	 */
	public Value(LocalDate v) {
		value = v;
		type = Types.DATE;
		modified = false;
	}
	/**
	 * Constructor of a LocalTime value.
	 *
	 * @param v A LocalTime.
	 */
	public Value(LocalTime v) {
		value = v;
		type = Types.TIME;
		modified = false;
	}
	/**
	 * Constructor of a LocalDateTime value.
	 *
	 * @param v A LocalDateTime.
	 */
	public Value(LocalDateTime v) {
		value = v;
		type = Types.DATETIME;
		modified = false;
	}
	/**
	 * Constructor of a String value.
	 *
	 * @param v A String.
	 */
	public Value(String v) {
		value = v;
		type = Types.STRING;
		modified = false;
	}
	/**
	 * Constructor of a byte array value.
	 *
	 * @param v A byte array.
	 */
	public Value(byte[] v) {
		value = v;
		type = Types.BINARY;
		modified = false;
	}
	/**
	 * Constructor of a JSONObject value.
	 *
	 * @param v A JSONObject.
	 */
	public Value(JSONObject v) {
		value = v;
		type = Types.OBJECT;
		modified = false;
	}

	/**
	 * @return True if the value is null, false otherwise.
	 */
	public boolean isNull() {
		return value == null;
	}
	/**
	 * @return True if the value is a number, false otherwise.
	 */
	public boolean isNumber() {
		return type.isNumber();
	}
	/**
	 * @return True if the value has been modified, false otherwise.
	 */
	public boolean isModified() {
		return modified;
	}
	/**
	 * @return True if the value is a boolean, false otherwise.
	 */
	public boolean isBoolean() {
		return type.isBoolean();
	}
	/**
	 * @return True if the value is a decimal, false otherwise.
	 */
	public boolean isDecimal() {
		return getType().isDecimal();
	}
	/**
	 * @return True if the value is a double, false otherwise.
	 */
	public boolean isDouble() {
		return type.isDouble();
	}
	/**
	 * @return True if the value is an integer, false otherwise.
	 */
	public boolean isInteger() {
		return type.isInteger();
	}
	/**
	 * @return True if the value is a long, false otherwise.
	 */
	public boolean isLong() {
		return type.isLong();
	}
	/**
	 * @return True if the value is a date, false otherwise.
	 */
	public boolean isDate() {
		return type.isDate();
	}
	/**
	 * @return True if the value is a time, false otherwise.
	 */
	public boolean isTime() {
		return type.isTime();
	}
	/**
	 * @return True if the value is a date-time, false otherwise.
	 */
	public boolean isDateTime() {
		return type.isDateTime();
	}
	/**
	 * @return True if the value is a string, false otherwise.
	 */
	public boolean isString() {
		return type.isString();
	}
	/**
	 * @return True if the value is a binary, false otherwise.
	 */
	public boolean isBinary() {
		return type.isBinary();
	}
	/**
	 * @return True if the value is an object, false otherwise.
	 */
	public boolean isObject() {
		return type.isObject();
	}

	/**
	 * @return The type of the value.
	 */
	public Types getType() {
		return type;
	}
	/**
	 * @return The value as a number, or throws an exception if the value is not a number.
	 */
	public Number getNumber() {
		if (!isNumber()) {
			throw new IllegalStateException("Value is not a number");
		}
		return (Number) value;
	}
	/**
	 * @return The value as a boolean, or throws an exception if the value is not a boolean.
	 */
	public boolean getBoolean() {
		if (!isBoolean()) {
			throw new IllegalStateException("Value is not a boolean");
		}
		return (boolean) value;
	}
	/**
	 * @return The value as a decimal, or throws an exception if the value is not a number.
	 */
	public BigDecimal getDecimal() {
		if (!isNumber()) {
			throw new IllegalStateException("Value is not a number");
		}
		if (isDecimal()) {
			return (BigDecimal) value;
		}
		if (isDouble()) {
			return BigDecimal.valueOf((double) value);
		}
		if (isLong()) {
			return BigDecimal.valueOf((long) value);
		}
		if (isInteger()) {
			return BigDecimal.valueOf((int) value);
		}
		throw new IllegalStateException("Should never happen");
	}
	/**
	 * @return The value as a double, or throws an exception if the value is not a number.
	 */
	public double getDouble() {
		if (!isNumber()) {
			throw new IllegalStateException("Value is not a number");
		}
		if (isDecimal()) {
			return ((BigDecimal) value).doubleValue();
		}
		if (isDouble()) {
			return (double) value;
		}
		if (isLong()) {
			return (double) (long) value;
		}
		if (isInteger()) {
			return (double) (int) value;
		}
		throw new IllegalStateException("Should never happen");
	}
	/**
	 * @return The value as a long, or throws an exception if the value is not a number.
	 */
	public long getLong() {
		if (!isNumber()) {
			throw new IllegalStateException("Value is not a number");
		}
		if (isDecimal()) {
			return ((BigDecimal) value).longValue();
		}
		if (isDouble()) {
			return (long) (double) value;
		}
		if (isLong()) {
			return (long) value;
		}
		if (isInteger()) {
			return (long) (int) value;
		}
		throw new IllegalStateException("Should never happen");
	}
	/**
	 * @return The value as an integer, or throws an exception if the value is not a number.
	 */
	public int getInteger() {
		if (!isNumber()) {
			throw new IllegalStateException("Value is not a number");
		}
		if (isDecimal()) {
			return ((BigDecimal) value).intValue();
		}
		if (isDouble()) {
			return (int) (double) value;
		}
		if (isLong()) {
			return (int) (long) value;
		}
		if (isInteger()) {
			return (int) (int) value;
		}
		throw new IllegalStateException("Should never happen");
	}
	/**
	 * @return The value as a date, or throws an exception if the value is not a date.
	 */
	public LocalDate getDate() {
		if (!isDate()) {
			throw new IllegalStateException("Value is not a date");
		}
		return (LocalDate) value;
	}
	/**
	 * @return The value as a time, or throws an exception if the value is not a time.
	 */
	public LocalTime getTime() {
		if (!isTime()) {
			throw new IllegalStateException("Value is not a time");
		}
		return (LocalTime) value;
	}
	/**
	 * @return The value as a date-time, or throws an exception if the value is not a date-time.
	 */
	public LocalDateTime getDateTime() {
		if (!isDateTime()) {
			throw new IllegalStateException("Value is not a date-time");
		}
		return (LocalDateTime) value;
	}
	/**
	 * @return The value as a string, or throws an exception if the value is not a string.
	 */
	public String getString() {
		if (!isString()) {
			throw new IllegalStateException("Value is not a string");
		}
		return (String) value;
	}
	/**
	 * @return The value as a binary, or throws an exception if the value is not a binary.
	 */
	public byte[] getBinary() {
		if (!isBinary()) {
			throw new IllegalStateException("Value is not a binary");
		}
		return (byte[]) value;
	}
	/**
	 * @return The value as an object, or throws an exception if the value is not an object.
	 */
	public JSONObject getObject() {
		if (!isObject()) {
			throw new IllegalStateException("Value is not an object");
		}
		return (JSONObject) value;
	}

	/**
	 * @param v The new boolean value.
	 * @throws IllegalStateException if the value is not a boolean.
	 */
	public void setBoolean(boolean v) {
		if (!isBoolean()) {
			throw new IllegalStateException("Value is not a boolean");
		}
		value = v;
		modified = true;
	}
	/**
	 * @param v The new decimal value.
	 * @throws IllegalStateException    if the value is not a number.
	 * @throws IllegalArgumentException if the value is null.
	 */
	public void setDecimal(BigDecimal v) {
		if (v == null) {
			throw new IllegalArgumentException("Value cannot be null");
		}
		if (!isNumber()) {
			throw new IllegalStateException("Value is not a number");
		}
		if (isDecimal()) {
			/* Preserve initial scale. */
			int scale = ((BigDecimal) value).scale();
			value = v.setScale(scale, RoundingMode.HALF_UP);
		}
		if (isDouble()) {
			value = v.doubleValue();
		}
		if (isLong()) {
			value = v.longValue();
		}
		if (isInteger()) {
			value = v.intValue();
		}
		modified = true;
	}
	/**
	 * @param v The new double value.
	 * @throws IllegalStateException if the value is not a number.
	 */
	public void setDouble(double v) {
		if (!isNumber()) {
			throw new IllegalStateException("Value is not a number");
		}
		if (isDecimal()) {
			/* Preserve initial scale. */
			int scale = ((BigDecimal) value).scale();
			value = BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
		}
		if (isDouble()) {
			value = v;
		}
		if (isLong()) {
			value = (long) v;
		}
		if (isInteger()) {
			value = (int) v;
		}
		modified = true;
	}
	/**
	 * @param v The new long value.
	 * @throws IllegalStateException if the value is not a number.
	 */
	public void setLong(long v) {
		if (!isNumber()) {
			throw new IllegalStateException("Value is not a number");
		}
		if (isDecimal()) {
			/* Preserve initial scale. */
			int scale = ((BigDecimal) value).scale();
			value = BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
		}
		if (isDouble()) {
			value = (double) v;
		}
		if (isLong()) {
			value = v;
		}
		if (isInteger()) {
			value = (int) v;
		}
		modified = true;
	}
	/**
	 * @param v The new int value.
	 * @throws IllegalStateException if the value is not a number.
	 */
	public void setInt(int v) {
		if (!isNumber()) {
			throw new IllegalStateException("Value is not a number");
		}
		if (isDecimal()) {
			/* Preserve initial scale. */
			int scale = ((BigDecimal) value).scale();
			value = BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
		}
		if (isDouble()) {
			value = (double) v;
		}
		if (isLong()) {
			value = (long) v;
		}
		if (isInteger()) {
			value = v;
		}
		modified = true;
	}
	/**
	 * @param v The new date value.
	 * @throws IllegalStateException if the value is not a date.
	 */
	public void setDate(LocalDate v) {
		if (!isDate()) {
			throw new IllegalStateException("Value is not a date");
		}
		value = v;
		modified = true;
	}
	/**
	 * @param v The new time value.
	 * @throws IllegalStateException if the value is not a time.
	 */
	public void setTime(LocalTime v) {
		if (!isTime()) {
			throw new IllegalStateException("Value is not a time");
		}
		value = v;
		modified = true;
	}
	/**
	 * @param v The new date-time value.
	 * @throws IllegalStateException if the value is not a date-time.
	 */
	public void setDateTime(LocalDateTime v) {
		if (!isDateTime()) {
			throw new IllegalStateException("Value is not a date-time");
		}
		value = v;
		modified = true;
	}
	/**
	 * @param v The new string value.
	 * @throws IllegalStateException if the value is not a string.
	 */
	public void setString(String v) {
		if (!isString()) {
			throw new IllegalStateException("Value is not a string");
		}
		value = v;
		modified = true;
	}
	/**
	 * @param v The new binary value.
	 * @throws IllegalStateException if the value is not binary.
	 */
	public void setBinary(byte[] v) {
		if (!isBinary()) {
			throw new IllegalStateException("Value is not binary");
		}
		value = v;
		modified = true;
	}
	/**
	 * @param v The new object value.
	 * @throws IllegalStateException if the value is not an object.
	 */
	public void setObject(JSONObject v) {
		if (!isObject()) {
			throw new IllegalStateException("Value is not an object");
		}
		value = v;
		modified = true;
	}

	/**
	 * @return A string representation of the value.
	 */
	@Override
	public String toString() {
		if (isNull()) {
			return "null";
		}
		if (isBinary()) {
			return new String((byte[]) value);
		}
		return value.toString();
	}

	/**
	 * @param v The value to compare to.
	 * @return A negative integer, zero, or a positive integer as this value is less than, equal to,
	 * or greater than the specified value.
	 * @throws IllegalArgumentException if the values are not comparable.
	 * @throws NullPointerException     if the specified value is null.
	 */
	@Override
	public int compareTo(Value v) {

		// Check null
		if (v == null) {
			throw new NullPointerException("Argument cannot be null");
		}

		// Check comparable
		boolean comparable = (isBoolean() && v.isBoolean());
		comparable |= (isNumber() && v.isNumber());
		comparable |= (isDate() && v.isDate());
		comparable |= (isTime() && v.isTime());
		comparable |= (isDateTime() && v.isDateTime());
		comparable |= (isString() && v.isString());
		comparable |= (isBinary() && v.isBinary());
		comparable |= (isObject() && v.isObject());
		if (!comparable) {
			throw new IllegalArgumentException("Values are not comparable");
		}

		// Null, only one or both
		if (isNull() && !v.isNull()) {
			return -1;
		}
		if (!isNull() && v.isNull()) {
			return 1;
		}
		if (isNull() && v.isNull()) {
			return 0;
		}

		// Boolean
		if (isBoolean() && v.isBoolean()) {
			return ((Boolean) value).compareTo((Boolean) v.value);
		}

		// Number
		if (isNumber() && v.isNumber()) {
			double d1 = getDouble();
			double d2 = v.getDouble();
			return Double.compare(d1, d2);
		}

		// Date
		if (isDate() && v.isDate()) {
			return ((LocalDate) value).compareTo((LocalDate) v.value);
		}

		// Time
		if (isTime() && v.isTime()) {
			return ((LocalTime) value).compareTo((LocalTime) v.value);
		}

		// Date-time
		if (isDateTime() && v.isDateTime()) {
			return ((LocalDateTime) value).compareTo((LocalDateTime) v.value);
		}

		// String
		if (isString() && v.isString()) {
			return ((String) value).compareTo((String) v.value);
		}

		// Binary
		if (isBinary() && v.isBinary()) {
			return Binaries.compare((byte[]) value, (byte[]) v.value);
		}

		// JSONObject
		if (isObject() && v.isObject()) {
			return getObject().toString().compareTo(v.getObject().toString());
		}

		// Never should happen
		throw new IllegalArgumentException("Values are not comparable (should never happen)");
	}

	/**
	 * @param o The object to compare to.
	 * @return True if the values are equal, false otherwise.
	 * @throws IllegalArgumentException if the object is not a Value.
	 */
	@Override
	public boolean equals(Object o) {
		try {
			return (compareTo((Value) o) == 0);
		} catch (Exception e) {
			throw new IllegalArgumentException("Object is not a Value");
		}
	}
	/**
	 * @param v The boolean value to compare to.
	 * @return True if the values are equal, false otherwise.
	 * @throws IllegalArgumentException if the values are not comparable.
	 */
	public boolean equals(boolean v) {
		return equals(new Value(v));
	}
	/**
	 * @param v The BigDecimal value to compare to.
	 * @return True if the values are equal, false otherwise.
	 * @throws IllegalArgumentException if the values are not comparable.
	 */
	public boolean equals(BigDecimal v) {
		return equals(new Value(v));
	}
	/**
	 * @param v The double value to compare to.
	 * @return True if the values are equal, false otherwise.
	 * @throws IllegalArgumentException if the values are not comparable.
	 */
	public boolean equals(double v) {
		return equals(new Value(v));
	}
	/**
	 * @param v The long value to compare to.
	 * @return True if the values are equal, false otherwise.
	 * @throws IllegalArgumentException if the values are not comparable.
	 */
	public boolean equals(long v) {
		return equals(new Value(v));
	}
	/**
	 * @param v The int value to compare to.
	 * @return True if the values are equal, false otherwise.
	 * @throws IllegalArgumentException if the values are not comparable.
	 */
	public boolean equals(int v) {
		return equals(new Value(v));
	}
	/**
	 * @param v The LocalDate value to compare to.
	 * @return True if the values are equal, false otherwise.
	 * @throws IllegalArgumentException if the values are not comparable.
	 */
	public boolean equals(LocalDate v) {
		return equals(new Value(v));
	}
	/**
	 * @param v The LocalTime value to compare to.
	 * @return True if the values are equal, false otherwise.
	 * @throws IllegalArgumentException if the values are not comparable.
	 */
	public boolean equals(LocalTime v) {
		return equals(new Value(v));
	}
	/**
	 * @param v The LocalDateTime value to compare to.
	 * @return True if the values are equal, false otherwise.
	 * @throws IllegalArgumentException if the values are not comparable.
	 */
	public boolean equals(LocalDateTime v) {
		return equals(new Value(v));
	}
	/**
	 * @param v The String value to compare to.
	 * @return True if the values are equal, false otherwise.
	 * @throws IllegalArgumentException if the values are not comparable.
	 */
	public boolean equals(String v) {
		return equals(new Value(v));
	}
	/**
	 * @param v The byte array value to compare to.
	 * @return True if the values are equal, false otherwise.
	 * @throws IllegalArgumentException if the values are not comparable.
	 */
	public boolean equals(byte[] v) {
		return equals(new Value(v));
	}
	/**
	 * @param v The JSONObject value to compare to.
	 * @return True if the values are equal, false otherwise.
	 * @throws IllegalArgumentException if the values are not comparable.
	 */
	public boolean equals(JSONObject v) {
		return equals(new Value(v));
	}
}
