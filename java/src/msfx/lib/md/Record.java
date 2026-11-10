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

import msfx.lib.util.Properties;

/**
 * A list of values with their column definitions.
 * <p>
 * Records are meant to exist in large numbers, as in a record set, so all the
 * records of a set share the same {@link ColumnList}, and each record only adds
 * its array of values. For the same reason the constructor does not check that
 * the values match the column types: check it once, with
 * {@link #validate(ColumnList, Value[])}, before reading a large number of
 * records.
 * <p>
 * The array of values is used as is, not copied.
 *
 * @author Miquel Sas
 */
public class Record {

    /**
     * Checks that the values match the columns: same number, no null references,
     * and each value of the same type as its column. Meant to be called once
     * before creating a large number of records with the same columns.
     *
     * @param columns The columns.
     * @param values  The values.
     * @throws IllegalArgumentException If the columns or the values are null,
     *                                  their sizes differ, a value is null or its
     *                                  type is not the type of its column.
     */
    public static void validate(ColumnList columns, Value[] values) {
        checkSizes(columns, values);
        for (int i = 0; i < values.length; i++) {
            Value value = values[i];
            Column column = columns.get(i);
            if (value == null) {
                StringBuilder msg = new StringBuilder();
                msg.append("Value of column ");
                msg.append(columns.getAlias(i));
                msg.append(" is null");
                throw new IllegalArgumentException(msg.toString());
            }
            if (value.getType() != column.getType()) {
                StringBuilder msg = new StringBuilder();
                msg.append("Value of column ");
                msg.append(columns.getAlias(i));
                msg.append(" is ");
                msg.append(value.getType());
                msg.append(", expected ");
                msg.append(column.getType());
                throw new IllegalArgumentException(msg.toString());
            }
        }
    }

    /**
     * Checks that neither the columns nor the values are null and that there is
     * one value per column. This is the check the constructor does on every
     * record; it does not check the value types, see
     * {@link #validate(ColumnList, Value[])}.
     *
     * @param columns The columns.
     * @param values  The values.
     * @throws IllegalArgumentException If the columns or the values are null, or
     *                                  their sizes differ.
     */
    public static void checkSizes(ColumnList columns, Value[] values) {
        if (columns == null) {
            throw new IllegalArgumentException("Columns cannot be null");
        }
        if (values == null) {
            throw new IllegalArgumentException("Values cannot be null");
        }
        if (columns.size() != values.length) {
            throw new IllegalArgumentException("Columns and values must have the same size");
        }
    }

    /**
     * The column definitions, usually shared by many records.
     */
    private final ColumnList columns;
    /**
     * The values, one per column.
     */
    private final Value[] values;
    /**
     * The user properties, created when first requested.
     */
    private Properties props = null;

    /**
     * Creates a record without checking that the values match the column types,
     * see {@link #validate(ColumnList, Value[])}.
     *
     * @param columns The columns.
     * @param values  The values, one per column, used as is.
     * @throws IllegalArgumentException If the columns or the values are null, or
     *                                  their sizes differ.
     */
    public Record(ColumnList columns, Value[] values) {
        checkSizes(columns, values);
        this.columns = columns;
        this.values = values;
    }

    /**
     * Returns the column definitions, to access columns by index or alias, check
     * whether a column exists or get the index of an alias.
     *
     * @return The columns, usually shared by many records.
     */
    public ColumnList columns() {
        return columns;
    }

    /**
     * @param index The index.
     * @return The value at the index.
     * @throws IndexOutOfBoundsException If the index is out of range.
     */
    public Value getValue(int index) {
        return values[index];
    }

    /**
     * Returns the value of a column by alias. When reading many records, get the
     * index once with {@link ColumnList#indexOf(String)} and use
     * {@link #getValue(int)}, which is faster.
     *
     * @param alias The alias of the column.
     * @return The value of the column.
     * @throws IllegalArgumentException If there is no column with the alias.
     */
    public Value getValue(String alias) {
        int index = columns.indexOf(alias);
        if (index == -1) {
            StringBuilder msg = new StringBuilder();
            msg.append("Column not found: ");
            msg.append(alias);
            throw new IllegalArgumentException(msg.toString());
        }
        return values[index];
    }

    /**
     * @return The number of values, which is the number of columns.
     */
    public int size() {
        return values.length;
    }

    /**
     * @return The user properties of the record, created when first requested.
     */
    public Properties getProperties() {
        if (props == null) {
            props = new Properties();
        }
        return props;
    }
}
