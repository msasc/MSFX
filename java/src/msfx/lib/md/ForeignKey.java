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
 * A relation from a local table to a foreign table, made of segments that each
 * pair a local column with a foreign column.
 * <p>
 * It is a non persistent description, used by other operations, so it has no
 * name nor ON DELETE or ON UPDATE rules. The local and the foreign table may be
 * the same table.
 *
 * @author Miquel Sas
 */
public class ForeignKey implements Copyable<ForeignKey> {

    /**
     * A segment of the foreign key: a local column and the foreign column it
     * refers to.
     */
    public static class Segment {
        /**
         * The column of the local table.
         */
        public final Column localColumn;
        /**
         * The column of the foreign table.
         */
        public final Column foreignColumn;

        /**
         * @param localColumn   The column of the local table.
         * @param foreignColumn The column of the foreign table.
         */
        public Segment(Column localColumn, Column foreignColumn) {
            this.localColumn = localColumn;
            this.foreignColumn = foreignColumn;
        }
    }

    /**
     * Container for the foreign key properties.
     */
    private final Properties props = new Properties();

    /**
     * Empty foreign key, to be filled by {@link #copy(Copier)}.
     */
    private ForeignKey() {
    }

    /**
     * @param localTable   The local table, that refers to the foreign table.
     * @param foreignTable The foreign table, referred to.
     * @throws NullPointerException If a table is null.
     */
    public ForeignKey(Table localTable, Table foreignTable) {
        Objects.requireNonNull(localTable, "Local table can not be null");
        Objects.requireNonNull(foreignTable, "Foreign table can not be null");
        props.putObject("local-table", localTable);
        props.putObject("foreign-table", foreignTable);
        props.put("segments", new ArrayList<Segment>());
    }

    /**
     * @return The local table, that refers to the foreign table.
     */
    public Table getLocalTable() {
        return props.getObject("local-table", Table.class);
    }

    /**
     * @return The foreign table, referred to.
     */
    public Table getForeignTable() {
        return props.getObject("foreign-table", Table.class);
    }

    /**
     * Adds a segment at the end of the foreign key.
     *
     * @param localColumnName   The name of the local column, exactly as in the
     *                          local table.
     * @param foreignColumnName The name of the foreign column, exactly as in the
     *                          foreign table.
     * @throws NullPointerException     If a name is null.
     * @throws IllegalArgumentException If a table has no column with the name.
     */
    public void addSegment(String localColumnName, String foreignColumnName) {
        Column localColumn = getColumn(getLocalTable(), localColumnName);
        Column foreignColumn = getColumn(getForeignTable(), foreignColumnName);
        segments().add(new Segment(localColumn, foreignColumn));
    }

    /**
     * @return An unmodifiable list of the segments, in the order they were added.
     */
    public List<Segment> getSegments() {
        return Collections.unmodifiableList(segments());
    }

    /**
     * @param index The index of the segment.
     * @return The segment at the index.
     * @throws IndexOutOfBoundsException If the index is out of range.
     */
    public Segment getSegment(int index) {
        return segments().get(index);
    }

    /**
     * @return The number of segments.
     */
    public int size() {
        return segments().size();
    }

    /**
     * Copies the foreign key. The tables and columns are referred to, not owned:
     * when the local table is copied in the same operation, the copy refers to
     * the copy of the local table and of its columns, and so for the foreign
     * table. A foreign table not copied, the usual case, is shared.
     *
     * @param copier The copier of the copy operation.
     * @return The copy.
     */
    @Override
    public ForeignKey copy(Copier copier) {
        ForeignKey copy = new ForeignKey();
        copier.register(this, copy);
        for (Object key : props.keys()) {
            Object value = props.getObject(key, Object.class);
            if (key.equals("local-table") || key.equals("foreign-table")) {
                value = copier.resolve(value);
            } else if (key.equals("segments")) {
                ArrayList<Segment> segments = new ArrayList<>();
                for (Segment segment : segments()) {
                    segments.add(new Segment(
                            copier.resolve(segment.localColumn), copier.resolve(segment.foreignColumn)));
                }
                value = segments;
            } else {
                value = copier.copy(value);
            }
            copy.props.putObject(key, value);
        }
        return copy;
    }

    /**
     * @return The internal list of segments.
     */
    private ArrayList<Segment> segments() {
        return props.getArrayList("segments");
    }

    /**
     * @param table The table.
     * @param name  The column name.
     * @return The column of the table with that name.
     * @throws NullPointerException     If the name is null.
     * @throws IllegalArgumentException If the table has no column with the name.
     */
    private static Column getColumn(Table table, String name) {
        Objects.requireNonNull(name, "Column name can not be null");
        Column column = table.columns().get(name);
        if (column == null) {
            StringBuilder msg = new StringBuilder();
            msg.append("Column ");
            msg.append(name);
            msg.append(" is not in table ");
            msg.append(table.getName());
            throw new IllegalArgumentException(msg.toString());
        }
        return column;
    }
}
