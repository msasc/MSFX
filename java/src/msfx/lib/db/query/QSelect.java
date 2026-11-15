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

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
import msfx.lib.util.Numbers;

/**
 * SELECT statement.
 * <p>
 * Every table gets an alias, required and unique ignoring case, that is the
 * name the expressions of the statement use to refer to its columns.
 * <p>
 * A select created for an EXISTS condition of an outer select also sees the
 * tables of the outer selects. Aliases are unique in the whole tree of
 * selects, so that an inner alias never hides an outer one.
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
	 * The outer select of an EXISTS select, or null.
	 */
	private final QSelect outer;
	/**
	 * Tables used, by alias. Aliases are unique ignoring case, and keep the case
	 * of their first spelling. Filled by the FROM clause and the joins.
	 */
	private final Map<String, Table> tables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	/**
	 * Aliases used by the EXISTS selects attached to this select or to its inner
	 * selects. They can not be used by this select, whose expressions do not see
	 * their tables.
	 */
	private final Set<String> reserved = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	/**
	 * True once this EXISTS select is attached to its outer select.
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
	 * Columns of the records read, one per column of the select list, named
	 * after its alias, with the definition of its domain, in the order they were
	 * added. Empty when the columns have no domain: all the columns have a
	 * domain or none has.
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
		outer = null;
		where = new QWhere(db);
	}

	/**
	 * Constructor of a select for an EXISTS condition of an outer select, to be
	 * attached to it with whereExists. Its expressions can use the tables of the
	 * outer selects.
	 *
	 * @param outer The outer select.
	 * @throws NullPointerException If the outer select is null.
	 */
	public QSelect(QSelect outer) {
		super(Objects.requireNonNull(outer, "Outer select can not be null").getDBEngine());
		this.outer = outer;
		where = new QWhere(getDBEngine());
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
		checkTable(table, alias);
		from = new QFrom(getDBEngine(), table, alias);
		tables.put(alias, table);
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
		addColumn(expr, alias, null);
	}

	/**
	 * Adds a column to the select list, with the domain that defines its type.
	 * The expression can use the columns of the tables already registered, so
	 * columns are added after the FROM clause and the joins they use.
	 *
	 * @param expr   The expression.
	 * @param alias  The alias of the column in the result, unique ignoring case.
	 * @param domain The domain, or null. All the columns have a domain or none
	 *               has.
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
		/* Validate mixing call with and without domain */
		boolean canAdd = (domain != null && columns.size() == recordColumns.size());
		canAdd |= (domain == null && recordColumns.isEmpty());
		if (!canAdd) {
			StringBuilder msg = new StringBuilder();
			msg.append("You are mixing call with and without a Domain");
			throw new IllegalArgumentException(msg.toString());
		}
		/* Do add it. */
		columns.put(key, new QExpr(expr, alias, domain, scope()));
		if (domain != null) {
			recordColumns.add(new Column(alias, domain));
		}
	}

	/**
	 * Adds a new group of conditions to the WHERE clause, linked to the previous
	 * ones by a logical operator. The next condition is the first of the group.
	 *
	 * @param logOp The logical operator.
	 * @throws NullPointerException     If the logical operator is null.
	 * @throws IllegalArgumentException If the logical operator is NOT.
	 * @throws IllegalStateException    If the FROM clause is not set, or the
	 *                                  last group has no conditions.
	 */
	public void whereGroup(Logical logOp) {
		checkFrom();
		where.group(logOp);
	}

	/**
	 * Adds the first condition of the last group of the WHERE clause, with
	 * optional placeholders <code>?</code> replaced by the values when written.
	 *
	 * @param expr   The condition.
	 * @param values The values of the placeholders, in order.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If a value is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the group has already
	 *                                              conditions.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the condition is wrong.
	 */
	public void whereCond(String expr, Value... values) {
		addCondition(null, expr, values, false);
	}

	/**
	 * Adds a condition to the last group of the WHERE clause, linked to the
	 * previous one by the logical operator, or the first one negated by NOT,
	 * with optional placeholders <code>?</code> replaced by the values when
	 * written.
	 *
	 * @param logOp  The logical operator.
	 * @param expr   The condition.
	 * @param values The values of the placeholders, in order.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If a value is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the logical operator does
	 *                                              not fit the position of the
	 *                                              condition.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the condition is wrong.
	 */
	public void whereCond(Logical logOp, String expr, Value... values) {
		addCondition(requireLogical(logOp), expr, values, false);
	}

	/**
	 * Adds the first condition of the last group of the WHERE clause, comparing
	 * two expressions.
	 *
	 * @param leftExpr  The left expression.
	 * @param comOp     The comparison operator.
	 * @param rightExpr The right expression.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the group has already
	 *                                              conditions.
	 * @throws msfx.lib.db.query.sa.SyntaxException If an expression is wrong.
	 */
	public void whereExpr(String leftExpr, Comparison comOp, String rightExpr) {
		addCondition(null, compare(leftExpr, comOp, rightExpr), new Value[0], true);
	}

	/**
	 * Adds a condition to the last group of the WHERE clause, linked to the
	 * previous one by the logical operator, or the first one negated by NOT,
	 * comparing two expressions.
	 *
	 * @param logOp     The logical operator.
	 * @param leftExpr  The left expression.
	 * @param comOp     The comparison operator.
	 * @param rightExpr The right expression.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the logical operator does
	 *                                              not fit the position of the
	 *                                              condition.
	 * @throws msfx.lib.db.query.sa.SyntaxException If an expression is wrong.
	 */
	public void whereExpr(Logical logOp, String leftExpr, Comparison comOp, String rightExpr) {
		addCondition(requireLogical(logOp), compare(leftExpr, comOp, rightExpr), new Value[0], true);
	}

	/**
	 * Adds the first condition of the last group of the WHERE clause, comparing
	 * an expression with a string.
	 *
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The string, written as a literal.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If the value is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the group has already
	 *                                              conditions.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void whereValue(String expr, Comparison comOp, String value) {
		addValue(null, expr, comOp, new Value(value));
	}

	/**
	 * Adds the first condition of the last group of the WHERE clause, comparing
	 * an expression with an integer.
	 *
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The integer.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the group has already
	 *                                              conditions.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void whereValue(String expr, Comparison comOp, int value) {
		addValue(null, expr, comOp, new Value(value));
	}

	/**
	 * Adds the first condition of the last group of the WHERE clause, comparing
	 * an expression with a long.
	 *
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The long.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the group has already
	 *                                              conditions.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void whereValue(String expr, Comparison comOp, long value) {
		addValue(null, expr, comOp, new Value(value));
	}

	/**
	 * Adds the first condition of the last group of the WHERE clause, comparing
	 * an expression with a double.
	 *
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The double.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the group has already
	 *                                              conditions.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void whereValue(String expr, Comparison comOp, double value) {
		addValue(null, expr, comOp, new Value(value));
	}

	/**
	 * Adds the first condition of the last group of the WHERE clause, comparing
	 * an expression with a decimal.
	 *
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The decimal.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If the value is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the group has already
	 *                                              conditions.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void whereValue(String expr, Comparison comOp, BigDecimal value) {
		addValue(null, expr, comOp, new Value(value));
	}

	/**
	 * Adds the first condition of the last group of the WHERE clause, comparing
	 * an expression with a value of any type.
	 *
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The value.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If the value is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the group has already
	 *                                              conditions.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void whereValue(String expr, Comparison comOp, Value value) {
		addValue(null, expr, comOp, value);
	}

	/**
	 * Adds a condition to the last group of the WHERE clause, linked to the
	 * previous one by the logical operator, or the first one negated by NOT,
	 * comparing an expression with a string.
	 *
	 * @param logOp The logical operator.
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The string, written as a literal.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If the value is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the logical operator does
	 *                                              not fit the position of the
	 *                                              condition.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void whereValue(Logical logOp, String expr, Comparison comOp, String value) {
		addValue(requireLogical(logOp), expr, comOp, new Value(value));
	}

	/**
	 * Adds a condition to the last group of the WHERE clause, linked to the
	 * previous one by the logical operator, or the first one negated by NOT,
	 * comparing an expression with an integer.
	 *
	 * @param logOp The logical operator.
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The integer.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the logical operator does
	 *                                              not fit the position of the
	 *                                              condition.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void whereValue(Logical logOp, String expr, Comparison comOp, int value) {
		addValue(requireLogical(logOp), expr, comOp, new Value(value));
	}

	/**
	 * Adds a condition to the last group of the WHERE clause, linked to the
	 * previous one by the logical operator, or the first one negated by NOT,
	 * comparing an expression with a long.
	 *
	 * @param logOp The logical operator.
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The long.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the logical operator does
	 *                                              not fit the position of the
	 *                                              condition.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void whereValue(Logical logOp, String expr, Comparison comOp, long value) {
		addValue(requireLogical(logOp), expr, comOp, new Value(value));
	}

	/**
	 * Adds a condition to the last group of the WHERE clause, linked to the
	 * previous one by the logical operator, or the first one negated by NOT,
	 * comparing an expression with a double.
	 *
	 * @param logOp The logical operator.
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The double.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the logical operator does
	 *                                              not fit the position of the
	 *                                              condition.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void whereValue(Logical logOp, String expr, Comparison comOp, double value) {
		addValue(requireLogical(logOp), expr, comOp, new Value(value));
	}

	/**
	 * Adds a condition to the last group of the WHERE clause, linked to the
	 * previous one by the logical operator, or the first one negated by NOT,
	 * comparing an expression with a decimal.
	 *
	 * @param logOp The logical operator.
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The decimal.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If the value is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the logical operator does
	 *                                              not fit the position of the
	 *                                              condition.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void whereValue(Logical logOp, String expr, Comparison comOp, BigDecimal value) {
		addValue(requireLogical(logOp), expr, comOp, new Value(value));
	}

	/**
	 * Adds a condition to the last group of the WHERE clause, linked to the
	 * previous one by the logical operator, or the first one negated by NOT,
	 * comparing an expression with a value of any type.
	 *
	 * @param logOp The logical operator.
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The value.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If the value is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the logical operator does
	 *                                              not fit the position of the
	 *                                              condition.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void whereValue(Logical logOp, String expr, Comparison comOp, Value value) {
		addValue(requireLogical(logOp), expr, comOp, value);
	}

	/**
	 * Adds the first condition of the last group of the WHERE clause, an IN of
	 * an expression in a list of values. For NOT IN as the first condition, use
	 * {@link #whereIn(Logical, String, Value...)} with NOT.
	 *
	 * @param expr   The expression.
	 * @param values The values, at least one.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If there are no values, or a
	 *                                              value is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the group has already
	 *                                              conditions.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void whereIn(String expr, Value... values) {
		addIn(null, expr, values);
	}

	/**
	 * Adds a condition to the last group of the WHERE clause, linked to the
	 * previous one by the logical operator, or the first one negated by NOT,
	 * an IN of an expression in a list of values. NOT, AND_NOT and OR_NOT give
	 * a NOT IN.
	 *
	 * @param logOp  The logical operator.
	 * @param expr   The expression.
	 * @param values The values, at least one.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If there are no values, or a
	 *                                              value is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the logical operator does
	 *                                              not fit the position of the
	 *                                              condition.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void whereIn(Logical logOp, String expr, Value... values) {
		addIn(requireLogical(logOp), expr, values);
	}

	/**
	 * Adds the first condition of the last group of the WHERE clause, an EXISTS
	 * of a select created for this one. For NOT EXISTS as the first condition,
	 * use {@link #whereExists(Logical, QSelect)} with NOT.
	 *
	 * @param exists The EXISTS select, created with this select as its outer
	 *               select.
	 * @throws NullPointerException     If the select is null.
	 * @throws IllegalArgumentException If the select was not created for this
	 *                                  one, or uses an alias already used.
	 * @throws IllegalStateException    If the FROM clause of either select is
	 *                                  not set, the EXISTS select has no
	 *                                  columns, has aggregates and other
	 *                                  expressions without groupBy(), or is
	 *                                  already attached, or the group has
	 *                                  already conditions.
	 */
	public void whereExists(QSelect exists) {
		addExists(null, exists);
	}

	/**
	 * Adds a condition to the last group of the WHERE clause, linked to the
	 * previous one by the logical operator, or the first one negated by NOT,
	 * an EXISTS of a select created for this one. NOT, AND_NOT and OR_NOT give
	 * a NOT EXISTS.
	 *
	 * @param logOp  The logical operator.
	 * @param exists The EXISTS select, created with this select as its outer
	 *               select.
	 * @throws NullPointerException     If an argument is null.
	 * @throws IllegalArgumentException If the select was not created for this
	 *                                  one, or uses an alias already used.
	 * @throws IllegalStateException    If the FROM clause of either select is
	 *                                  not set, the EXISTS select has no
	 *                                  columns, has aggregates and other
	 *                                  expressions without groupBy(), or is
	 *                                  already attached, or the logical
	 *                                  operator does not fit the position of
	 *                                  the condition.
	 */
	public void whereExists(Logical logOp, QSelect exists) {
		addExists(requireLogical(logOp), exists);
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
		having = Analysis.analyze(expr, Clause.HAVING, scope(), Arrays.asList(values), selectList);
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
	 * select, in the order of the select, named after its alias, with the
	 * definition of its domain. The columns are created when added to the
	 * select and belong to no table.
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
		int size = recordColumns.size();
		Value[] values = new Value[size];
		for (int i = 0; i < size; i++) {
			int index = i + 1;
			Column column = recordColumns.get(i);
			DataType dataType = column.getDataType();
			Value value = switch (dataType.type()) {
				case BOOLEAN:
					yield new Value(rs.getBoolean(index));
				case DECIMAL:
					yield new Value(Numbers.toScale(rs.getDecimal(index), dataType.decimals()));
				case DOUBLE:
					yield new Value(rs.getDouble(index));
				case INTEGER:
					yield new Value(rs.getInteger(index));
				case LONG:
					yield new Value(rs.getLong(index));
				case DATE:
					yield new Value(rs.getDate(index));
				case TIME:
					yield new Value(rs.getTime(index));
				case DATETIME:
					yield new Value(rs.getDateTime(index));
				case STRING: {
					String string = rs.getString(index);
					yield new Value(string != null ? string : "");
				}
				case BINARY:
					yield new Value(rs.getBinary(index));
				case OBJECT:
					yield new Value(rs.getObject(index));
			};
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
		checkTable(table, alias);
		from = new QFrom(getDBEngine(), Arrays.asList(selects), all, alias);
		tables.put(alias, table);
	}

	/**
	 * Checks that the FROM clause is set, before the conditions.
	 *
	 * @throws IllegalStateException If the FROM clause is not set.
	 */
	private void checkFrom() {
		if (from == null) {
			throw new IllegalStateException("The FROM clause must be set before the conditions");
		}
	}

	/**
	 * Checks that a logical operator is given, in the methods that take one.
	 *
	 * @param logOp The logical operator.
	 * @return The logical operator.
	 * @throws NullPointerException If the logical operator is null.
	 */
	private static Logical requireLogical(Logical logOp) {
		return Objects.requireNonNull(logOp, "Logical operator can not be null");
	}

	/**
	 * Composes the comparison of two expressions.
	 *
	 * @param leftExpr  The left expression.
	 * @param comOp     The comparison operator.
	 * @param rightExpr The right expression.
	 * @return The comparison.
	 * @throws NullPointerException If an argument is null.
	 */
	private static String compare(String leftExpr, Comparison comOp, String rightExpr) {
		Objects.requireNonNull(leftExpr, "Expression can not be null");
		Objects.requireNonNull(comOp, "Comparison operator can not be null");
		Objects.requireNonNull(rightExpr, "Expression can not be null");
		StringBuilder b = new StringBuilder();
		b.append(leftExpr);
		b.append(" ");
		b.append(comOp.getOperator());
		b.append(" ");
		b.append(rightExpr);
		return b.toString();
	}

	/**
	 * Adds the comparison of an expression with a value, written as a
	 * placeholder.
	 *
	 * @param logOp The logical operator, or null for the first condition.
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The value.
	 */
	private void addValue(Logical logOp, String expr, Comparison comOp, Value value) {
		Objects.requireNonNull(value, "Value can not be null");
		addCondition(logOp, compare(expr, comOp, "?"), new Value[] { value }, true);
	}

	/**
	 * Adds the IN of an expression in a list of values, written as placeholders.
	 *
	 * @param logOp  The logical operator, or null for the first condition.
	 * @param expr   The expression.
	 * @param values The values.
	 */
	private void addIn(Logical logOp, String expr, Value[] values) {
		Objects.requireNonNull(expr, "Expression can not be null");
		Objects.requireNonNull(values, "Values can not be null");
		if (values.length == 0) {
			throw new IllegalArgumentException("IN requires at least one value");
		}
		StringBuilder b = new StringBuilder();
		b.append(expr);
		b.append(" IN (");
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				b.append(", ");
			}
			b.append("?");
		}
		b.append(")");
		addCondition(logOp, b.toString(), values, true);
	}

	/**
	 * Adds a condition to the last group of the WHERE clause. A rejected
	 * condition leaves the statement as it was.
	 *
	 * @param logOp    The logical operator, or null for the first condition.
	 * @param expr     The condition.
	 * @param values   The values of the placeholders.
	 * @param composed True if the condition was composed by this select.
	 */
	private void addCondition(Logical logOp, String expr, Value[] values, boolean composed) {
		checkFrom();
		Objects.requireNonNull(values, "Values can not be null");
		where.add(logOp, expr, Arrays.asList(values), scope(), composed);
	}

	/**
	 * Attaches an EXISTS select to the last group of the WHERE clause, and
	 * reserves its aliases in this select and the outer ones. Its aliases must
	 * not be used by this select or the outer ones; sibling EXISTS selects can
	 * reuse them, since none sees the other. A rejected select leaves the
	 * statement as it was.
	 *
	 * @param logOp  The logical operator, or null for the first condition.
	 * @param exists The EXISTS select.
	 */
	private void addExists(Logical logOp, QSelect exists) {
		checkFrom();
		Objects.requireNonNull(exists, "EXISTS select can not be null");
		if (exists.outer != this) {
			throw new IllegalArgumentException("The EXISTS select was not created for this select");
		}
		if (exists.attached) {
			throw new IllegalStateException("The EXISTS select is already attached");
		}
		if (exists.from == null) {
			throw new IllegalStateException("The EXISTS select has no FROM clause");
		}
		if (exists.columns.isEmpty()) {
			throw new IllegalStateException("The EXISTS select has no columns");
		}
		exists.checkGrouped();
		Set<String> aliases = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		aliases.addAll(exists.tables.keySet());
		aliases.addAll(exists.reserved);
		Map<String, Table> scope = scope();
		for (String alias : aliases) {
			if (scope.containsKey(alias)) {
				StringBuilder msg = new StringBuilder();
				msg.append("Alias ");
				msg.append(alias);
				msg.append(" of the EXISTS select is already used");
				throw new IllegalArgumentException(msg.toString());
			}
		}
		where.add(logOp, exists);
		for (QSelect select = this; select != null; select = select.outer) {
			select.reserved.addAll(aliases);
		}
		exists.attached = true;
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
		if (outer != null) {
			throw new IllegalStateException("An EXISTS select is executed only within its outer select");
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
	 * Returns the tables the expressions of this select can use: its own tables
	 * and those of the outer selects.
	 *
	 * @return The tables, by alias.
	 */
	private Map<String, Table> scope() {
		Map<String, Table> scope = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		if (outer != null) {
			scope.putAll(outer.scope());
		}
		scope.putAll(tables);
		return scope;
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
		checkTable(table, alias);
		Map<String, Table> scope = scope();
		scope.put(alias, table);
		QJoin join = new QJoin(getDBEngine(), type, table, alias, linkExpr, scope);
		tables.put(alias, table);
		joins.add(join);
	}

	/**
	 * Checks a table and its alias before registering them.
	 *
	 * @param table The table.
	 * @param alias The alias.
	 * @throws NullPointerException     If the table or the alias is null.
	 * @throws IllegalArgumentException If the alias is blank, or is already used
	 *                                  by this select, an outer one or an
	 *                                  attached EXISTS select, compared ignoring
	 *                                  case.
	 */
	private void checkTable(Table table, String alias) {
		Objects.requireNonNull(table, "Table can not be null");
		Objects.requireNonNull(alias, "Alias can not be null");
		if (alias.isBlank()) {
			throw new IllegalArgumentException("Alias can not be blank");
		}
		Map<String, Table> scope = scope();
		if (scope.containsKey(alias)) {
			StringBuilder msg = new StringBuilder();
			msg.append("Alias ");
			msg.append(alias);
			msg.append(" is already used by table ");
			msg.append(scope.get(alias).getName());
			throw new IllegalArgumentException(msg.toString());
		}
		if (reserved.contains(alias)) {
			StringBuilder msg = new StringBuilder();
			msg.append("Alias ");
			msg.append(alias);
			msg.append(" is already used by an EXISTS select");
			throw new IllegalArgumentException(msg.toString());
		}
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
