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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import msfx.lib.util.Copier;
import msfx.lib.util.Copyable;
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
public class Column implements Copyable<Column> {
    /**
     * The name of the column.
     */
    private final String name;
    /**
     * The data type of the column: type, length and decimals.
     */
    private final DataType dataType;
    /**
     * The table that owns the column, or null while it has not been added to a
     * table.
     */
    private Table table;
    /**
     * Container for the other column properties.
     */
    private final Properties props = new Properties();

    /**
     * Column with a name and a data type, used by {@link #copy(Copier)}.
     *
     * @param name     The name of the column.
     * @param dataType The data type.
     * @throws NullPointerException If the name or the data type is null.
     */
    private Column(String name, DataType dataType) {
        this.name = Objects.requireNonNull(name, "Name can not be null");
        this.dataType = Objects.requireNonNull(dataType, "Data type can not be null");
    }

    /**
     * @param name     The name of the column.
     * @param type     The type of the column.
     * @param length   The length of the column, -1 if not applicable.
     * @param decimals The number of decimal places for the column, -1 if not
     *                 applicable.
     * @throws NullPointerException If the name or the type is null.
     */
    public Column(String name, Types type, int length, int decimals) {
        this(name, new DataType(type, length, decimals));
    }

    /**
     * Creates a column from a domain, copying its name, type, length, decimals,
     * and the header, label, description, default value and possible values set
     * in the domain. They can be changed afterwards in the column without
     * affecting the domain.
     *
     * @param domain The domain.
     * @throws NullPointerException If the domain is null.
     */
    public Column(Domain domain) {
        this(Objects.requireNonNull(domain, "Domain can not be null").getName(), domain);
    }

    /**
     * Creates a column from a domain with another name, copying the rest as
     * {@link #Column(Domain)} does. The data type is shared with the domain,
     * since it is immutable.
     *
     * @param name   The name of the column.
     * @param domain The domain.
     * @throws NullPointerException If the name or the domain is null.
     */
    public Column(String name, Domain domain) {
        this(name, Objects.requireNonNull(domain, "Domain can not be null").getDataType());
        if (domain.getHeader() != null) {
            props.put("header", domain.getHeader());
        }
        if (domain.getLabel() != null) {
            props.put("label", domain.getLabel());
        }
        if (domain.getDescription() != null) {
            props.put("description", domain.getDescription());
        }
        /* Already checked by the domain, with the same type, length, decimals and nullable. */
        if (domain.hasPossibleValues()) {
            props.put("possible-values", new LinkedHashMap<>(domain.getPossibleValues()));
        }
        if (domain.getDefaultValue() != null) {
            props.putObject("default-value", domain.getDefaultValue());
        }
    }

    /**
     * Creates a copy of a column that keeps its table, to use the column apart
     * from the table, for instance in the select list of a query, with its own
     * header, label or other properties. The copy refers to the table but is
     * not one of its columns: it is equal to the original, and changes to its
     * properties do not reach it. The data type is shared, since it is
     * immutable.
     *
     * @param column The column to copy.
     * @throws NullPointerException If the column is null.
     */
    public Column(Column column) {
        this(Objects.requireNonNull(column, "Column can not be null").name, column.dataType);
        this.table = column.table;
        Copier copier = new Copier();
        for (Object key : column.props.keys()) {
            Object value = column.props.getObject(key, Object.class);
            props.putObject(key, copier.copy(value));
        }
    }

    /**
     * @return The name of the column.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The data type of the column: type, length and decimals.
     */
    public DataType getDataType() {
        return dataType;
    }

    /**
     * Returns a new domain with the definition of the column, to define other
     * columns like it, for instance the columns of a select. It has the name and
     * the data type of the column, and the header, label, description, possible
     * values and default value explicitly set in the column, without the
     * fallbacks of the getters. The table, the nullable flag and the user
     * properties are not part of a domain.
     * <p>
     * A new domain is created on each call, reflecting the current definition
     * of the column; the data type is shared, since it is immutable.
     *
     * @return The domain.
     */
    public Domain getDomain() {
        Domain domain = new Domain(getName(), getDataType());
        String header = props.getString("header", null);
        if (header != null) {
            domain.setHeader(header);
        }
        String label = props.getString("label", null);
        if (label != null) {
            domain.setLabel(label);
        }
        String description = props.getString("description", null);
        if (description != null) {
            domain.setDescription(description);
        }
        /* Possible values first, so that the default value is one of them. */
        for (Map.Entry<Value, String> entry : getPossibleValues().entrySet()) {
            domain.setPossibleValue(entry.getKey(), entry.getValue());
        }
        Value defaultValue = getExplicitDefaultValue();
        if (defaultValue != null) {
            domain.setDefaultValue(defaultValue);
        }
        return domain;
    }

    /**
     * @return The type of the column.
     */
    public Types getType() {
        return getDataType().type();
    }

    /**
     * @return The length of the column, -1 if not applicable.
     */
    public int getLength() {
        return getDataType().length();
    }

    /**
     * @return The number of decimal places for the column, -1 if not applicable.
     */
    public int getDecimals() {
        return getDataType().decimals();
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
            StringBuilder msg = new StringBuilder();
            msg.append("Column ");
            msg.append(getName());
            msg.append(" of type ");
            msg.append(type);
            msg.append(" can not be nullable");
            throw new IllegalArgumentException(msg.toString());
        }
        if (!nullable) {
            Value defaultValue = getExplicitDefaultValue();
            if (defaultValue != null && defaultValue.isNull()) {
                StringBuilder msg = new StringBuilder();
                msg.append("Column ");
                msg.append(getName());
                msg.append(" has a null default value");
                throw new IllegalArgumentException(msg.toString());
            }
            for (Value value : getPossibleValues().keySet()) {
                if (value.isNull()) {
                    StringBuilder msg = new StringBuilder();
                    msg.append("Column ");
                    msg.append(getName());
                    msg.append(" has a null possible value");
                    throw new IllegalArgumentException(msg.toString());
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
            StringBuilder msg = new StringBuilder();
            msg.append("Default value ");
            msg.append(value);
            msg.append(" is not a possible value of column ");
            msg.append(getName());
            throw new IllegalArgumentException(msg.toString());
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
            StringBuilder msg = new StringBuilder();
            msg.append("Possible value ");
            msg.append(accepted);
            msg.append(" is the default value of column ");
            msg.append(getName());
            throw new IllegalStateException(msg.toString());
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
            StringBuilder msg = new StringBuilder();
            msg.append("Default value ");
            msg.append(defaultValue);
            msg.append(" of column ");
            msg.append(getName());
            msg.append(" would not be a possible value. Set the possible values first.");
            throw new IllegalArgumentException(msg.toString());
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
        StringBuilder owner = new StringBuilder();
        owner.append("column ");
        owner.append(getName());
        return validateValue(value, getType(), getLength(), getDecimals(), isNullable(), what, owner.toString());
    }

    /**
     * Checks that a default or possible value fits a type, length, decimals and
     * nullable flag. Shared by {@link Column} and {@link Domain}.
     *
     * @param value    The value.
     * @param type     The type.
     * @param length   The length, -1 if not applicable.
     * @param decimals The decimals, -1 if not applicable.
     * @param nullable Whether null values are accepted.
     * @param what     What the value is, for error messages.
     * @param owner    The owner of the value, for error messages.
     * @return The value, converted to the type if it is a number.
     * @throws NullPointerException     If the value is null.
     * @throws IllegalArgumentException If the value does not fit.
     */
    static Value validateValue(
            Value value, Types type, int length, int decimals, boolean nullable, String what, String owner) {
        if (value == null) {
            StringBuilder msg = new StringBuilder();
            msg.append(what);
            msg.append(" can not be null");
            throw new NullPointerException(msg.toString());
        }
        Value accepted = type.accept(value);
        if (accepted.isNull()) {
            if (!nullable) {
                StringBuilder msg = new StringBuilder();
                msg.append(what);
                msg.append(" can not be null, ");
                msg.append(owner);
                msg.append(" is not nullable");
                throw new IllegalArgumentException(msg.toString());
            }
            return accepted;
        }
        if (type.isDecimal() && decimals >= 0) {
            try {
                accepted = new Value(accepted.getDecimal().setScale(decimals, RoundingMode.UNNECESSARY));
            } catch (ArithmeticException e) {
                StringBuilder msg = new StringBuilder();
                msg.append(what);
                msg.append(" ");
                msg.append(value);
                msg.append(" has more than ");
                msg.append(decimals);
                msg.append(" decimals, ");
                msg.append(owner);
                throw new IllegalArgumentException(msg.toString());
            }
        }
        if (type.isString() && length > 0 && accepted.getString().length() > length) {
            StringBuilder msg = new StringBuilder();
            msg.append(what);
            msg.append(" ");
            msg.append(value);
            msg.append(" is longer than ");
            msg.append(length);
            msg.append(", ");
            msg.append(owner);
            throw new IllegalArgumentException(msg.toString());
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
        return table;
    }

    /**
     * Compares the database structure of two columns: the name, ignoring case,
     * the data type (type, length and decimals) and the table, both null or
     * equal. The other properties, like nullable, the header or the default
     * value, are behaviour, validation or user interface properties that may
     * change over time, and are not compared.
     *
     * @param o The object to compare to.
     * @return True if it is a column with the same name, data type and table.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Column other)) {
            return false;
        }
        return name.equalsIgnoreCase(other.name) && dataType.equals(other.dataType) && Objects.equals(table, other.table);
    }

    /**
     * Hash code consistent with {@link #equals(Object)}: from the name in upper
     * case and the data type. The table is left out, since it is set when the
     * column is added to it, and the hash code must not change afterwards.
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        String key = name.toUpperCase(Locale.ROOT);
        return Objects.hash(key, dataType);
    }

    /**
     * Copies the column with all its properties except its table: the copy
     * belongs to no table until it is added to one. When a table is copied, its
     * copy adds the copies of its columns. The data type is shared, since it is
     * immutable.
     *
     * @param copier The copier of the copy operation.
     * @return The copy.
     */
    @Override
    public Column copy(Copier copier) {
        Column copy = new Column(name, dataType);
        copier.register(this, copy);
        for (Object key : props.keys()) {
            copy.props.putObject(key, copier.copy(props.getObject(key, Object.class)));
        }
        return copy;
    }

    /**
     * Sets the table that owns the column. Only called by the table when the
     * column is added to it.
     *
     * @param table The owner table.
     */
    void setTable(Table table) {
        this.table = table;
    }
}
