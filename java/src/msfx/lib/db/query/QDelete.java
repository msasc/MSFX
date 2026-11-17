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
import java.util.List;
import java.util.Objects;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBEngine;
import msfx.lib.db.DBPreparedStatement;
import msfx.lib.md.Column;
import msfx.lib.md.ColumnList;
import msfx.lib.md.Index;
import msfx.lib.md.Record;
import msfx.lib.md.Table;
import msfx.lib.md.Value;

/**
 * DELETE statement on a single table, built as it is written:
 *
 * <pre>
 * QDelete delete = new QDelete(db);
 * delete.from(table, "P");
 * QWhere where = delete.getWhere();
 * where.cond("P.CPTYPE = ?", value);
 * delete.execute();
 * </pre>
 *
 * Aimed at deleting blocks of rows with a WHERE clause, on any table; without
 * conditions, all the rows of the table are deleted. The alias is required, so
 * that the conditions and their EXISTS selects name the table deleted from.
 * <p>
 * A single row is deleted through its record, by the primary key of its table,
 * with <code>new QDelete(db).execute(record)</code>. A delete is built by hand
 * or used with records, never both.
 *
 * @author Miquel Sas
 */
public class QDelete extends QBase {

	/**
	 * The table deleted from, with its alias, or null while it is not set or
	 * the delete is used with records.
	 */
	private QFrom from = null;
	/**
	 * The alias of the table deleted from, or null while it is not set.
	 */
	private String alias = null;
	/**
	 * Tables used, by alias: the table deleted from, and those of the outer
	 * statements seen by the EXISTS selects of the WHERE clause.
	 */
	private final QScope scope;
	/**
	 * WHERE conditions, set by hand.
	 */
	private final QWhere where;
	/**
	 * The column list of the records deleted with {@link #execute(Record)}, or
	 * null while the delete is not used with records. Later records must share
	 * it.
	 */
	private ColumnList recordColumns = null;
	/**
	 * The positions in the records of the columns of the primary key, in the
	 * order of the key, set with the first record.
	 */
	private final List<Integer> keyPositions = new ArrayList<>();
	/**
	 * The statement of the records, DELETE FROM table WHERE key1 = ? AND ...,
	 * built with the first record.
	 */
	private String recordSQL = null;
	/**
	 * The values of the key of the last record, for toString.
	 */
	private final List<Value> keyValues = new ArrayList<>();

	/**
	 * Constructor.
	 *
	 * @param db The database engine.
	 * @throws NullPointerException If the engine is null.
	 */
	public QDelete(DBEngine db) {
		super(db);
		scope = new QScope(null);
		where = new QWhere(db, scope);
	}

	/**
	 * Sets the table to delete from, and registers it under its alias for the
	 * conditions.
	 *
	 * @param table The table.
	 * @param alias The alias used in the conditions.
	 * @throws NullPointerException     If the table or the alias is null.
	 * @throws IllegalArgumentException If the alias is blank.
	 * @throws IllegalStateException    If the table is already set, or the
	 *                                  delete is used with records.
	 */
	public void from(Table table, String alias) {
		checkByHand();
		if (from != null) {
			throw new IllegalStateException("The table is already set");
		}
		scope.checkTable(table, alias);
		from = new QFrom(getDBEngine(), table, alias);
		this.alias = alias;
		scope.put(alias, table);
	}

	/**
	 * Returns the WHERE clause, to add its conditions. They can use the table
	 * deleted from, so they are added after {@link #from(Table, String)}.
	 *
	 * @return The WHERE clause.
	 * @throws IllegalStateException If the delete is used with records.
	 */
	public QWhere getWhere() {
		checkByHand();
		return where;
	}

	/**
	 * Deletes the row of a record by its primary key, on its own pooled
	 * connection, with a prepared statement.
	 * <p>
	 * The delete takes its table and the positions of the key from the first
	 * record: all its columns must belong to the same table, which must have a
	 * primary key, and the key columns must be present. The following records
	 * must share the column list of the first one, as the records of a read do,
	 * and are deleted without checking their columns again.
	 *
	 * @param record The record.
	 * @return The number of rows deleted, 0 or 1.
	 * @throws NullPointerException     If the record is null.
	 * @throws IllegalArgumentException If the first record has no columns, a
	 *                                  column of no table or of another table, a
	 *                                  table without primary key, or misses a key
	 *                                  column, or a later record has another
	 *                                  column list.
	 * @throws IllegalStateException    If the table or conditions were set by
	 *                                  hand.
	 * @throws SQLException             If a database access error occurs.
	 */
	public int execute(Record record) throws SQLException {
		Objects.requireNonNull(record, "Record can not be null");
		if (from != null || !where.isEmpty()) {
			throw new IllegalStateException("The table or conditions were set by hand, execute(Record) takes them from the records");
		}
		ColumnList list = record.columns();
		if (recordColumns == null) {
			setFromRecord(list);
			recordColumns = list;
		} else if (list != recordColumns) {
			throw new IllegalArgumentException("The record has other columns than the previous ones, use a new QDelete");
		}
		keyValues.clear();
		for (int position : keyPositions) {
			Value value = record.getValue(position);
			keyValues.add(value);
		}
		DBEngine db = getDBEngine();
		try (DBConnection cn = db.getConnection();
				DBPreparedStatement ps = cn.prepareStatement(recordSQL)) {
			for (int i = 0; i < keyValues.size(); i++) {
				db.setValue(ps, i + 1, keyValues.get(i));
			}
			int rows = ps.executeUpdate();
			return rows;
		}
	}

	/**
	 * Executes the statement built by hand on its own pooled connection. Without
	 * conditions, all the rows of the table are deleted.
	 *
	 * @return The number of rows deleted.
	 * @throws IllegalStateException If the table is not set, or the delete is
	 *                               used with records.
	 * @throws SQLException          If a database access error occurs.
	 */
	public int execute() throws SQLException {
		checkByHand();
		if (from == null) {
			throw new IllegalStateException("The table is not set");
		}
		String sql = toSQL();
		int rows = executeUpdate(sql);
		return rows;
	}

	/**
	 * Checks that the delete is not used with records, before building it by
	 * hand.
	 *
	 * @throws IllegalStateException If the delete is used with records.
	 */
	private void checkByHand() {
		if (recordColumns != null) {
			throw new IllegalStateException("The delete is used with records, its table and key come from them");
		}
	}

	/**
	 * Sets the positions of the key and the statement from the column list of
	 * the first record.
	 *
	 * @param list The column list of the record.
	 * @throws IllegalArgumentException If the record has no columns, a column
	 *                                  belongs to no table or to another one,
	 *                                  the table has no primary key, or a key
	 *                                  column is missing.
	 */
	private void setFromRecord(ColumnList list) {
		if (list.isEmpty()) {
			throw new IllegalArgumentException("The record has no columns");
		}
		Table recordTable = list.get(0).getTable();
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
		}
		Index primaryKey = recordTable.getPrimaryKey();
		if (primaryKey == null) {
			StringBuilder msg = new StringBuilder();
			msg.append("Table ");
			msg.append(recordTable.getName());
			msg.append(" has no primary key, its records can not be deleted by key");
			throw new IllegalArgumentException(msg.toString());
		}
		DBEngine db = getDBEngine();
		List<Integer> positions = new ArrayList<>();
		StringBuilder sql = new StringBuilder();
		sql.append("DELETE FROM ");
		sql.append(getQualifiedName(recordTable));
		sql.append("\nWHERE ");
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
			if (!positions.isEmpty()) {
				sql.append(" AND ");
			}
			sql.append(db.quote(keyColumn.getName()));
			sql.append(" = ?");
			positions.add(position);
		}
		keyPositions.addAll(positions);
		recordSQL = sql.toString();
	}

	/**
	 * Returns the statement built by hand as far as it is built: on SQL Server
	 * <code>DELETE alias FROM table alias</code>, on Oracle
	 * <code>DELETE FROM table alias</code>, then the WHERE clause.
	 *
	 * @return The SQL, empty while the table is not set.
	 */
	private String toSQL() {
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		if (from == null) {
			return sql.toString();
		}
		String delete = switch (db.getAdapter()) {
			case SQLSERVER: {
				StringBuilder b = new StringBuilder();
				b.append("DELETE ");
				b.append(db.quote(alias));
				yield b.toString();
			}
			case ORACLE:
				yield "DELETE";
		};
		sql.append(delete);
		sql.append("\n");
		sql.append(from.toSQL());
		String whereSQL = where.toSQL();
		if (!whereSQL.isEmpty()) {
			sql.append("\n");
			sql.append(whereSQL);
		}
		return sql.toString();
	}

	/**
	 * Returns the statement as far as it is built: the one built by hand, or
	 * the one of the records followed by one comment line per value of the key
	 * of the last record, as <code>-- 1: A325</code>.
	 *
	 * @return The SQL.
	 */
	@Override
	public String toString() {
		if (recordSQL == null) {
			String sql = toSQL();
			return sql;
		}
		StringBuilder b = new StringBuilder();
		b.append(recordSQL);
		for (int i = 0; i < keyValues.size(); i++) {
			b.append("\n-- ");
			b.append(i + 1);
			b.append(": ");
			b.append(keyValues.get(i));
		}
		return b.toString();
	}
}
