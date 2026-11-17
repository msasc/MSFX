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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import msfx.lib.md.Value;

/**
 * UPDATE statement on a single table, built as it is written:
 *
 * <pre>
 * QUpdate update = new QUpdate(db);
 * update.table(table, "P");
 * update.set(table.columns().get("DPTYPE"), "UPPER(P.DPTYPE)");
 * update.set(table.columns().get("ISPAREPART"), new Value(true));
 * QWhere where = update.getWhere();
 * where.cond("P.CBUSINESS = ?", value);
 * update.execute();
 * </pre>
 *
 * Aimed at updating blocks of rows with a WHERE clause, on any table; without
 * conditions, all the rows of the table are updated. The alias is required,
 * so that the expressions, the conditions and their EXISTS selects name the
 * table updated.
 * <p>
 * Each column of the SET list gets an expression, validated and written as
 * SQL, or a value, sent as a parameter of a prepared statement, so that
 * BINARY and OBJECT values are accepted; both kinds can be mixed. The columns
 * of the primary key can be set too: the database checks the key.
 * <p>
 * A single row is updated through its record, by the primary key of its
 * table, with <code>new QUpdate(db).execute(record)</code>. An update is built
 * by hand or used with records, never both.
 *
 * @author Miquel Sas
 */
public class QUpdate extends QBase {

	/**
	 * An assignment of the SET list: the column and its expression or its
	 * value, only one of them set.
	 *
	 * @param column     The column of the table.
	 * @param expression The validated expression, or null.
	 * @param value      The value, or null.
	 */
	private record Assignment(Column column, Analysis expression, Value value) {
	}

	/**
	 * The table updated, with its alias, or null while it is not set or the
	 * update is used with records.
	 */
	private QFrom from = null;
	/**
	 * The table updated, or null while it is not set.
	 */
	private Table table = null;
	/**
	 * The alias of the table updated, or null while it is not set.
	 */
	private String alias = null;
	/**
	 * Tables used, by alias: the table updated, and those of the outer
	 * statements seen by the EXISTS selects of the WHERE clause.
	 */
	private final QScope scope;
	/**
	 * The SET list, in the order given, by the name of the column in upper
	 * case, so that a repeated column is found in constant time.
	 */
	private final Map<String, Assignment> assignments = new LinkedHashMap<>();
	/**
	 * WHERE conditions, set by hand.
	 */
	private final QWhere where;
	/**
	 * The column list of the records updated with {@link #execute(Record)}, or
	 * null while the update is not used with records. Later records must share
	 * it.
	 */
	private ColumnList recordColumns = null;
	/**
	 * The positions in the records of the columns of the SET list, set with the
	 * first record.
	 */
	private final List<Integer> setPositions = new ArrayList<>();
	/**
	 * The positions in the records of the columns of the primary key, in the
	 * order of the key, set with the first record.
	 */
	private final List<Integer> keyPositions = new ArrayList<>();
	/**
	 * The statement of the records, UPDATE table SET col1 = ?, ... WHERE key1 =
	 * ? AND ..., built with the first record.
	 */
	private String recordSQL = null;
	/**
	 * The values sent with the last record, the SET values then the key, for
	 * toString.
	 */
	private final List<Value> recordValues = new ArrayList<>();

	/**
	 * Constructor.
	 *
	 * @param db The database engine.
	 * @throws NullPointerException If the engine is null.
	 */
	public QUpdate(DBEngine db) {
		super(db);
		scope = new QScope(null);
		where = new QWhere(db, scope);
	}

	/**
	 * Sets the table to update, and registers it under its alias for the
	 * expressions and the conditions.
	 *
	 * @param table The table.
	 * @param alias The alias used in the expressions and the conditions.
	 * @throws NullPointerException     If the table or the alias is null.
	 * @throws IllegalArgumentException If the alias is blank.
	 * @throws IllegalStateException    If the table is already set, or the
	 *                                  update is used with records.
	 */
	public void table(Table table, String alias) {
		checkByHand();
		if (from != null) {
			throw new IllegalStateException("The table is already set");
		}
		scope.checkTable(table, alias);
		from = new QFrom(getDBEngine(), table, alias);
		this.table = table;
		this.alias = alias;
		scope.put(alias, table);
	}

	/**
	 * Adds a column to the SET list with an expression, validated against the
	 * table: columns of the table, literals, functions, arithmetic and CASE,
	 * without aggregates nor placeholders, as <code>UPPER(P.DPTYPE)</code>.
	 *
	 * @param column     The column, of the table.
	 * @param expression The expression.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If the column does not
	 *                                              belong to the table or is
	 *                                              already in the SET list.
	 * @throws IllegalStateException                If the table is not set, or
	 *                                              the update is used with
	 *                                              records.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void set(Column column, String expression) {
		checkSetAllowed();
		Objects.requireNonNull(column, "Column can not be null");
		Objects.requireNonNull(expression, "Expression can not be null");
		Map<String, Table> tables = scope.resolve();
		Analysis analysis = Analysis.analyze(expression, Clause.SET, tables, List.of(), List.of());
		putAssignment(new Assignment(column, analysis, null));
	}

	/**
	 * Adds a column to the SET list with an expression, the column given by
	 * name, ignoring case.
	 *
	 * @param columnName The name of the column.
	 * @param expression The expression.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If the name is not a column
	 *                                              of the table, or the column
	 *                                              is already in the SET list.
	 * @throws IllegalStateException                If the table is not set, or
	 *                                              the update is used with
	 *                                              records.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void set(String columnName, String expression) {
		Column column = findColumn(columnName);
		set(column, expression);
	}

	/**
	 * Adds a column to the SET list with a value, sent as a parameter. The
	 * value is not validated: its type and null are checked by the database.
	 *
	 * @param column The column, of the table.
	 * @param value  The value.
	 * @throws NullPointerException     If an argument is null.
	 * @throws IllegalArgumentException If the column does not belong to the
	 *                                  table or is already in the SET list.
	 * @throws IllegalStateException    If the table is not set, or the update
	 *                                  is used with records.
	 */
	public void set(Column column, Value value) {
		checkSetAllowed();
		Objects.requireNonNull(column, "Column can not be null");
		Objects.requireNonNull(value, "Value can not be null, use a null value of the type");
		putAssignment(new Assignment(column, null, value));
	}

	/**
	 * Adds a column to the SET list with a value, the column given by name,
	 * ignoring case.
	 *
	 * @param columnName The name of the column.
	 * @param value      The value.
	 * @throws NullPointerException     If an argument is null.
	 * @throws IllegalArgumentException If the name is not a column of the
	 *                                  table, or the column is already in the
	 *                                  SET list.
	 * @throws IllegalStateException    If the table is not set, or the update
	 *                                  is used with records.
	 */
	public void set(String columnName, Value value) {
		Column column = findColumn(columnName);
		set(column, value);
	}

	/**
	 * Returns the WHERE clause, to add its conditions. They can use the table
	 * updated, so they are added after {@link #table(Table, String)}.
	 *
	 * @return The WHERE clause.
	 * @throws IllegalStateException If the update is used with records.
	 */
	public QWhere getWhere() {
		checkByHand();
		return where;
	}

	/**
	 * Updates the row of a record by its primary key, on its own pooled
	 * connection, with a prepared statement: all the columns of the record
	 * that are not of the key are set to its values.
	 * <p>
	 * The update takes its table and the positions of the columns from the
	 * first record: all its columns must belong to the same table, which must
	 * have a primary key, and the key columns must be present, with at least
	 * one other column. The following records must share the column list of
	 * the first one, as the records of a read do.
	 *
	 * @param record The record.
	 * @return The number of rows updated, 0 or 1.
	 * @throws NullPointerException     If the record is null.
	 * @throws IllegalArgumentException If the first record has no columns, a
	 *                                  column of no table or of another table, a
	 *                                  repeated column, a table without primary
	 *                                  key, misses a key column or has only key
	 *                                  columns, or a later record has another
	 *                                  column list.
	 * @throws IllegalStateException    If the table, the SET list or conditions
	 *                                  were set by hand.
	 * @throws SQLException             If a database access error occurs.
	 */
	public int execute(Record record) throws SQLException {
		Objects.requireNonNull(record, "Record can not be null");
		if (from != null || !assignments.isEmpty() || !where.isEmpty()) {
			throw new IllegalStateException("The table, the SET list or conditions were set by hand, execute(Record) takes them from the records");
		}
		ColumnList list = record.columns();
		if (recordColumns == null) {
			setFromRecord(list);
			recordColumns = list;
		} else if (list != recordColumns) {
			throw new IllegalArgumentException("The record has other columns than the previous ones, use a new QUpdate");
		}
		recordValues.clear();
		for (int position : setPositions) {
			Value value = record.getValue(position);
			recordValues.add(value);
		}
		for (int position : keyPositions) {
			Value value = record.getValue(position);
			recordValues.add(value);
		}
		int rows = executePrepared(recordSQL, recordValues);
		return rows;
	}

	/**
	 * Executes the statement built by hand on its own pooled connection, with a
	 * prepared statement when the SET list has values. Without conditions, all
	 * the rows of the table are updated.
	 *
	 * @return The number of rows updated.
	 * @throws IllegalStateException If the table or the SET list is not set, or
	 *                               the update is used with records.
	 * @throws SQLException          If a database access error occurs.
	 */
	public int execute() throws SQLException {
		checkByHand();
		if (from == null) {
			throw new IllegalStateException("The table is not set");
		}
		if (assignments.isEmpty()) {
			throw new IllegalStateException("The SET list is empty");
		}
		String sql = toSQL();
		List<Value> values = getSetValues();
		if (values.isEmpty()) {
			int rows = executeUpdate(sql);
			return rows;
		}
		int rows = executePrepared(sql, values);
		return rows;
	}

	/**
	 * Executes a statement with placeholders on its own pooled connection.
	 *
	 * @param sql    The statement.
	 * @param values The values of the placeholders, in order.
	 * @return The number of rows updated.
	 * @throws SQLException If a database access error occurs.
	 */
	private int executePrepared(String sql, List<Value> values) throws SQLException {
		DBEngine db = getDBEngine();
		try (DBConnection cn = db.getConnection();
				DBPreparedStatement ps = cn.prepareStatement(sql)) {
			for (int i = 0; i < values.size(); i++) {
				db.setValue(ps, i + 1, values.get(i));
			}
			int rows = ps.executeUpdate();
			return rows;
		}
	}

	/**
	 * Checks that the update is not used with records, before building it by
	 * hand.
	 *
	 * @throws IllegalStateException If the update is used with records.
	 */
	private void checkByHand() {
		if (recordColumns != null) {
			throw new IllegalStateException("The update is used with records, its table and columns come from them");
		}
	}

	/**
	 * Checks that the SET list can be filled: the table is set and the update
	 * is not used with records.
	 *
	 * @throws IllegalStateException If the SET list can not be filled.
	 */
	private void checkSetAllowed() {
		checkByHand();
		if (table == null) {
			throw new IllegalStateException("The table must be set before the SET list");
		}
	}

	/**
	 * Returns a column of the table by name, ignoring case.
	 *
	 * @param columnName The name.
	 * @return The column.
	 * @throws NullPointerException     If the name is null.
	 * @throws IllegalArgumentException If the name is not a column of the
	 *                                  table.
	 * @throws IllegalStateException    If the table is not set, or the update
	 *                                  is used with records.
	 */
	private Column findColumn(String columnName) {
		checkSetAllowed();
		Objects.requireNonNull(columnName, "Column name can not be null");
		Column column = table.columns().getIgnoreCase(columnName);
		if (column == null) {
			StringBuilder msg = new StringBuilder();
			msg.append("Column ");
			msg.append(columnName);
			msg.append(" not found in table ");
			msg.append(table.getName());
			throw new IllegalArgumentException(msg.toString());
		}
		return column;
	}

	/**
	 * Adds an assignment to the SET list, rejecting a column of another table
	 * or already in the list.
	 *
	 * @param assignment The assignment.
	 * @throws IllegalArgumentException If the column does not belong to the
	 *                                  table or is already in the list.
	 */
	private void putAssignment(Assignment assignment) {
		Column column = assignment.column();
		if (!table.equals(column.getTable())) {
			StringBuilder msg = new StringBuilder();
			msg.append("Column ");
			msg.append(column.getName());
			msg.append(" does not belong to table ");
			msg.append(table.getName());
			throw new IllegalArgumentException(msg.toString());
		}
		String key = column.getName().toUpperCase(Locale.ROOT);
		if (assignments.containsKey(key)) {
			StringBuilder msg = new StringBuilder();
			msg.append("Column ");
			msg.append(column.getName());
			msg.append(" is already in the SET list");
			throw new IllegalArgumentException(msg.toString());
		}
		assignments.put(key, assignment);
	}

	/**
	 * Returns the values of the SET list, in order, sent as parameters.
	 *
	 * @return The values, empty if all the assignments are expressions.
	 */
	private List<Value> getSetValues() {
		List<Value> values = new ArrayList<>();
		for (Assignment assignment : assignments.values()) {
			if (assignment.value() != null) {
				values.add(assignment.value());
			}
		}
		return values;
	}

	/**
	 * Sets the positions of the SET and key columns and the statement from the
	 * column list of the first record.
	 *
	 * @param list The column list of the record.
	 * @throws IllegalArgumentException If the record has no columns, a column
	 *                                  belongs to no table or to another one or
	 *                                  is repeated, the table has no primary
	 *                                  key, a key column is missing, or all the
	 *                                  columns are of the key.
	 */
	private void setFromRecord(ColumnList list) {
		if (list.isEmpty()) {
			throw new IllegalArgumentException("The record has no columns");
		}
		Table recordTable = list.get(0).getTable();
		Set<String> names = new HashSet<>();
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
			String name = column.getName().toUpperCase(Locale.ROOT);
			if (!names.add(name)) {
				StringBuilder msg = new StringBuilder();
				msg.append("Column ");
				msg.append(column.getName());
				msg.append(" is repeated");
				throw new IllegalArgumentException(msg.toString());
			}
		}
		Index primaryKey = recordTable.getPrimaryKey();
		if (primaryKey == null) {
			StringBuilder msg = new StringBuilder();
			msg.append("Table ");
			msg.append(recordTable.getName());
			msg.append(" has no primary key, its records can not be updated by key");
			throw new IllegalArgumentException(msg.toString());
		}
		List<Integer> keys = new ArrayList<>();
		for (Column keyColumn : primaryKey.getColumns()) {
			int position = -1;
			for (int i = 0; i < list.size() && position < 0; i++) {
				if (list.get(i).equals(keyColumn)) {
					position = i;
				}
			}
			if (position < 0) {
				StringBuilder msg = new StringBuilder();
				msg.append("The record lacks column ");
				msg.append(keyColumn.getName());
				msg.append(" of the primary key of table ");
				msg.append(recordTable.getName());
				throw new IllegalArgumentException(msg.toString());
			}
			keys.add(position);
		}
		List<Integer> sets = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			if (!keys.contains(i)) {
				sets.add(i);
			}
		}
		if (sets.isEmpty()) {
			StringBuilder msg = new StringBuilder();
			msg.append("The record has only columns of the primary key of table ");
			msg.append(recordTable.getName());
			msg.append(", there is nothing to update");
			throw new IllegalArgumentException(msg.toString());
		}
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		sql.append("UPDATE ");
		sql.append(getQualifiedName(recordTable));
		sql.append("\nSET ");
		for (int i = 0; i < sets.size(); i++) {
			if (i > 0) {
				sql.append(", ");
			}
			Column column = list.get(sets.get(i));
			sql.append(db.quote(column.getName()));
			sql.append(" = ?");
		}
		sql.append("\nWHERE ");
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0) {
				sql.append(" AND ");
			}
			Column column = list.get(keys.get(i));
			sql.append(db.quote(column.getName()));
			sql.append(" = ?");
		}
		keyPositions.addAll(keys);
		setPositions.addAll(sets);
		recordSQL = sql.toString();
	}

	/**
	 * Returns the statement built by hand as far as it is built: on SQL Server
	 * <code>UPDATE alias SET col = expr, col = ? FROM table alias</code>, on
	 * Oracle <code>UPDATE table alias SET col = expr, col = ?</code>, then the
	 * WHERE clause. The columns of the SET list are not qualified.
	 *
	 * @return The SQL, empty while the table is not set.
	 */
	private String toSQL() {
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		if (from == null) {
			return sql.toString();
		}
		StringBuilder set = new StringBuilder();
		if (!assignments.isEmpty()) {
			set.append("\nSET ");
			boolean first = true;
			for (Assignment assignment : assignments.values()) {
				if (!first) {
					set.append(", ");
				}
				set.append(db.quote(assignment.column().getName()));
				set.append(" = ");
				if (assignment.expression() != null) {
					set.append(assignment.expression().toSQL(db));
				} else {
					set.append("?");
				}
				first = false;
			}
		}
		String update = switch (db.getAdapter()) {
			case SQLSERVER: {
				StringBuilder b = new StringBuilder();
				b.append("UPDATE ");
				b.append(db.quote(alias));
				b.append(set);
				b.append("\n");
				b.append(from.toSQL());
				yield b.toString();
			}
			case ORACLE: {
				StringBuilder b = new StringBuilder();
				b.append("UPDATE ");
				b.append(getQualifiedName(table));
				b.append(" ");
				b.append(db.quote(alias));
				b.append(set);
				yield b.toString();
			}
		};
		sql.append(update);
		String whereSQL = where.toSQL();
		if (!whereSQL.isEmpty()) {
			sql.append("\n");
			sql.append(whereSQL);
		}
		return sql.toString();
	}

	/**
	 * Returns the statement as far as it is built, the one built by hand or the
	 * one of the records, followed by one comment line per value sent, as
	 * <code>-- 1: A325</code>.
	 *
	 * @return The SQL.
	 */
	@Override
	public String toString() {
		String sql;
		List<Value> values;
		if (recordSQL != null) {
			sql = recordSQL;
			values = recordValues;
		} else {
			sql = toSQL();
			values = getSetValues();
		}
		StringBuilder b = new StringBuilder();
		b.append(sql);
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
