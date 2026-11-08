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
package msfx.lib.md;

import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import msfx.lib.util.Properties;

/**
 * Represents a column in a table.
 * <p>
 * The nullable flag, the default value and the possible values are kept
 * consistent with each other and with the type:
 * <ul>
 * <li>Only types that {@link Types#isNullable()} can be nullable, and by
 * default a column is nullable if its type is.</li>
 * <li>Default and possible values must be compatible with the type, may only
 * hold null if the column is nullable, may not have more decimals than a
 * DECIMAL column, and may not be longer than a STRING column.</li>
 * <li>When the column has possible values, the default value must be one of
 * them. Set the possible values before the default value.</li>
 * </ul>
 *
 * @author Miquel Sas
 */
public class Column {
    /**
     * Container for the column properties.
     */
    private final Properties props = new Properties();

    /**
     * @param name     The name of the column.
     * @param type     The type of the column.
     * @param length   The length of the column, -1 if not applicable.
     * @param decimals The number of decimal places for the column, -1 if not
     *                 applicable.
     */
    public Column(String name, Types type, int length, int decimals) {
        Objects.requireNonNull(type, "Type can not be null");
        props.putObject("type", type);
        props.put("name", name);
        props.put("length", length);
        props.put("decimals", decimals);
    }

    /**
     * @return The name of the column.
     */
    public String getName() {
        return props.getString("name");
    }

    /**
     * @return The type of the column.
     */
    public Types getType() {
        return props.getObject("type", Types.class);
    }

    /**
     * @return The length of the column, -1 if not applicable.
     */
    public int getLength() {
        return props.getInteger("length");
    }

    /**
     * @return The number of decimal places for the column, -1 if not applicable.
     */
    public int getDecimals() {
        return props.getInteger("decimals");
    }

    /**
     * @return Whether the column accepts null values. If not set, whether the
     *         type is nullable.
     */
    public boolean isNullable() {
        return props.getBoolean("nullable", getType().isNullable());
    }

    /**
     * @param nullable Whether the column accepts null values.
     * @throws IllegalArgumentException If set to true and the type is not
     *                                  nullable, or set to false and the default
     *                                  value or a possible value is null.
     */
    public void setNullable(boolean nullable) {
        Types type = getType();
        if (nullable && !type.isNullable()) {
            throw new IllegalArgumentException("Column " + getName() + " of type " + type + " can not be nullable");
        }
        if (!nullable) {
            Value defaultValue = getExplicitDefaultValue();
            if (defaultValue != null && defaultValue.isNull()) {
                throw new IllegalArgumentException("Column " + getName() + " has a null default value");
            }
            for (Value value : getPossibleValues().keySet()) {
                if (value.isNull()) {
                    throw new IllegalArgumentException("Column " + getName() + " has a null possible value");
                }
            }
        }
        props.put("nullable", nullable);
    }

    /**
     * Returns the default value of the column, which is, in order:
     * <ol>
     * <li>The default value explicitly set.</li>
     * <li>The first possible value, if the column has possible values.</li>
     * <li>The default value of the type, see {@link Types#getDefaultValue}.</li>
     * </ol>
     *
     * @return The default value, or null if the column is not nullable and the
     *         type default is null, meaning there is no default and a value must
     *         always be given.
     */
    public Value getDefaultValue() {
        Value defaultValue = getExplicitDefaultValue();
        if (defaultValue != null) {
            return defaultValue;
        }
        if (hasPossibleValues()) {
            return getPossibleValues().keySet().iterator().next();
        }
        Value value = Types.getDefaultValue(getType(), getDecimals());
        if (value.isNull() && !isNullable()) {
            return null;
        }
        return value;
    }

    /**
     * Sets the default value. Numbers are converted to the column type.
     *
     * @param defaultValue The default value.
     * @throws NullPointerException     If the default value is null.
     * @throws IllegalArgumentException If the value does not fit the column, see
     *                                  the class description, or the column has
     *                                  possible values and it is not one of them.
     */
    public void setDefaultValue(Value defaultValue) {
        Value value = validateValue(defaultValue, "Default value");
        if (hasPossibleValues() && !getPossibleValues().containsKey(value)) {
            throw new IllegalArgumentException(
                    "Default value " + value + " is not a possible value of column " + getName());
        }
        props.putObject("default-value", value);
    }

    /**
     * Removes the default value explicitly set, so the default falls back to the
     * first possible value or the type default.
     */
    public void clearDefaultValue() {
        props.remove("default-value");
    }

    /**
     * @return The default value explicitly set, or null if not set.
     */
    private Value getExplicitDefaultValue() {
        return props.getObject("default-value", Value.class);
    }

    /**
     * Returns the possible values of the column, each with its label, in the
     * order they were set. When not empty, the column is restricted to these
     * values.
     *
     * @return An unmodifiable view of the possible values mapped to their labels,
     *         or an empty map if none are set.
     */
    public Map<Value, String> getPossibleValues() {
        LinkedHashMap<Value, String> possibleValues = props.getLinkedHashMap("possible-values");
        if (possibleValues == null) {
            possibleValues = new LinkedHashMap<>();
        }
        return Collections.unmodifiableMap(possibleValues);
    }

    /**
     * Removes a possible value and its label. Does nothing if the value is not
     * set.
     *
     * @param value The value to remove.
     * @throws NullPointerException     If the value is null.
     * @throws IllegalArgumentException If the value is not compatible with the
     *                                  type.
     * @throws IllegalStateException    If the value is the default value
     *                                  explicitly set. Change or clear the
     *                                  default value first.
     */
    public void removePossibleValue(Value value) {
        Objects.requireNonNull(value, "Possible value can not be null");
        Value accepted = getType().accept(value);
        Value defaultValue = getExplicitDefaultValue();
        if (defaultValue != null && defaultValue.equals(accepted)) {
            throw new IllegalStateException(
                    "Possible value " + accepted + " is the default value of column " + getName());
        }
        LinkedHashMap<Value, String> possibleValues = props.getLinkedHashMap("possible-values");
        if (possibleValues != null) {
            possibleValues.remove(accepted);
        }
    }

    /**
     * Removes all the possible values, so the column is no longer restricted.
     */
    public void clearPossibleValues() {
        LinkedHashMap<Value, String> possibleValues = props.getLinkedHashMap("possible-values");
        if (possibleValues != null) {
            possibleValues.clear();
        }
    }

    /**
     * @return Whether the column has possible values, that is, whether it is
     *         restricted to a set of values.
     */
    public boolean hasPossibleValues() {
        LinkedHashMap<Value, String> possibleValues = props.getLinkedHashMap("possible-values");
        return possibleValues != null && !possibleValues.isEmpty();
    }

    /**
     * Adds a possible value without a label.
     *
     * @param value The value.
     * @see #setPossibleValue(Value, String)
     */
    public void setPossibleValue(Value value) {
        setPossibleValue(value, null);
    }

    /**
     * Adds a possible value with its label. Numbers are converted to the column
     * type. If the value is already set, its label is replaced and its position
     * is kept.
     *
     * @param value The value.
     * @param label The label to show for the value, may be null.
     * @throws NullPointerException     If the value is null.
     * @throws IllegalArgumentException If the value does not fit the column, see
     *                                  the class description, or it is the first
     *                                  possible value and a different default
     *                                  value is explicitly set.
     */
    public void setPossibleValue(Value value, String label) {
        Value accepted = validateValue(value, "Possible value");
        LinkedHashMap<Value, String> possibleValues = props.getLinkedHashMap("possible-values");
        if (possibleValues == null) {
            possibleValues = new LinkedHashMap<>();
            props.put("possible-values", possibleValues);
        }
        /* Restricting the column would leave the explicit default out. */
        Value defaultValue = getExplicitDefaultValue();
        if (possibleValues.isEmpty() && defaultValue != null && !defaultValue.equals(accepted)) {
            throw new IllegalArgumentException("Default value " + defaultValue + " of column " + getName()
                    + " would not be a possible value. Set the possible values first.");
        }
        possibleValues.put(accepted, label);
    }

    /**
     * Checks that a default or possible value fits the column.
     *
     * @param value The value.
     * @param what  What the value is, for error messages.
     * @return The value, converted to the column type if it is a number.
     * @throws NullPointerException     If the value is null.
     * @throws IllegalArgumentException If the value does not fit the column.
     */
    private Value validateValue(Value value, String what) {
        Objects.requireNonNull(value, what + " can not be null");
        Types type = getType();
        Value accepted = type.accept(value);
        if (accepted.isNull()) {
            if (!isNullable()) {
                throw new IllegalArgumentException(what + " can not be null, column " + getName() + " is not nullable");
            }
            return accepted;
        }
        int decimals = getDecimals();
        if (type.isDecimal() && decimals >= 0) {
            try {
                accepted = new Value(accepted.getDecimal().setScale(decimals, RoundingMode.UNNECESSARY));
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(
                        what + " " + value + " has more than " + decimals + " decimals, column " + getName());
            }
        }
        int length = getLength();
        if (type.isString() && length > 0 && accepted.getString().length() > length) {
            throw new IllegalArgumentException(
                    what + " " + value + " is longer than " + length + ", column " + getName());
        }
        return accepted;
    }

    /**
     * @return The user properties of the column.
     */
    public Properties getProperties() {
        Properties userProps = props.getProperties("user-props");
        if (userProps == null) {
            userProps = new Properties();
            props.put("user-props", userProps);
        }
        return userProps;
    }

    /**
     * @return The header of the column, used as table header, or the name if not
     *         set.
     */
    public String getHeader() {
        return props.getString("header", getName());
    }

    /**
     * @param header The header of the column, used as table header.
     * @throws NullPointerException If the header is null.
     */
    public void setHeader(String header) {
        Objects.requireNonNull(header, "Header can not be null");
        props.put("header", header);
    }

    /**
     * @return The label of the column, used as form label, or the name if not
     *         set.
     */
    public String getLabel() {
        return props.getString("label", getName());
    }

    /**
     * @param label The label of the column, used as form label.
     * @throws NullPointerException If the label is null.
     */
    public void setLabel(String label) {
        Objects.requireNonNull(label, "Label can not be null");
        props.put("label", label);
    }

    /**
     * @return The description of the column, or the label if not set.
     */
    public String getDescription() {
        return props.getString("description", getLabel());
    }

    /**
     * @param description The description of the column.
     * @throws NullPointerException If the description is null.
     */
    public void setDescription(String description) {
        Objects.requireNonNull(description, "Description can not be null");
        props.put("description", description);
    }

    /**
     * @return The table that owns the column, or null if it has not been added
     *         to a table.
     */
    public Table getTable() {
        return props.getObject("table", Table.class);
    }

    /**
     * Sets the table that owns the column. Only called by the table when the
     * column is added to it.
     *
     * @param table The owner table.
     */
    void setTable(Table table) {
        props.putObject("table", table);
    }
}
