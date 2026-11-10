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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import msfx.lib.util.Copier;
import msfx.lib.util.Copyable;
import msfx.lib.util.Properties;

/**
 * Basic definition of column properties, to normalize the definition of
 * columns used in several tables.
 * <p>
 * A domain defines the name, type, length and decimals, and optionally the
 * header, label, description, default value and possible values. A column
 * created with {@link Column#Column(Domain)} copies them, and can change them
 * afterwards without affecting the domain.
 * <p>
 * The getters of the optional properties return null when not set: the
 * fallbacks, like the header falling back to the name, are applied by the
 * column.
 * <p>
 * The default and possible values follow the same rules as in {@link Column},
 * with the domain nullable if its type is: they may only hold null if the type
 * is nullable.
 *
 * @author Miquel Sas
 */
public class Domain implements Copyable<Domain> {
    /**
     * Container for the domain properties.
     */
    private final Properties props = new Properties();

    /**
     * Empty domain, to be filled by {@link #copy(Copier)}.
     */
    private Domain() {
    }

    /**
     * @param name     The name of the domain.
     * @param type     The type of the domain.
     * @param length   The length of the domain, -1 if not applicable.
     * @param decimals The number of decimal places for the domain, -1 if not
     *                 applicable.
     * @throws NullPointerException If the type is null.
     */
    public Domain(String name, Types type, int length, int decimals) {
        Objects.requireNonNull(type, "Type can not be null");
        props.putObject("type", type);
        props.put("name", name);
        props.put("length", length);
        props.put("decimals", decimals);
    }

    /**
     * @return The name of the domain.
     */
    public String getName() {
        return props.getString("name");
    }

    /**
     * @return The type of the domain.
     */
    public Types getType() {
        return props.getObject("type", Types.class);
    }

    /**
     * @return The length of the domain, -1 if not applicable.
     */
    public int getLength() {
        return props.getInteger("length");
    }

    /**
     * @return The number of decimal places for the domain, -1 if not applicable.
     */
    public int getDecimals() {
        return props.getInteger("decimals");
    }

    /**
     * @return The header, or null if not set.
     */
    public String getHeader() {
        return props.getString("header", null);
    }

    /**
     * @param header The header, used as table header.
     * @throws NullPointerException If the header is null.
     */
    public void setHeader(String header) {
        Objects.requireNonNull(header, "Header can not be null");
        props.put("header", header);
    }

    /**
     * @return The label, or null if not set.
     */
    public String getLabel() {
        return props.getString("label", null);
    }

    /**
     * @param label The label, used as form label.
     * @throws NullPointerException If the label is null.
     */
    public void setLabel(String label) {
        Objects.requireNonNull(label, "Label can not be null");
        props.put("label", label);
    }

    /**
     * @return The description, or null if not set.
     */
    public String getDescription() {
        return props.getString("description", null);
    }

    /**
     * @param description The description.
     * @throws NullPointerException If the description is null.
     */
    public void setDescription(String description) {
        Objects.requireNonNull(description, "Description can not be null");
        props.put("description", description);
    }

    /**
     * @return The default value, or null if not set.
     */
    public Value getDefaultValue() {
        return props.getObject("default-value", Value.class);
    }

    /**
     * Sets the default value. Numbers are converted to the domain type.
     *
     * @param defaultValue The default value.
     * @throws NullPointerException     If the default value is null.
     * @throws IllegalArgumentException If the value does not fit the domain, or
     *                                  the domain has possible values and it is
     *                                  not one of them.
     * @see Column#setDefaultValue(Value)
     */
    public void setDefaultValue(Value defaultValue) {
        Value value = validateValue(defaultValue, "Default value");
        if (hasPossibleValues() && !getPossibleValues().containsKey(value)) {
            StringBuilder msg = new StringBuilder();
            msg.append("Default value ");
            msg.append(value);
            msg.append(" is not a possible value of domain ");
            msg.append(getName());
            throw new IllegalArgumentException(msg.toString());
        }
        props.putObject("default-value", value);
    }

    /**
     * Removes the default value.
     */
    public void clearDefaultValue() {
        props.remove("default-value");
    }

    /**
     * @return An unmodifiable view of the possible values mapped to their labels,
     *         in the order they were set, or an empty map if none are set.
     */
    public Map<Value, String> getPossibleValues() {
        LinkedHashMap<Value, String> possibleValues = props.getLinkedHashMap("possible-values");
        if (possibleValues == null) {
            possibleValues = new LinkedHashMap<>();
        }
        return Collections.unmodifiableMap(possibleValues);
    }

    /**
     * @return Whether the domain has possible values.
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
     * Adds a possible value with its label. Numbers are converted to the domain
     * type. If the value is already set, its label is replaced and its position
     * is kept.
     *
     * @param value The value.
     * @param label The label to show for the value, may be null.
     * @throws NullPointerException     If the value is null.
     * @throws IllegalArgumentException If the value does not fit the domain, or
     *                                  it is the first possible value and a
     *                                  different default value is set.
     * @see Column#setPossibleValue(Value, String)
     */
    public void setPossibleValue(Value value, String label) {
        Value accepted = validateValue(value, "Possible value");
        LinkedHashMap<Value, String> possibleValues = props.getLinkedHashMap("possible-values");
        if (possibleValues == null) {
            possibleValues = new LinkedHashMap<>();
            props.put("possible-values", possibleValues);
        }
        /* Restricting the domain would leave the default out. */
        Value defaultValue = getDefaultValue();
        if (possibleValues.isEmpty() && defaultValue != null && !defaultValue.equals(accepted)) {
            StringBuilder msg = new StringBuilder();
            msg.append("Default value ");
            msg.append(defaultValue);
            msg.append(" of domain ");
            msg.append(getName());
            msg.append(" would not be a possible value. Set the possible values first.");
            throw new IllegalArgumentException(msg.toString());
        }
        possibleValues.put(accepted, label);
    }

    /**
     * Removes a possible value and its label. Does nothing if the value is not
     * set.
     *
     * @param value The value to remove.
     * @throws NullPointerException     If the value is null.
     * @throws IllegalArgumentException If the value is not compatible with the
     *                                  type.
     * @throws IllegalStateException    If the value is the default value. Change
     *                                  or clear the default value first.
     */
    public void removePossibleValue(Value value) {
        Objects.requireNonNull(value, "Possible value can not be null");
        Value accepted = getType().accept(value);
        Value defaultValue = getDefaultValue();
        if (defaultValue != null && defaultValue.equals(accepted)) {
            StringBuilder msg = new StringBuilder();
            msg.append("Possible value ");
            msg.append(accepted);
            msg.append(" is the default value of domain ");
            msg.append(getName());
            throw new IllegalStateException(msg.toString());
        }
        LinkedHashMap<Value, String> possibleValues = props.getLinkedHashMap("possible-values");
        if (possibleValues != null) {
            possibleValues.remove(accepted);
        }
    }

    /**
     * Removes all the possible values, so the domain is no longer restricted.
     */
    public void clearPossibleValues() {
        LinkedHashMap<Value, String> possibleValues = props.getLinkedHashMap("possible-values");
        if (possibleValues != null) {
            possibleValues.clear();
        }
    }

    /**
     * Copies the domain with all its properties.
     *
     * @param copier The copier of the copy operation.
     * @return The copy.
     */
    @Override
    public Domain copy(Copier copier) {
        Domain copy = new Domain();
        copier.register(this, copy);
        for (Object key : props.keys()) {
            copy.props.putObject(key, copier.copy(props.getObject(key, Object.class)));
        }
        return copy;
    }

    /**
     * Checks that a default or possible value fits the domain, nullable if its
     * type is.
     *
     * @param value The value.
     * @param what  What the value is, for error messages.
     * @return The value, converted to the domain type if it is a number.
     * @throws NullPointerException     If the value is null.
     * @throws IllegalArgumentException If the value does not fit the domain.
     */
    private Value validateValue(Value value, String what) {
        Types type = getType();
        StringBuilder owner = new StringBuilder();
        owner.append("domain ");
        owner.append(getName());
        return Column.validateValue(
                value, type, getLength(), getDecimals(), type.isNullable(), what, owner.toString());
    }
}
