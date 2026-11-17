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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBEngine;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.query.sa.Analysis;
import msfx.lib.db.query.sa.Clause;
import msfx.lib.md.Column;
import msfx.lib.md.ColumnList;
import msfx.lib.md.DataType;
import msfx.lib.md.Domain;
import msfx.lib.md.Record;
import msfx.lib.md.Table;
import msfx.lib.md.Value;

/**
 * SELECT statement.
 * <p>
 * Every table gets an alias, required and unique ignoring case, that is the
 * name the expressions of the statement use to refer to its columns.
 * <p>
 * A select created for an EXISTS condition, with
 * {@link QWhere#createExists()}, also sees the tables of the outer
 * statements. Aliases are unique in the whole tree of statements, so that an
 * inner alias never hides an outer one.
 * <p>
 * Instead of a master table, the FROM clause can wrap the UNION or UNION ALL
 * of other selects, with an alias, as a table whose columns are their column
 * aliases.
 *
 * @author Miquel Sas
 */
public class QSelect extends QBase {

	/**
	 * Callback that receives each row read by
	 * {@link QSelect#readCallback(Callback)}, positioned on the row, and says
	 * whether to go on reading.
	 */
	@FunctionalInterface
	public interface Callback {
		/**
		 * Processes the current row.
		 *
		 * @param rs The result set, positioned on the row. It must not be moved
		 *           nor closed.
		 * @return True to go on reading, false to stop.
		 * @throws Exception If an error occurs. Exceptions other than
		 *                   SQLException and runtime exceptions reach the caller
		 *                   of readCallback as the cause of an SQLException.
		 */
		boolean call(DBResultSet rs) throws Exception;
	}

	/**
	 * Tables used, by alias, filled by the FROM clause and the joins, and the
	 * tables of the outer statements for an EXISTS select.
	 */
	private final QScope scope;
	/**
	 * True once this EXISTS select is attached to its outer statement.
	 */
	private boolean attached = false;
	/**
	 * FROM clause, with the master table and its alias, or the wrapped selects
	 * and their alias, or null while it is not set.
	 */
	private QFrom from;
	/**
	 * Joins, in the order they were added.
	 */
	private final List<QJoin> joins = new ArrayList<>();

	/**
	 * Columns of the select list, in the order they were added, by their alias
	 * in upper case, so that an alias is found in constant time ignoring case.
	 * Code generated selects can have hundreds of columns.
	 */
	private final Map<String, QExpr> columns = new LinkedHashMap<>();
	/**
	 * Columns of the records read, one per column of the select list, under its
	 * alias, in the order they were added: a column created from the domain, or
	 * a copy of the column given, that keeps its table. Empty when the columns
	 * have no domain nor column: all the columns have one or none has.
	 */
	private final ColumnList recordColumns = new ColumnList();

	/**
	 * WHERE clause, written only when it has conditions.
	 */
	private final QWhere where;
	/**
	 * GROUP BY clause, or null while the select is not grouped.
	 */
	private QGroupBy groupBy;
	/**
	 * HAVING condition, validated against the select list, or null.
	 */
	private Analysis having;
	/**
	 * ORDER BY clause, or null while the select is not ordered.
	 */
	private QOrderBy orderBy;

	/**
	 * Constructor.
	 *
	 * @param db The database engine.
	 * @throws NullPointerException If the engine is null.
	 */
	public QSelect(DBEngine db) {
		super(db);
		scope = new QScope(null);
		where = new QWhere(db, scope);
	}

	/**
	 * Constructor of a select for an EXISTS condition, created by the WHERE
	 * clause of the outer statement with {@link QWhere#createExists()}. Its
	 * expressions can use the tables of the outer statements.
	 *
	 * @param db     The database engine.
	 * @param parent The scope of the outer statement.
	 */
	QSelect(DBEngine db, QScope parent) {
		super(db);
		scope = new QScope(parent);
		where = new QWhere(db, scope);
	}

	/**
	 * Sets the FROM clause with the master table, and registers the table.
	 *
	 * @param table The master table.
	 * @param alias The alias.
	 * @throws NullPointerException     If the table or the alias is null.
	 * @throws IllegalArgumentException If the alias is blank.
	 * @throws IllegalStateException    If the FROM clause is already set.
	 */
	public void from(Table table, String alias) {
		if (from != null) {
			throw new IllegalStateException("The FROM clause is already set");
		}
		scope.checkTable(table, alias);
		from = new QFrom(getDBEngine(), table, alias);
		scope.put(alias, table);
	}

	/**
	 * Sets the FROM clause with the UNION of selects, wrapped within parentheses
	 * with an alias, as an alternative to {@link #from(Table, String)}. The
	 * wrapped source is registered as a table named after the alias, with the
	 * columns of the first select named after their aliases, so that joins,
	 * columns, conditions and the other clauses use it as any table.
	 *
	 * @param alias   The alias of the wrapped selects.
	 * @param selects The wrapped selects: complete, without ORDER BY, and with
	 *                the same column aliases in the same order.
	 * @throws NullPointerException     If the alias, the selects or one of them
	 *                                  is null.
	 * @throws IllegalArgumentException If the alias is blank or already used,
	 *                                  there are no selects, or a select has
	 *                                  another engine, an ORDER BY clause, or
	 *                                  other column aliases than the first one.
	 * @throws IllegalStateException    If the FROM clause is already set, or a
	 *                                  select can not be executed.
	 */
	public void wrapUnion(String alias, QSelect... selects) {
		wrap(alias, selects, false);
	}

	/**
	 * Sets the FROM clause with the UNION ALL of selects, wrapped within
	 * parentheses with an alias, as an alternative to
	 * {@link #from(Table, String)}. The wrapped source is registered as a table
	 * named after the alias, with the columns of the first select named after
	 * their aliases, so that joins, columns, conditions and the other clauses
	 * use it as any table.
	 *
	 * @param alias   The alias of the wrapped selects.
	 * @param selects The wrapped selects: complete, without ORDER BY, and with
	 *                the same column aliases in the same order.
	 * @throws NullPointerException     If the alias, the selects or one of them
	 *                                  is null.
	 * @throws IllegalArgumentException If the alias is blank or already used,
	 *                                  there are no selects, or a select has
	 *                                  another engine, an ORDER BY clause, or
	 *                                  other column aliases than the first one.
	 * @throws IllegalStateException    If the FROM clause is already set, or a
	 *                                  select can not be executed.
	 */
	public void wrapUnionAll(String alias, QSelect... selects) {
		wrap(alias, selects, true);
	}

	/**
	 * Adds a LEFT JOIN: all the rows of the previous tables, with or without a
	 * matching row in the joined table.
	 *
	 * @param table    The joined table.
	 * @param alias    The alias of the joined table.
	 * @param linkExpr The ON condition, that can use the columns of the FROM
	 *                 table, of the previous joins and of the joined table.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If the alias is blank or
	 *                                              already used.
	 * @throws IllegalStateException                If the FROM clause is not set.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the condition is wrong.
	 */
	public void leftJoin(Table table, String alias, String linkExpr) {
		addJoin(QJoin.Type.LEFT, table, alias, linkExpr);
	}

	/**
	 * Adds an INNER JOIN: only the rows with a matching row in the joined table.
	 *
	 * @param table    The joined table.
	 * @param alias    The alias of the joined table.
	 * @param linkExpr The ON condition, that can use the columns of the FROM
	 *                 table, of the previous joins and of the joined table.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If the alias is blank or
	 *                                              already used.
	 * @throws IllegalStateException                If the FROM clause is not set.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the condition is wrong.
	 */
	public void innerJoin(Table table, String alias, String linkExpr) {
		addJoin(QJoin.Type.INNER, table, alias, linkExpr);
	}

	/**
	 * Adds a column to the select list. The expression can use the columns of
	 * the tables already registered, so columns are added after the FROM clause
	 * and the joins they use.
	 *
	 * @param expr  The expression.
	 * @param alias The alias of the column in the result, unique ignoring case.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If the alias is blank or
	 *                                              already used, or the previous
	 *                                              columns have a domain.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the select has already the
	 *                                              maximum number of columns of
	 *                                              the database.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void addColumn(String expr, String alias) {
		putColumn(expr, alias, null);
	}

	/**
	 * Adds a column to the select list, with the domain that defines it in the
	 * records read: the records get a column named after the alias, that belongs
	 * to no table. The expression can use the columns of the tables already
	 * registered, so columns are added after the FROM clause and the joins they
	 * use.
	 *
	 * @param expr   The expression.
	 * @param alias  The alias of the column in the result, unique ignoring case.
	 * @param domain The domain, or null. All the columns have a domain or a
	 *               column, or none has.
	 * @throws NullPointerException                 If the expression or the alias
	 *                                              is null.
	 * @throws IllegalArgumentException             If the alias is blank or
	 *                                              already used, or the column
	 *                                              has a domain and the previous
	 *                                              ones have none, or the other
	 *                                              way round.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the select has already the
	 *                                              maximum number of columns of
	 *                                              the database.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void addColumn(String expr, String alias, Domain domain) {
		Objects.requireNonNull(alias, "Alias can not be null");
		Column recordColumn = domain == null ? null : new Column(alias, domain);
		putColumn(expr, alias, recordColumn);
	}

	/**
	 * Adds a column to the select list, with the column that describes it in
	 * the records read. A column of a table, given for an expression that is
	 * that very column, makes the records read refer to the table, so that they
	 * can be used to update it; a column of no table, as
	 * <code>new Column(alias, domain)</code>, only defines the value. The
	 * records get a copy of the column, that keeps its table, so that changes to
	 * its header, label or other properties do not reach the table. The
	 * expression can use the columns of the tables already registered, so
	 * columns are added after the FROM clause and the joins they use.
	 *
	 * @param expr   The expression.
	 * @param alias  The alias of the column in the result, unique ignoring case.
	 * @param column The column, or null. All the columns have a domain or a
	 *               column, or none has.
	 * @throws NullPointerException                 If the expression or the alias
	 *                                              is null.
	 * @throws IllegalArgumentException             If the alias is blank or
	 *                                              already used, the column has a
	 *                                              column and the previous ones
	 *                                              have none, or the other way
	 *                                              round, or the column belongs to
	 *                                              a table and the expression is
	 *                                              not that column.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the select has already the
	 *                                              maximum number of columns of
	 *                                              the database.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void addColumn(String expr, String alias, Column column) {
		Column recordColumn = column == null ? null : new Column(column);
		putColumn(expr, alias, recordColumn);
	}

	/**
	 * Adds a column to the select list, with the column of the records read,
	 * already created for the select.
	 *
	 * @param expr         The expression.
	 * @param alias        The alias.
	 * @param recordColumn The column of the records read, or null.
	 */
	private void putColumn(String expr, String alias, Column recordColumn) {
		if (from == null) {
			throw new IllegalStateException("The FROM clause must be set before the columns");
		}
		Objects.requireNonNull(alias, "Alias can not be null");
		String key = alias.toUpperCase(Locale.ROOT);
		QExpr existing = columns.get(key);
		if (existing != null) {
			StringBuilder msg = new StringBuilder();
			msg.append("Column alias ");
			msg.append(alias);
			msg.append(" is already used by column ");
			msg.append(existing);
			throw new IllegalArgumentException(msg.toString());
		}
		DBEngine db = getDBEngine();
		if (columns.size() == db.getMaxSelectColumns()) {
			StringBuilder msg = new StringBuilder();
			msg.append("A select accepts ");
			msg.append(db.getMaxSelectColumns());
			msg.append(" columns at most in ");
			msg.append(db.getAdapter());
			throw new IllegalStateException(msg.toString());
		}
		/* Validate mixing calls with and without domain or column */
		boolean canAdd = (recordColumn != null && columns.size() == recordColumns.size());
		canAdd |= (recordColumn == null && recordColumns.isEmpty());
		if (!canAdd) {
			StringBuilder msg = new StringBuilder();
			msg.append("You are mixing calls with and without a domain or column");
			throw new IllegalArgumentException(msg.toString());
		}
		/* Do add it. */
		Map<String, Table> tables = scope.resolve();
		QExpr column = new QExpr(expr, alias, recordColumn, tables);
		columns.put(key, column);
		if (recordColumn != null) {
			recordColumns.add(alias, recordColumn);
		}
	}

	/**
	 * Returns the WHERE clause, to add its conditions. They can use the tables
	 * already registered, so they are added after the FROM clause and the
	 * joins they use.
	 *
	 * @return The WHERE clause.
	 */
	public QWhere getWhere() {
		return where;
	}

	/**
	 * Groups the select. The GROUP BY clause is generated from the select list
	 * when the select is written: the expressions that are not aggregates, so
	 * all of them if there are no aggregates. Expressions that use no column of
	 * this select, like constants, are left out.
	 *
	 * @throws IllegalStateException If the select is already grouped.
	 */
	public void groupBy() {
		if (groupBy != null) {
			throw new IllegalStateException("The select is already grouped");
		}
		Map<String, Table> tables = scope.getTables();
		groupBy = new QGroupBy(getDBEngine(), columns.values(), tables);
	}

	/**
	 * Sets the HAVING condition, on the aggregates of the select list, with
	 * optional placeholders <code>?</code> replaced by the values when written.
	 * Each aggregate must be one of the select list, arithmetic around them is
	 * accepted, functions around them and plain columns are not: conditions on
	 * plain columns go in the WHERE clause.
	 *
	 * @param expr   The condition.
	 * @param values The values of the placeholders, in order.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If a value is null.
	 * @throws IllegalStateException                If the select already has a
	 *                                              HAVING condition.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the condition is wrong.
	 */
	public void having(String expr, Value... values) {
		if (having != null) {
			throw new IllegalStateException("The select already has a HAVING condition");
		}
		Objects.requireNonNull(expr, "Expression can not be null");
		Objects.requireNonNull(values, "Values can not be null");
		for (Value value : values) {
			if (value == null || value.isNull()) {
				throw new IllegalArgumentException("A null value has no literal, use IS NULL");
			}
		}
		List<Analysis> selectList = new ArrayList<>();
		for (QExpr column : columns.values()) {
			selectList.add(column.getAnalysis());
		}
		Map<String, Table> tables = scope.resolve();
		having = Analysis.analyze(expr, Clause.HAVING, tables, Arrays.asList(values), selectList);
	}

	/**
	 * Sets the ORDER BY clause. Each segment is the alias of a column of the
	 * select list, ignoring case, optionally followed by ASC or DESC, as in
	 * <code>orderBy("NYEAR", "NMONTH DESC")</code>.
	 *
	 * @param segments The segments.
	 * @throws NullPointerException     If the segments or one of them is null.
	 * @throws IllegalArgumentException If there are no segments, or a segment
	 *                                  is blank, names an unknown alias, has a
	 *                                  word other than ASC or DESC after the
	 *                                  alias, or repeats a column.
	 * @throws IllegalStateException    If the select is already ordered.
	 */
	public void orderBy(String... segments) {
		if (orderBy != null) {
			throw new IllegalStateException("The select is already ordered");
		}
		orderBy = new QOrderBy(getDBEngine(), columns, segments);
	}

	/**
	 * Returns the columns of the records read: one column per column of the
	 * select, in the order of the select, under its alias. A column created from
	 * a domain is named after the alias and belongs to no table; a column given
	 * as a column is a copy of it, with its name, and keeps its table, so that
	 * the records refer to the table where they hold its values.
	 * <p>
	 * The list returned is the one of the select, shared by the records it
	 * reads. Changes made to it or its columns show in the records read
	 * afterwards; they are not meant to be changed.
	 *
	 * @return The column list.
	 * @throws IllegalStateException If the select has no columns, or its
	 *                               columns have no domain.
	 */
	public ColumnList getColumnList() {
		if (columns.isEmpty()) {
			throw new IllegalStateException("The select has no columns");
		}
		if (recordColumns.isEmpty()) {
			throw new IllegalStateException("The columns of the select have no domain");
		}
		return recordColumns;
	}

	/**
	 * Returns the values of the current row, one per column of
	 * {@link #getColumnList()} and in its order, to create a record. Each value
	 * is read with the getter of the type of its column, the driver converting
	 * the type of the database, and is not modified:
	 * <ul>
	 * <li>DECIMAL values get the decimals of the column, rounded half up.</li>
	 * <li>Null numbers and booleans are zero and false, null strings are
	 * empty.</li>
	 * <li>Booleans are read from 1/0, Y/N or T/F, and objects parsed from their
	 * JSON text.</li>
	 * </ul>
	 * Meant to be called for every row read, it checks only that the columns
	 * have a domain. Each column is read once, in order.
	 *
	 * @param rs The result set of this select, positioned on the row.
	 * @return The values, a new array on each call.
	 * @throws IllegalStateException If the columns of the select have no
	 *                               domain.
	 * @throws SQLException          If a database access error occurs, or an
	 *                               object is not valid JSON.
	 */
	public Value[] getValues(DBResultSet rs) throws SQLException {
		if (recordColumns.isEmpty()) {
			throw new IllegalStateException("The columns of the select have no domain");
		}
		DBEngine db = getDBEngine();
		int size = recordColumns.size();
		Value[] values = new Value[size];
		for (int i = 0; i < size; i++) {
			int index = i + 1;
			Column column = recordColumns.get(i);
			DataType dataType = column.getDataType();
			Value value = db.getValue(rs, index, dataType);
			values[i] = value;
		}
		return values;
	}

	/**
	 * Returns the record of the current row, with the column list of the
	 * select, shared by all the records it reads, and the values of the row.
	 *
	 * @param rs The result set of this select, positioned on the row.
	 * @return The record.
	 * @throws IllegalStateException If the select has no columns, or its
	 *                               columns have no domain.
	 * @throws SQLException          If a database access error occurs, or an
	 *                               object is not valid JSON.
	 * @see #getColumnList()
	 * @see #getValues(DBResultSet)
	 */
	public Record getRecord(DBResultSet rs) throws SQLException {
		return new Record(getColumnList(), getValues(rs));
	}

	/**
	 * Executes the select on its own pooled connection and passes each row to
	 * the callback, until there are no more rows or the callback returns false.
	 * The result set, the statement and the connection are closed in any case.
	 * <p>
	 * The connection is held while the callback runs: a callback that executes
	 * other queries takes other connections from the pool.
	 *
	 * @param cb The callback.
	 * @throws NullPointerException  If the callback is null.
	 * @throws IllegalStateException If the select can not be executed: no FROM
	 *                               clause, no columns, an EXISTS select, or
	 *                               aggregates and other expressions without
	 *                               groupBy().
	 * @throws SQLException          If a database access error occurs, or the
	 *                               callback throws a checked exception, that
	 *                               is then the cause.
	 */
	public void readCallback(Callback cb) throws SQLException {
		Objects.requireNonNull(cb, "Callback can not be null");
		checkExecutable();
		try (DBConnection cn = getDBEngine().getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(toSQL())) {
			while (rs.next()) {
				if (!call(cb, rs)) {
					break;
				}
			}
		}
	}

	/**
	 * Executes the select on its own pooled connection and returns all the rows
	 * read as records, sharing the column list of the select. The result set,
	 * the statement and the connection are closed in any case.
	 * <p>
	 * All the records are kept in memory: large reads go through
	 * {@link #readCallback(Callback)}, processing each record as it is read.
	 *
	 * @return The records, in the order read, empty if there are no rows.
	 * @throws IllegalStateException If the select can not be executed: no FROM
	 *                               clause, no columns, an EXISTS select, or
	 *                               aggregates and other expressions without
	 *                               groupBy(); or its columns have no domain.
	 * @throws SQLException          If a database access error occurs, or an
	 *                               object is not valid JSON.
	 * @see #getRecord(DBResultSet)
	 */
	public List<Record> readList() throws SQLException {
		/* Fails before executing, also when there would be no rows. */
		getColumnList();
		List<Record> recs = new ArrayList<>();
		Callback cb = rs -> {
			recs.add(getRecord(rs));
			return true;
		};
		readCallback(cb);
		return recs;
	}

	/**
	 * Sets the FROM clause with the union of selects, and registers the table
	 * that stands for them. Column types are not validated: the database checks
	 * them.
	 *
	 * @param alias   The alias.
	 * @param selects The wrapped selects.
	 * @param all     True for UNION ALL, false for UNION.
	 */
	private void wrap(String alias, QSelect[] selects, boolean all) {
		if (from != null) {
			throw new IllegalStateException("The FROM clause is already set");
		}
		Objects.requireNonNull(alias, "Alias can not be null");
		Objects.requireNonNull(selects, "Selects can not be null");
		if (selects.length == 0) {
			throw new IllegalArgumentException("A union requires at least one select");
		}
		List<String> aliases = null;
		for (int i = 0; i < selects.length; i++) {
			QSelect select = Objects.requireNonNull(selects[i], "Select can not be null");
			select.checkNested(getDBEngine());
			List<String> selectAliases = new ArrayList<>(select.columns.keySet());
			if (i == 0) {
				aliases = selectAliases;
				continue;
			}
			if (selectAliases.size() != aliases.size()) {
				StringBuilder msg = new StringBuilder();
				msg.append("Wrapped select ");
				msg.append(i + 1);
				msg.append(" has ");
				msg.append(selectAliases.size());
				msg.append(" columns, the first one has ");
				msg.append(aliases.size());
				throw new IllegalArgumentException(msg.toString());
			}
			for (int j = 0; j < aliases.size(); j++) {
				if (!selectAliases.get(j).equals(aliases.get(j))) {
					StringBuilder msg = new StringBuilder();
					msg.append("Column ");
					msg.append(j + 1);
					msg.append(" of wrapped select ");
					msg.append(i + 1);
					msg.append(" is ");
					msg.append(selectAliases.get(j));
					msg.append(", the first select has ");
					msg.append(aliases.get(j));
					throw new IllegalArgumentException(msg.toString());
				}
			}
		}
		Table table = new Table(alias);
		for (QExpr column : selects[0].columns.values()) {
			table.columns().add(column.createColumn());
		}
		scope.checkTable(table, alias);
		from = new QFrom(getDBEngine(), Arrays.asList(selects), all, alias);
		scope.put(alias, table);
	}

	/**
	 * Checks that this select can be attached as an EXISTS select to the
	 * statement of a scope: it was created by its WHERE clause, is not attached
	 * yet, has a FROM clause and columns, and is grouped if required.
	 *
	 * @param outer The scope of the outer statement.
	 * @return The aliases this select takes in the tree: its tables and those
	 *         reserved by its own EXISTS selects.
	 * @throws IllegalArgumentException If the select was not created for that
	 *                                  statement.
	 * @throws IllegalStateException    If the select is already attached, has no
	 *                                  FROM clause or no columns, or has
	 *                                  aggregates and other expressions without
	 *                                  groupBy().
	 */
	Set<String> checkExists(QScope outer) {
		if (scope.getParent() != outer) {
			throw new IllegalArgumentException("The EXISTS select was not created for this statement");
		}
		if (attached) {
			throw new IllegalStateException("The EXISTS select is already attached");
		}
		if (from == null) {
			throw new IllegalStateException("The EXISTS select has no FROM clause");
		}
		if (columns.isEmpty()) {
			throw new IllegalStateException("The EXISTS select has no columns");
		}
		checkGrouped();
		Set<String> aliases = scope.getAliases();
		return aliases;
	}

	/**
	 * Marks this EXISTS select as attached to its outer statement.
	 */
	void attach() {
		attached = true;
	}

	/**
	 * Calls the read callback, translating its checked exceptions other than
	 * SQLException into an SQLException with them as the cause.
	 *
	 * @param cb The callback.
	 * @param rs The result set, positioned on the row.
	 * @return True to go on reading.
	 * @throws SQLException If the callback throws a checked exception.
	 */
	private static boolean call(Callback cb, DBResultSet rs) throws SQLException {
		try {
			return cb.call(rs);
		} catch (SQLException | RuntimeException exc) {
			throw exc;
		} catch (InterruptedException exc) {
			Thread.currentThread().interrupt();
			throw new SQLException("Read callback interrupted", exc);
		} catch (Exception exc) {
			throw new SQLException("Read callback failed", exc);
		}
	}

	/**
	 * Checks that the select can be executed: the FROM clause is set, there are
	 * columns, it is not an EXISTS select, and it is grouped if required.
	 *
	 * @throws IllegalStateException If the select can not be executed.
	 */
	private void checkExecutable() {
		if (from == null) {
			throw new IllegalStateException("The select has no FROM clause");
		}
		if (columns.isEmpty()) {
			throw new IllegalStateException("The select has no columns");
		}
		if (scope.getParent() != null) {
			throw new IllegalStateException("An EXISTS select is executed only within its outer statement");
		}
		checkGrouped();
	}

	/**
	 * Checks that the select can be used inside another statement, wrapped by a
	 * select or as the source of an insert: on the same engine, without ORDER
	 * BY, and executable.
	 *
	 * @param db The engine of the other statement.
	 * @throws IllegalArgumentException If the select has another engine or an
	 *                                  ORDER BY clause.
	 * @throws IllegalStateException    If the select can not be executed.
	 */
	void checkNested(DBEngine db) {
		if (getDBEngine() != db) {
			throw new IllegalArgumentException("A nested select has another database engine");
		}
		if (orderBy != null) {
			throw new IllegalArgumentException("A nested select can not have an ORDER BY clause");
		}
		checkExecutable();
	}

	/**
	 * Returns the number of columns of the select list, used by the statements
	 * that nest the select.
	 *
	 * @return The number of columns.
	 */
	int getColumnCount() {
		return columns.size();
	}

	/**
	 * Checks that a select with aggregates and expressions to group is grouped.
	 * Run when the select is used, attached as an EXISTS select or executed, not
	 * while it is built.
	 *
	 * @throws IllegalStateException If the select has aggregates and expressions
	 *                               to group, and groupBy() was not called.
	 */
	private void checkGrouped() {
		if (groupBy != null) {
			return;
		}
		Map<String, Table> tables = scope.getTables();
		boolean aggregates = false;
		boolean grouped = false;
		for (QExpr column : columns.values()) {
			aggregates |= column.isAggregate();
			grouped |= column.isGrouped(tables);
		}
		if (aggregates && grouped) {
			throw new IllegalStateException(
					"The select has aggregates and other expressions, groupBy() must be called");
		}
	}

	/**
	 * Adds a join. The table is registered only when the join is valid, so that
	 * a rejected join leaves the statement as it was.
	 *
	 * @param type     The type of join.
	 * @param table    The joined table.
	 * @param alias    The alias of the joined table.
	 * @param linkExpr The ON condition.
	 */
	private void addJoin(QJoin.Type type, Table table, String alias, String linkExpr) {
		if (from == null) {
			throw new IllegalStateException("The FROM clause must be set before the joins");
		}
		scope.checkTable(table, alias);
		Map<String, Table> tables = scope.resolve();
		tables.put(alias, table);
		QJoin join = new QJoin(getDBEngine(), type, table, alias, linkExpr, tables);
		scope.put(alias, table);
		joins.add(join);
	}

	/**
	 * Returns the statement as far as it is built, one clause per line: for now
	 * the select list, the FROM clause, the joins, the WHERE clause, the GROUP BY
	 * clause, the HAVING condition and the ORDER BY clause. The select list, the
	 * WHERE clause and the
	 * GROUP BY clause are written only when they have columns, conditions or
	 * expressions to group.
	 *
	 * @return The SQL, empty while the FROM clause is not set.
	 */
	private String toSQL() {
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		if (from == null) {
			return sql.toString();
		}
		if (!columns.isEmpty()) {
			sql.append("SELECT ");
			boolean first = true;
			for (QExpr column : columns.values()) {
				if (!first) {
					sql.append(", ");
				}
				sql.append(column.toSQL(db));
				first = false;
			}
			sql.append("\n");
		}
		sql.append(from.toSQL());
		for (QJoin join : joins) {
			sql.append("\n");
			sql.append(join.toSQL());
		}
		String whereSQL = where.toSQL();
		if (!whereSQL.isEmpty()) {
			sql.append("\n");
			sql.append(whereSQL);
		}
		if (groupBy != null) {
			String groupBySQL = groupBy.toSQL();
			if (!groupBySQL.isEmpty()) {
				sql.append("\n");
				sql.append(groupBySQL);
			}
		}
		if (having != null) {
			sql.append("\nHAVING ");
			sql.append(having.toSQL(db));
		}
		if (orderBy != null) {
			sql.append("\n");
			sql.append(orderBy.toSQL());
		}
		return sql.toString();
	}

	/**
	 * Returns the statement as far as it is built, as {@link #toSQL()} writes
	 * it.
	 *
	 * @return The SQL.
	 */
	@Override
	public String toString() {
		return toSQL();
	}
}
