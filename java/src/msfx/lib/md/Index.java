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

import msfx.lib.util.Copier;
import msfx.lib.util.Copyable;
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
 * <p>
 * Large objects can not be indexed: OBJECT columns, and STRING and BINARY
 * columns with no length or longer than {@link #MAX_INDEXABLE_LENGTH}, which
 * databases store as large objects (CLOB, BLOB, NVARCHAR(MAX), VARBINARY(MAX)).
 *
 * @author Miquel Sas
 */
public class Index implements Copyable<Index> {
    /**
     * Maximum length of an indexable STRING or BINARY column: the smallest
     * length that the supported databases store without a large object, 2000 in
     * Oracle for NVARCHAR2 and RAW.
     */
    public static final int MAX_INDEXABLE_LENGTH = 2000;

    /**
     * Container for the index properties.
     */
    private final Properties props = new Properties();

    /**
     * Empty index, to be filled by {@link #copy(Copier)}.
     */
    private Index() {
    }

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
     *                                  a column is a large object, or a column
     *                                  is already in the index or repeated in
     *                                  the names.
     */
    public void addColumns(String... columnNames) {
        ArrayList<Column> columns = columns();
        List<Column> added = new ArrayList<>();
        for (String name : columnNames) {
            Objects.requireNonNull(name, "Column name can not be null");
            Column column = getTable().columns().get(name);
            if (column == null) {
                StringBuilder msg = new StringBuilder();
                msg.append("Column ");
                msg.append(name);
                msg.append(" is not in table ");
                msg.append(getTable().getName());
                throw new IllegalArgumentException(msg.toString());
            }
            if (isLargeObject(column)) {
                StringBuilder msg = new StringBuilder();
                msg.append("Column ");
                msg.append(name);
                msg.append(" is a large object and can not be indexed");
                throw new IllegalArgumentException(msg.toString());
            }
            if (columns.contains(column) || added.contains(column)) {
                StringBuilder msg = new StringBuilder();
                msg.append("Column ");
                msg.append(name);
                msg.append(" is already in the index");
                throw new IllegalArgumentException(msg.toString());
            }
            added.add(column);
        }
        columns.addAll(added);
    }

    /**
     * @param column The column.
     * @return Whether the databases store the column as a large object, that can
     *         not be indexed.
     */
    private static boolean isLargeObject(Column column) {
        boolean largeObject = switch (column.getType()) {
            case OBJECT:
                yield true;
            case STRING:
            case BINARY:
                yield column.getLength() <= 0 || column.getLength() > MAX_INDEXABLE_LENGTH;
            case BOOLEAN:
            case DECIMAL:
            case DOUBLE:
            case INTEGER:
            case LONG:
            case DATE:
            case TIME:
            case DATETIME:
                yield false;
        };
        return largeObject;
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
     * Copies the index. The table and the columns are referred to, not owned:
     * when the table is copied in the same operation, the copy refers to the
     * copy of the table and of its columns, and otherwise to the same table and
     * columns.
     *
     * @param copier The copier of the copy operation.
     * @return The copy.
     */
    @Override
    public Index copy(Copier copier) {
        Index copy = new Index();
        copier.register(this, copy);
        for (Object key : props.keys()) {
            Object value = props.getObject(key, Object.class);
            if (key.equals("table")) {
                value = copier.resolve(value);
            } else if (key.equals("columns")) {
                ArrayList<Column> columns = new ArrayList<>();
                for (Column column : columns()) {
                    columns.add(copier.resolve(column));
                }
                value = columns;
            } else {
                value = copier.copy(value);
            }
            copy.props.putObject(key, value);
        }
        return copy;
    }

    /**
     * @return The internal list of columns.
     */
    private ArrayList<Column> columns() {
        return props.getArrayList("columns");
    }
}
