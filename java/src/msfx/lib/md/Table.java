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
 * A database table, with its columns and indexes.
 * <p>
 * A column belongs to one table only: when added, the table becomes its owner,
 * and adding it to another table is rejected. Columns are added under their
 * name, never under a different alias, and names must be unique in the table,
 * ignoring case.
 * <p>
 * To avoid incompatibilities among databases, the only unique index is the
 * primary key; secondary indexes are never unique. Index names must be unique
 * in the table, ignoring case, and two indexes can not have the same columns
 * in the same order. An index not named when assigned is named after the
 * table: TABLE_PK for the primary key, and TABLE_SKn for the secondary
 * indexes.
 *
 * @author Miquel Sas
 */
public class Table implements Copyable<Table> {
    /**
     * The columns of the table.
     */
    private final ColumnList columns = new ColumnList();
    /**
     * Container for the table properties.
     */
    private final Properties props = new Properties();

    /**
     * Empty table, to be filled by {@link #copy(Copier)}.
     */
    private Table() {
        columns.setInterceptor((alias, column) -> intercept(alias, column));
    }

    /**
     * @param name The name of the table.
     * @throws NullPointerException If the name is null.
     */
    public Table(String name) {
        this();
        Objects.requireNonNull(name, "Name can not be null");
        props.put("name", name);
    }

    /**
     * Copies the table with its columns, primary key, indexes, foreign keys and
     * all its properties. The copies of the indexes and foreign keys refer to
     * the copies of the columns. The foreign tables of the foreign keys are not
     * copied, they are shared, except when the foreign table is this table.
     *
     * @param copier The copier of the copy operation.
     * @return The copy.
     */
    @Override
    public Table copy(Copier copier) {
        Table copy = new Table();
        copier.register(this, copy);
        /* The columns first, so the indexes and foreign keys resolve to their copies. */
        for (Column column : columns) {
            copy.columns.add(copier.copy(column));
        }
        for (Object key : props.keys()) {
            copy.props.putObject(key, copier.copy(props.getObject(key, Object.class)));
        }
        return copy;
    }

    /**
     * @return The name of the table.
     */
    public String getName() {
        return props.getString("name");
    }

    /**
     * @return The schema of the table, or null if not set. It is not set when
     *         the table is defined, but when it is accessed through the catalog.
     */
    public String getSchema() {
        return props.getString("schema", null);
    }

    /**
     * @param schema The schema of the table.
     * @throws NullPointerException If the schema is null.
     */
    public void setSchema(String schema) {
        Objects.requireNonNull(schema, "Schema can not be null");
        props.put("schema", schema);
    }

    /**
     * @return The columns of the table, to add and access them.
     */
    public ColumnList columns() {
        return columns;
    }

    /**
     * Called by the column list before a column is added. Rejects the column if
     * its alias is not its name or it already belongs to a table, and otherwise
     * makes this table its owner. Repeated names, also ignoring case, are
     * already rejected by the column list, since the alias is the name.
     *
     * @param alias  The alias the column is about to be added under.
     * @param column The column about to be added.
     * @throws IllegalArgumentException If the alias is not the column name, or
     *                                  the column already belongs to a table.
     */
    private void intercept(String alias, Column column) {
        if (!alias.equals(column.getName())) {
            StringBuilder msg = new StringBuilder();
            msg.append("Column ");
            msg.append(column.getName());
            msg.append(" can not be added to table ");
            msg.append(getName());
            msg.append(" under a different alias: ");
            msg.append(alias);
            throw new IllegalArgumentException(msg.toString());
        }
        if (column.getTable() != null) {
            StringBuilder msg = new StringBuilder();
            msg.append("Column ");
            msg.append(column.getName());
            msg.append(" already belongs to table ");
            msg.append(column.getTable().getName());
            throw new IllegalArgumentException(msg.toString());
        }
        column.setTable(this);
    }

    /**
     * @return The primary key, or null if not set.
     */
    public Index getPrimaryKey() {
        return props.getObject("primary-key", Index.class);
    }

    /**
     * Sets the primary key, replacing the current one if any. The index becomes
     * unique, and is named TABLE_PK if it has no name. A replaced primary key is
     * no longer unique.
     *
     * @param index The index to set as primary key.
     * @throws NullPointerException     If the index is null.
     * @throws IllegalArgumentException If the index belongs to another table, has
     *                                  no columns, has the name or the columns of
     *                                  another index, or any of its columns is
     *                                  nullable.
     */
    public void setPrimaryKey(Index index) {
        Objects.requireNonNull(index, "Index can not be null");
        Index current = getPrimaryKey();
        if (index == current) {
            return;
        }
        String name = index.getName();
        if (name == null) {
            StringBuilder pkName = new StringBuilder();
            pkName.append(getName());
            pkName.append("_PK");
            name = pkName.toString();
        }
        validateIndex(index, name, current);
        for (Column column : index.getColumns()) {
            if (column.isNullable()) {
                StringBuilder msg = new StringBuilder();
                msg.append("Column ");
                msg.append(column.getName());
                msg.append(" of the primary key of table ");
                msg.append(getName());
                msg.append(" is nullable");
                throw new IllegalArgumentException(msg.toString());
            }
        }
        if (index.getName() == null) {
            index.setName(name);
        }
        index.setUnique(true);
        if (current != null) {
            current.setUnique(false);
        }
        props.putObject("primary-key", index);
    }

    /**
     * Adds a secondary index. The index is not unique, and is named TABLE_SKn if
     * it has no name, where n is its position among the secondary indexes, or
     * the next free number.
     *
     * @param index The index to add.
     * @throws NullPointerException     If the index is null.
     * @throws IllegalArgumentException If the index belongs to another table, has
     *                                  no columns, or has the name or the columns
     *                                  of another index.
     */
    public void addIndex(Index index) {
        Objects.requireNonNull(index, "Index can not be null");
        String name = index.getName() != null ? index.getName() : nextSecondaryName();
        validateIndex(index, name, null);
        if (index.getName() == null) {
            index.setName(name);
        }
        index.setUnique(false);
        indexes().add(index);
    }

    /**
     * @return An unmodifiable list of the secondary indexes, in the order they
     *         were added.
     */
    public List<Index> getIndexes() {
        ArrayList<Index> indexes = props.getArrayList("indexes");
        if (indexes == null) {
            return List.of();
        }
        return Collections.unmodifiableList(indexes);
    }

    /**
     * @return The internal list of secondary indexes, created if needed.
     */
    private ArrayList<Index> indexes() {
        ArrayList<Index> indexes = props.getArrayList("indexes");
        if (indexes == null) {
            indexes = new ArrayList<>();
            props.put("indexes", indexes);
        }
        return indexes;
    }

    /**
     * Checks that an index can be assigned to this table.
     *
     * @param index    The index.
     * @param name     The name the index will have.
     * @param replaced The index it replaces, excluded from the comparisons, or
     *                 null.
     * @throws IllegalArgumentException If the index can not be assigned.
     */
    private void validateIndex(Index index, String name, Index replaced) {
        if (index.getTable() != this) {
            StringBuilder msg = new StringBuilder();
            msg.append("Index ");
            msg.append(name);
            msg.append(" belongs to table ");
            msg.append(index.getTable().getName());
            msg.append(", not to ");
            msg.append(getName());
            throw new IllegalArgumentException(msg.toString());
        }
        if (index.size() == 0) {
            StringBuilder msg = new StringBuilder();
            msg.append("Index ");
            msg.append(name);
            msg.append(" has no columns");
            throw new IllegalArgumentException(msg.toString());
        }
        for (Index other : getAllIndexes()) {
            if (other == replaced) {
                continue;
            }
            if (other.getName().equalsIgnoreCase(name)) {
                StringBuilder msg = new StringBuilder();
                msg.append("Index name ");
                msg.append(name);
                msg.append(" is already used by index ");
                msg.append(other.getName());
                throw new IllegalArgumentException(msg.toString());
            }
            if (other.getColumns().equals(index.getColumns())) {
                StringBuilder msg = new StringBuilder();
                msg.append("Index ");
                msg.append(name);
                msg.append(" has the same columns as index ");
                msg.append(other.getName());
                throw new IllegalArgumentException(msg.toString());
            }
        }
    }

    /**
     * @return The name for a new secondary index: TABLE_SKn, where n is the
     *         position the index will have, or the next number not used.
     */
    private String nextSecondaryName() {
        int n = getIndexes().size() + 1;
        while (true) {
            StringBuilder skName = new StringBuilder();
            skName.append(getName());
            skName.append("_SK");
            skName.append(n);
            String name = skName.toString();
            boolean used = false;
            for (Index index : getAllIndexes()) {
                if (index.getName().equalsIgnoreCase(name)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return name;
            }
            n++;
        }
    }

    /**
     * @return An unmodifiable list with the primary key, if set, followed by the
     *         secondary indexes, in the order they were added.
     */
    public List<Index> getAllIndexes() {
        List<Index> indexes = new ArrayList<>();
        Index primaryKey = getPrimaryKey();
        if (primaryKey != null) {
            indexes.add(primaryKey);
        }
        indexes.addAll(getIndexes());
        return Collections.unmodifiableList(indexes);
    }

    /**
     * Adds a foreign key whose local table is this table.
     *
     * @param foreignKey The foreign key.
     * @throws NullPointerException     If the foreign key is null.
     * @throws IllegalArgumentException If the local table of the foreign key is
     *                                  not this table.
     */
    public void addForeignKey(ForeignKey foreignKey) {
        Objects.requireNonNull(foreignKey, "Foreign key can not be null");
        if (foreignKey.getLocalTable() != this) {
            StringBuilder msg = new StringBuilder();
            msg.append("The local table of the foreign key is ");
            msg.append(foreignKey.getLocalTable().getName());
            msg.append(", not ");
            msg.append(getName());
            throw new IllegalArgumentException(msg.toString());
        }
        ArrayList<ForeignKey> foreignKeys = props.getArrayList("foreign-keys");
        if (foreignKeys == null) {
            foreignKeys = new ArrayList<>();
            props.put("foreign-keys", foreignKeys);
        }
        foreignKeys.add(foreignKey);
    }

    /**
     * @return An unmodifiable list of the foreign keys, in the order they were
     *         added.
     */
    public List<ForeignKey> foreignKeys() {
        ArrayList<ForeignKey> foreignKeys = props.getArrayList("foreign-keys");
        if (foreignKeys == null) {
            return List.of();
        }
        return Collections.unmodifiableList(foreignKeys);
    }

    /**
     * @return The user properties of the table.
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
     * @return The title of the table, or an empty string if not set.
     */
    public String getTitle() {
        return props.getString("title", "");
    }

    /**
     * @param title The title of the table.
     * @throws NullPointerException If the title is null.
     */
    public void setTitle(String title) {
        Objects.requireNonNull(title, "Title can not be null");
        props.put("title", title);
    }

    /**
     * @return The description of the table, or the title if not set.
     */
    public String getDescription() {
        return props.getString("description", getTitle());
    }

    /**
     * @param description The description of the table.
     * @throws NullPointerException If the description is null.
     */
    public void setDescription(String description) {
        Objects.requireNonNull(description, "Description can not be null");
        props.put("description", description);
    }
}
