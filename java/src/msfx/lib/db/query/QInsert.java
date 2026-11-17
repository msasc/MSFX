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

package msfx.lib.db.query;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBEngine;
import msfx.lib.db.DBPreparedStatement;
import msfx.lib.db.query.sa.Analysis;
import msfx.lib.db.query.sa.Clause;
import msfx.lib.md.Column;
import msfx.lib.md.ColumnList;
import msfx.lib.md.Index;
import msfx.lib.md.Record;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import msfx.lib.md.Value;

/**
 * INSERT statement into a single table, built as it is written:
 * 
 * <pre>
 * QInsert insert = new QInsert(db);
 * insert.into(table);
 * insert.columns(...);
 * insert.values(...);
 * insert.execute();
 * </pre>
 * 
 * The columns are always stated: all the columns of the table, or a list of
 * them. Each call to a columns method replaces the list, and the columns are
 * fixed once a row or a select is set.
 * <p>
 * The rows inserted come from a row of expressions, a row of values or a
 * select. Each call to values or select replaces the previous row or select,
 * so the statement is reused by setting a new row and executing it again.
 * Only the structure of the statement is validated; the types and nulls of
 * the data are checked by the database.
 *
 * @author Miquel Sas
 */
public class QInsert extends QBase {

	/**
	 * The table to insert into, or null while it is not set.
	 */
	private Table table;
	/**
	 * Columns of the statement, in order, by their name in upper case, so that a
	 * repeated column is found in constant time. Tables can have hundreds of
	 * columns.
	 */
	private final Map<String, Column> columns = new LinkedHashMap<>();
	/**
	 * The row to insert as validated expressions, one per column, or empty.
	 */
	private final List<Analysis> expressions = new ArrayList<>();
	/**
	 * The row to insert as values, one per column, or empty.
	 */
	private final List<Value> values = new ArrayList<>();
	/**
	 * The select whose rows are inserted, or null. Only one of the row of
	 * expressions, the row of values and the select is set.
	 */
	private QSelect select = null;
	/**
	 * The column list of the records inserted with {@link #execute(Record)}, or
	 * null while the insert is not used with records. Records that share it are
	 * inserted without checking their columns again.
	 */
	private ColumnList recordColumns = null;

	/**
	 * Constructor.
	 *
	 * @param db The database engine.
	 * @throws NullPointerException If the engine is null.
	 */
	public QInsert(DBEngine db) {
		super(db);
	}

	/**
	 * Sets the table to insert into.
	 *
	 * @param table The table.
	 * @throws NullPointerException     If the table is null.
	 * @throws IllegalArgumentException If the table has no columns.
	 * @throws IllegalStateException    If the table is already set.
	 */
	public void into(Table table) {
		if (this.table != null) {
			throw new IllegalStateException("The table is already set");
		}
		Objects.requireNonNull(table, "Table can not be null");
		if (table.columns().isEmpty()) {
			StringBuilder msg = new StringBuilder();
			msg.append("Table ");
			msg.append(table.getName());
			msg.append(" has no columns");
			throw new IllegalArgumentException(msg.toString());
		}
		this.table = table;
	}

	/**
	 * Sets all the columns of the table, in the order of the table, replacing
	 * the current list.
	 *
	 * @throws IllegalStateException If the table is not set, or values have
	 *                               already been added.
	 */
	public void columns() {
		checkColumnsAllowed();
		columns.clear();
		for (Column column : table.columns()) {
			columns.put(toKey(column.getName()), column);
		}
	}

	/**
	 * Sets the columns, in the order given, replacing the current list.
	 *
	 * @param columns The columns, at least one, all of the table.
	 * @throws NullPointerException     If the columns or one of them is null.
	 * @throws IllegalArgumentException If there are no columns, or a column does
	 *                                  not belong to the table or is repeated.
	 * @throws IllegalStateException    If the table is not set, or values have
	 *                                  already been added.
	 */
	public void columns(Column... columns) {
		checkColumnsAllowed();
		Objects.requireNonNull(columns, "Columns can not be null");
		if (columns.length == 0) {
			throw new IllegalArgumentException("At least one column is required, columns() sets all of them");
		}
		Map<String, Column> list = new LinkedHashMap<>();
		for (Column column : columns) {
			Objects.requireNonNull(column, "Column can not be null");
			if (!table.equals(column.getTable())) {
				StringBuilder msg = new StringBuilder();
				msg.append("Column ");
				msg.append(column.getName());
				msg.append(" does not belong to table ");
				msg.append(table.getName());
				throw new IllegalArgumentException(msg.toString());
			}
			putColumn(list, column);
		}
		this.columns.clear();
		this.columns.putAll(list);
	}

	/**
	 * Sets the columns by name, ignoring case, in the order given, replacing the
	 * current list.
	 *
	 * @param columnNames The names of the columns, at least one, all of the
	 *                    table.
	 * @throws NullPointerException     If the names or one of them is null.
	 * @throws IllegalArgumentException If there are no names, or a name is not a
	 *                                  column of the table or is repeated.
	 * @throws IllegalStateException    If the table is not set, or values have
	 *                                  already been added.
	 */
	public void columns(String... columnNames) {
		checkColumnsAllowed();
		Objects.requireNonNull(columnNames, "Column names can not be null");
		if (columnNames.length == 0) {
			throw new IllegalArgumentException("At least one column is required, columns() sets all of them");
		}
		Map<String, Column> list = new LinkedHashMap<>();
		for (String name : columnNames) {
			Objects.requireNonNull(name, "Column name can not be null");
			Column column = table.columns().getIgnoreCase(name);
			if (column == null) {
				StringBuilder msg = new StringBuilder();
				msg.append("Column ");
				msg.append(name);
				msg.append(" not found in table ");
				msg.append(table.getName());
				throw new IllegalArgumentException(msg.toString());
			}
			putColumn(list, column);
		}
		this.columns.clear();
		this.columns.putAll(list);
	}

	/**
	 * Sets the row to insert as expressions, one per column and in their order,
	 * replacing the current row or select. The expressions have no tables, so
	 * they use no columns, and have no aggregates nor placeholders: literals,
	 * functions, arithmetic and CASE, as in <code>UPPER('abc')</code> or
	 * <code>CURRENT_TIMESTAMP</code>. They are validated at the call, and
	 * written as literal SQL.
	 * <p>
	 * BINARY and OBJECT columns have no literal: their rows are set with
	 * {@link #values(Value...)}.
	 *
	 * @param expressions The expressions.
	 * @throws NullPointerException                 If the expressions or one of
	 *                                              them is null.
	 * @throws IllegalArgumentException             If the number of expressions
	 *                                              is not the number of columns,
	 *                                              or a column is BINARY or
	 *                                              OBJECT.
	 * @throws IllegalStateException                If the columns are not set.
	 * @throws msfx.lib.db.query.sa.SyntaxException If an expression is wrong.
	 */
	public void values(String... expressions) {
		checkSourceAllowed();
		Objects.requireNonNull(expressions, "Expressions can not be null");
		checkCount(expressions.length, "expressions");
		for (Column column : columns.values()) {
			Types type = column.getType();
			if (type == Types.BINARY || type == Types.OBJECT) {
				StringBuilder msg = new StringBuilder();
				msg.append("Column ");
				msg.append(column.getName());
				msg.append(" is ");
				msg.append(type);
				msg.append(", without literal: use values(Value...)");
				throw new IllegalArgumentException(msg.toString());
			}
		}
		List<Analysis> row = new ArrayList<>();
		for (String expression : expressions) {
			Objects.requireNonNull(expression, "Expression can not be null");
			Analysis analysis = Analysis.analyze(expression, Clause.VALUES, Map.of(), List.of(), List.of());
			row.add(analysis);
		}
		clearSource();
		this.expressions.addAll(row);
	}

	/**
	 * Sets the row to insert as values, one per column and in their order,
	 * replacing the current row or select. The values are not validated: their
	 * types and nulls are checked by the database. The row is inserted with a
	 * prepared statement, so that BINARY and OBJECT values are accepted.
	 *
	 * @param values The values.
	 * @throws NullPointerException     If the values or one of them is null.
	 * @throws IllegalArgumentException If the number of values is not the number
	 *                                  of columns.
	 * @throws IllegalStateException    If the columns are not set.
	 */
	public void values(Value... values) {
		checkSourceAllowed();
		Objects.requireNonNull(values, "Values can not be null");
		checkCount(values.length, "values");
		for (Value value : values) {
			Objects.requireNonNull(value, "Value can not be null, use a null value of the type");
		}
		clearSource();
		this.values.addAll(Arrays.asList(values));
	}

	/**
	 * Sets the select whose rows are inserted, replacing the current row. The
	 * select is kept as it is, and is written when the insert is.
	 *
	 * @param select The select, complete, without ORDER BY, with one column per
	 *               column of the insert.
	 * @throws NullPointerException     If the select is null.
	 * @throws IllegalArgumentException If the select has another engine, an
	 *                                  ORDER BY clause, or another number of
	 *                                  columns.
	 * @throws IllegalStateException    If the columns are not set, or the select
	 *                                  can not be executed.
	 */
	public void select(QSelect select) {
		checkSourceAllowed();
		Objects.requireNonNull(select, "Select can not be null");
		select.checkNested(getDBEngine());
		checkCount(select.getColumnCount(), "select columns");
		clearSource();
		this.select = select;
	}

	/**
	 * Inserts a record into the table its columns belong to, on its own pooled
	 * connection, with a prepared statement. A single record is inserted with
	 * <code>new QInsert(db).execute(record)</code>.
	 * <p>
	 * The insert takes its table and its columns from the first record: all its
	 * columns must belong to the same table, and the primary key columns must be
	 * present. The following records must have the same columns, in the same
	 * order; those that share the column list of the previous one, as the
	 * records of a read do, are inserted without checking their columns again.
	 * An insert used with records accepts no table, columns, row or select set
	 * by hand, and the other way round.
	 *
	 * @param record The record.
	 * @return The number of rows inserted.
	 * @throws NullPointerException     If the record is null.
	 * @throws IllegalArgumentException If the first record has no columns, a
	 *                                  column of no table or of another table,
	 *                                  or misses a primary key column, or a
	 *                                  later record has other columns.
	 * @throws IllegalStateException    If the table or the columns were set by
	 *                                  hand.
	 * @throws SQLException             If a database access error occurs.
	 */
	public int execute(Record record) throws SQLException {
		Objects.requireNonNull(record, "Record can not be null");
		ColumnList list = record.columns();
		if (list != recordColumns) {
			if (recordColumns == null) {
				setFromRecord(list);
			} else {
				checkSameColumns(list);
			}
			recordColumns = list;
		}
		values.clear();
		int size = record.size();
		for (int i = 0; i < size; i++) {
			Value value = record.getValue(i);
			values.add(value);
		}
		return execute();
	}

	/**
	 * Executes the statement on its own pooled connection: a row of values with
	 * a prepared statement, a row of expressions or a select as SQL.
	 *
	 * @return The number of rows inserted.
	 * @throws IllegalStateException If the table, the columns or the row or
	 *                               select to insert are not set.
	 * @throws SQLException          If a database access error occurs.
	 */
	public int execute() throws SQLException {
		if (table == null) {
			throw new IllegalStateException("The table is not set");
		}
		if (columns.isEmpty()) {
			throw new IllegalStateException("The columns are not set");
		}
		if (values.isEmpty() && expressions.isEmpty() && select == null) {
			throw new IllegalStateException("There is no row nor select to insert");
		}
		if (values.isEmpty()) {
			String sql = toSQL();
			return executeUpdate(sql);
		}
		String sql = toSQL();
		DBEngine db = getDBEngine();
		try (DBConnection cn = db.getConnection();
				DBPreparedStatement ps = cn.prepareStatement(sql)) {
			for (int i = 0; i < values.size(); i++) {
				db.setValue(ps, i + 1, values.get(i));
			}
			return ps.executeUpdate();
		}
	}

	/**
	 * Checks that the columns can be set: the table is set and there is no row
	 * nor select, whose positions follow the columns.
	 *
	 * @throws IllegalStateException If the columns can not be set.
	 */
	private void checkColumnsAllowed() {
		if (table == null) {
			throw new IllegalStateException("The table must be set before the columns");
		}
		if (recordColumns != null) {
			throw new IllegalStateException("The insert is used with records, its columns come from them");
		}
		if (!values.isEmpty() || !expressions.isEmpty() || select != null) {
			throw new IllegalStateException("The columns can not be changed once a row or a select is set");
		}
	}

	/**
	 * Checks that a row or a select can be set: the columns are set, and the
	 * insert is not used with records.
	 *
	 * @throws IllegalStateException If the row or the select can not be set.
	 */
	private void checkSourceAllowed() {
		if (recordColumns != null) {
			throw new IllegalStateException("The insert is used with records, their values are the rows");
		}
		if (columns.isEmpty()) {
			throw new IllegalStateException("The columns must be set before the row or the select");
		}
	}

	/**
	 * Sets the table and the columns from the column list of the first record:
	 * the columns must belong all to the same table, and the primary key
	 * columns of the table, if it has one, must be present.
	 *
	 * @param list The column list of the record.
	 * @throws IllegalArgumentException If the record has no columns, a column
	 *                                  belongs to no table or to another one, a
	 *                                  column is repeated, or a column of the
	 *                                  primary key is missing.
	 * @throws IllegalStateException    If the table or the columns were set by
	 *                                  hand.
	 */
	private void setFromRecord(ColumnList list) {
		if (table != null || !columns.isEmpty()) {
			throw new IllegalStateException("The table and the columns were set by hand, execute(Record) takes them from the records");
		}
		if (list.isEmpty()) {
			throw new IllegalArgumentException("The record has no columns");
		}
		Table recordTable = list.get(0).getTable();
		Map<String, Column> collected = new LinkedHashMap<>();
		for (Column column : list) {
			Table columnTable = column.getTable();
			if (columnTable == null || !columnTable.equals(recordTable)) {
				StringBuilder msg = new StringBuilder();
				msg.append("Column ");
				msg.append(column.getName());
				msg.append(" of the record belongs to ");
				if (columnTable == null) {
					msg.append("no table");
				} else {
					msg.append("table ");
					msg.append(columnTable.getName());
				}
				msg.append(": all the columns must belong to the same table");
				throw new IllegalArgumentException(msg.toString());
			}
			putColumn(collected, column);
		}
		Index primaryKey = recordTable.getPrimaryKey();
		if (primaryKey != null) {
			for (Column column : primaryKey.getColumns()) {
				if (!collected.containsKey(toKey(column.getName()))) {
					StringBuilder msg = new StringBuilder();
					msg.append("The record lacks column ");
					msg.append(column.getName());
					msg.append(" of the primary key of table ");
					msg.append(recordTable.getName());
					throw new IllegalArgumentException(msg.toString());
				}
			}
		}
		into(recordTable);
		columns.putAll(collected);
	}

	/**
	 * Checks that the column list of a record has the same columns as the one
	 * of the previous records, in the same order.
	 *
	 * @param list The column list of the record.
	 * @throws IllegalArgumentException If the columns differ.
	 */
	private void checkSameColumns(ColumnList list) {
		boolean same = list.size() == columns.size();
		Iterator<Column> previous = columns.values().iterator();
		for (int i = 0; same && i < list.size(); i++) {
			Column column = list.get(i);
			Column other = previous.next();
			same = column.equals(other);
		}
		if (!same) {
			throw new IllegalArgumentException("The record has other columns than the previous ones, use a new QInsert");
		}
	}

	/**
	 * Checks that a row or a select has one item per column.
	 *
	 * @param count The number of items.
	 * @param what  What the items are, for the error message.
	 * @throws IllegalArgumentException If the number is not the number of
	 *                                  columns.
	 */
	private void checkCount(int count, String what) {
		if (count != columns.size()) {
			StringBuilder msg = new StringBuilder();
			msg.append(count);
			msg.append(" ");
			msg.append(what);
			msg.append(" for ");
			msg.append(columns.size());
			msg.append(" columns");
			throw new IllegalArgumentException(msg.toString());
		}
	}

	/**
	 * Clears the row and the select, before setting a new one.
	 */
	private void clearSource() {
		values.clear();
		expressions.clear();
		select = null;
	}

	/**
	 * Adds a column to a list being built, rejecting a repeated one.
	 *
	 * @param list   The list, by name in upper case.
	 * @param column The column.
	 * @throws IllegalArgumentException If the column is already in the list.
	 */
	private static void putColumn(Map<String, Column> list, Column column) {
		String key = toKey(column.getName());
		if (list.containsKey(key)) {
			StringBuilder msg = new StringBuilder();
			msg.append("Column ");
			msg.append(column.getName());
			msg.append(" is repeated");
			throw new IllegalArgumentException(msg.toString());
		}
		list.put(key, column);
	}

	/**
	 * Returns the key of a column name: the name in upper case.
	 *
	 * @param name The name.
	 * @return The key.
	 */
	private static String toKey(String name) {
		return name.toUpperCase(Locale.ROOT);
	}

	/**
	 * Returns the statement as far as it is built: INSERT INTO table (columns),
	 * then VALUES with the expressions or a placeholder per value, or the
	 * select.
	 *
	 * @return The SQL, empty while the table is not set.
	 */
	private String toSQL() {
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		if (table == null) {
			return sql.toString();
		}
		sql.append("INSERT INTO ");
		sql.append(getQualifiedName(table));
		if (!columns.isEmpty()) {
			sql.append(" (");
			boolean first = true;
			for (Column column : columns.values()) {
				if (!first) {
					sql.append(", ");
				}
				sql.append(db.quote(column.getName()));
				first = false;
			}
			sql.append(")");
		}
		if (!expressions.isEmpty()) {
			sql.append("\nVALUES (");
			for (int i = 0; i < expressions.size(); i++) {
				if (i > 0) {
					sql.append(", ");
				}
				sql.append(expressions.get(i).toSQL(db));
			}
			sql.append(")");
		}
		if (!values.isEmpty()) {
			sql.append("\nVALUES (");
			for (int i = 0; i < values.size(); i++) {
				if (i > 0) {
					sql.append(", ");
				}
				sql.append("?");
			}
			sql.append(")");
		}
		if (select != null) {
			sql.append("\n");
			sql.append(select.toString());
		}
		return sql.toString();
	}

	/**
	 * Returns the statement as far as it is built, as {@link #toSQL()} writes
	 * it, followed for a row of values by one comment line per value, as
	 * <code>-- 1: A325</code>.
	 *
	 * @return The SQL.
	 */
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(toSQL());
		for (int i = 0; i < values.size(); i++) {
			Value value = values.get(i);
			b.append("\n-- ");
			b.append(i + 1);
			b.append(": ");
			if (value.isBinary() && !value.isNull()) {
				b.append("BINARY, ");
				b.append(value.getBinary().length);
				b.append(" bytes");
			} else {
				b.append(value);
			}
		}
		return b.toString();
	}
}
