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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import msfx.lib.util.Properties;

/**
 * An index of a table.
 * <p>
 * An index belongs to the table given when it is created, and is made of a
 * list of columns of that table, not repeated. It is
 * assigned to the table either as its primary key, see
 * {@link Table#setPrimaryKey(Index)}, or as a secondary index, see
 * {@link Table#addIndex(Index)}. The table then sets whether it is unique, and
 * its name if not set.
 *
 * @author Miquel Sas
 */
public class Index {
    /**
     * Container for the index properties.
     */
    private final Properties props = new Properties();

    /**
     * @param table The table the index belongs to.
     * @throws NullPointerException If the table is null.
     */
    public Index(Table table) {
        Objects.requireNonNull(table, "Table can not be null");
        props.putObject("table", table);
        props.put("columns", new ArrayList<Column>());
    }

    /**
     * @return The table the index belongs to.
     */
    public Table getTable() {
        return props.getObject("table", Table.class);
    }

    /**
     * @return The name of the index, or null if not set yet. When assigned to the
     *         table without a name, the table sets one.
     */
    public String getName() {
        return props.getString("name", null);
    }

    /**
     * @param name The name of the index.
     * @throws NullPointerException     If the name is null.
     * @throws IllegalArgumentException If the name is blank.
     */
    public void setName(String name) {
        Objects.requireNonNull(name, "Name can not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Name can not be blank");
        }
        props.put("name", name);
    }

    /**
     * @return Whether the index is unique, which is true only for the primary
     *         key.
     */
    public boolean isUnique() {
        return props.getBoolean("unique", false);
    }

    /**
     * Sets whether the index is unique. Only called by the table when the index
     * is assigned to it.
     *
     * @param unique Whether the index is unique.
     */
    void setUnique(boolean unique) {
        props.put("unique", unique);
    }

    /**
     * Adds columns at the end of the index, in the order given. Either all the
     * columns are added or, if any name is invalid, none.
     *
     * @param columnNames The names of the columns, exactly as in the table.
     * @throws NullPointerException     If a name is null.
     * @throws IllegalArgumentException If the table has no column with a name,
     *                                  or a column is already in the index or
     *                                  repeated in the names.
     */
    public void addColumns(String... columnNames) {
        ArrayList<Column> columns = columns();
        List<Column> added = new ArrayList<>();
        for (String name : columnNames) {
            Objects.requireNonNull(name, "Column name can not be null");
            Column column = getTable().columns().get(name);
            if (column == null) {
                throw new IllegalArgumentException("Column " + name + " is not in table " + getTable().getName());
            }
            if (columns.contains(column) || added.contains(column)) {
                throw new IllegalArgumentException("Column " + name + " is already in the index");
            }
            added.add(column);
        }
        columns.addAll(added);
    }

    /**
     * @return An unmodifiable list of the columns, in index order.
     */
    public List<Column> getColumns() {
        return Collections.unmodifiableList(columns());
    }

    /**
     * @return The number of columns of the index.
     */
    public int size() {
        return columns().size();
    }

    /**
     * @return The internal list of columns.
     */
    private ArrayList<Column> columns() {
        return props.getArrayList("columns");
    }
}
