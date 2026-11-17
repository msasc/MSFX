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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import msfx.lib.db.DBEngine;
import msfx.lib.md.Table;
import msfx.lib.md.Value;

/**
 * WHERE clause of a statement: a list of groups of conditions. It starts with
 * an empty first group, without logical operator; conditions are added to the
 * last group, and a new group is linked to the previous ones by its logical
 * operator.
 * <p>
 * The conditions are built through the statement's clause, with the tables
 * the statement has registered, and those of its outer statements for an
 * EXISTS select:
 *
 * <pre>
 * QWhere where = select.getWhere();
 * where.cond("P.CPTYPE = ?", type);
 * where.value(Logical.AND, "P.ISPAREPART", Comparison.EQ, "Y");
 * </pre>
 *
 * Groups are written in parentheses when there are several and they have more
 * than one condition, and conditions whose root is AND or OR are written in
 * parentheses when they are not alone, so that the logical operators link
 * them as they were added. Within a group, AND binds before OR, as in SQL.
 * <p>
 * Created only by its statement, and shares its scope.
 *
 * @author Miquel Sas
 */
public class QWhere extends QBase {

	/**
	 * The tables of the statement, by alias, shared with it.
	 */
	private final QScope scope;
	/**
	 * The groups, in the order they were added, the first one always present.
	 */
	private final List<QConditionGroup> groups = new ArrayList<>();

	/**
	 * Constructor.
	 *
	 * @param db    The database engine.
	 * @param scope The scope of the statement.
	 */
	QWhere(DBEngine db, QScope scope) {
		super(db);
		this.scope = scope;
		groups.add(new QConditionGroup(null));
	}

	/**
	 * Adds a new group of conditions, linked to the previous ones by a logical
	 * operator. The next condition is the first of the group.
	 *
	 * @param logOp The logical operator.
	 * @throws NullPointerException     If the logical operator is null.
	 * @throws IllegalArgumentException If the logical operator is NOT.
	 * @throws IllegalStateException    If the FROM clause is not set, or the
	 *                                  last group has no conditions.
	 */
	public void group(Logical logOp) {
		checkFrom();
		Objects.requireNonNull(logOp, "Logical operator can not be null");
		if (logOp == Logical.NOT) {
			throw new IllegalArgumentException("A group is linked with AND, AND_NOT, OR or OR_NOT, not NOT");
		}
		if (groups.getLast().size() == 0) {
			throw new IllegalStateException("The last group has no conditions");
		}
		groups.add(new QConditionGroup(logOp));
	}

	/**
	 * Adds the first condition of the last group, with optional placeholders
	 * <code>?</code> replaced by the values when written.
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
	public void cond(String expr, Value... values) {
		addCondition(null, expr, values, false);
	}

	/**
	 * Adds a condition to the last group, linked to the previous one by the
	 * logical operator, or the first one negated by NOT, with optional
	 * placeholders <code>?</code> replaced by the values when written.
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
	public void cond(Logical logOp, String expr, Value... values) {
		Logical op = requireLogical(logOp);
		addCondition(op, expr, values, false);
	}

	/**
	 * Adds the first condition of the last group, comparing two expressions.
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
	public void expr(String leftExpr, Comparison comOp, String rightExpr) {
		String expr = compare(leftExpr, comOp, rightExpr);
		addCondition(null, expr, new Value[0], true);
	}

	/**
	 * Adds a condition to the last group, linked to the previous one by the
	 * logical operator, or the first one negated by NOT, comparing two
	 * expressions.
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
	public void expr(Logical logOp, String leftExpr, Comparison comOp, String rightExpr) {
		Logical op = requireLogical(logOp);
		String expr = compare(leftExpr, comOp, rightExpr);
		addCondition(op, expr, new Value[0], true);
	}

	/**
	 * Adds the first condition of the last group, comparing an expression with
	 * a string.
	 *
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The string.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If the value is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the group has already
	 *                                              conditions.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void value(String expr, Comparison comOp, String value) {
		addValue(null, expr, comOp, new Value(value));
	}

	/**
	 * Adds the first condition of the last group, comparing an expression with
	 * an integer.
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
	public void value(String expr, Comparison comOp, int value) {
		addValue(null, expr, comOp, new Value(value));
	}

	/**
	 * Adds the first condition of the last group, comparing an expression with
	 * a long.
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
	public void value(String expr, Comparison comOp, long value) {
		addValue(null, expr, comOp, new Value(value));
	}

	/**
	 * Adds the first condition of the last group, comparing an expression with
	 * a double.
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
	public void value(String expr, Comparison comOp, double value) {
		addValue(null, expr, comOp, new Value(value));
	}

	/**
	 * Adds the first condition of the last group, comparing an expression with
	 * a decimal.
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
	public void value(String expr, Comparison comOp, BigDecimal value) {
		addValue(null, expr, comOp, new Value(value));
	}

	/**
	 * Adds the first condition of the last group, comparing an expression with
	 * a value of any type.
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
	public void value(String expr, Comparison comOp, Value value) {
		addValue(null, expr, comOp, value);
	}

	/**
	 * Adds a condition to the last group, linked to the previous one by the
	 * logical operator, or the first one negated by NOT, comparing an
	 * expression with a string.
	 *
	 * @param logOp The logical operator.
	 * @param expr  The expression.
	 * @param comOp The comparison operator.
	 * @param value The string.
	 * @throws NullPointerException                 If an argument is null.
	 * @throws IllegalArgumentException             If the value is null.
	 * @throws IllegalStateException                If the FROM clause is not set,
	 *                                              or the logical operator does
	 *                                              not fit the position of the
	 *                                              condition.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	public void value(Logical logOp, String expr, Comparison comOp, String value) {
		Logical op = requireLogical(logOp);
		addValue(op, expr, comOp, new Value(value));
	}

	/**
	 * Adds a condition to the last group, linked to the previous one by the
	 * logical operator, or the first one negated by NOT, comparing an
	 * expression with an integer.
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
	public void value(Logical logOp, String expr, Comparison comOp, int value) {
		Logical op = requireLogical(logOp);
		addValue(op, expr, comOp, new Value(value));
	}

	/**
	 * Adds a condition to the last group, linked to the previous one by the
	 * logical operator, or the first one negated by NOT, comparing an
	 * expression with a long.
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
	public void value(Logical logOp, String expr, Comparison comOp, long value) {
		Logical op = requireLogical(logOp);
		addValue(op, expr, comOp, new Value(value));
	}

	/**
	 * Adds a condition to the last group, linked to the previous one by the
	 * logical operator, or the first one negated by NOT, comparing an
	 * expression with a double.
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
	public void value(Logical logOp, String expr, Comparison comOp, double value) {
		Logical op = requireLogical(logOp);
		addValue(op, expr, comOp, new Value(value));
	}

	/**
	 * Adds a condition to the last group, linked to the previous one by the
	 * logical operator, or the first one negated by NOT, comparing an
	 * expression with a decimal.
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
	public void value(Logical logOp, String expr, Comparison comOp, BigDecimal value) {
		Logical op = requireLogical(logOp);
		addValue(op, expr, comOp, new Value(value));
	}

	/**
	 * Adds a condition to the last group, linked to the previous one by the
	 * logical operator, or the first one negated by NOT, comparing an
	 * expression with a value of any type.
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
	public void value(Logical logOp, String expr, Comparison comOp, Value value) {
		Logical op = requireLogical(logOp);
		addValue(op, expr, comOp, value);
	}

	/**
	 * Adds the first condition of the last group, an IN of an expression in a
	 * list of values. For NOT IN as the first condition, use
	 * {@link #in(Logical, String, Value...)} with NOT.
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
	public void in(String expr, Value... values) {
		addIn(null, expr, values);
	}

	/**
	 * Adds a condition to the last group, linked to the previous one by the
	 * logical operator, or the first one negated by NOT, an IN of an expression
	 * in a list of values. NOT, AND_NOT and OR_NOT give a NOT IN.
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
	public void in(Logical logOp, String expr, Value... values) {
		Logical op = requireLogical(logOp);
		addIn(op, expr, values);
	}

	/**
	 * Creates a select for an EXISTS condition of this clause, to be attached
	 * with {@link #exists(QSelect)}. Its expressions can use the tables of this
	 * statement and of its outer ones.
	 *
	 * @return The EXISTS select.
	 */
	public QSelect createExists() {
		QSelect exists = new QSelect(getDBEngine(), scope);
		return exists;
	}

	/**
	 * Adds the first condition of the last group, an EXISTS of a select created
	 * by this clause. For NOT EXISTS as the first condition, use
	 * {@link #exists(Logical, QSelect)} with NOT.
	 *
	 * @param exists The EXISTS select, created with {@link #createExists()}.
	 * @throws NullPointerException     If the select is null.
	 * @throws IllegalArgumentException If the select was not created by this
	 *                                  clause, or uses an alias already used.
	 * @throws IllegalStateException    If the FROM clause of either statement is
	 *                                  not set, the EXISTS select has no columns,
	 *                                  has aggregates and other expressions
	 *                                  without groupBy(), or is already attached,
	 *                                  or the group has already conditions.
	 */
	public void exists(QSelect exists) {
		addExists(null, exists);
	}

	/**
	 * Adds a condition to the last group, linked to the previous one by the
	 * logical operator, or the first one negated by NOT, an EXISTS of a select
	 * created by this clause. NOT, AND_NOT and OR_NOT give a NOT EXISTS.
	 *
	 * @param logOp  The logical operator.
	 * @param exists The EXISTS select, created with {@link #createExists()}.
	 * @throws NullPointerException     If an argument is null.
	 * @throws IllegalArgumentException If the select was not created by this
	 *                                  clause, or uses an alias already used.
	 * @throws IllegalStateException    If the FROM clause of either statement is
	 *                                  not set, the EXISTS select has no columns,
	 *                                  has aggregates and other expressions
	 *                                  without groupBy(), or is already attached,
	 *                                  or the logical operator does not fit the
	 *                                  position of the condition.
	 */
	public void exists(Logical logOp, QSelect exists) {
		Logical op = requireLogical(logOp);
		addExists(op, exists);
	}

	/**
	 * Checks that the statement has registered its tables, before the
	 * conditions.
	 *
	 * @throws IllegalStateException If the FROM clause is not set.
	 */
	private void checkFrom() {
		if (scope.isEmpty()) {
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
		String condition = compare(expr, comOp, "?");
		addCondition(logOp, condition, new Value[] { value }, true);
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
	 * Adds a condition to the last group. A rejected condition leaves the clause
	 * as it was.
	 *
	 * @param logOp    The logical operator, or null for the first condition.
	 * @param expr     The condition.
	 * @param values   The values of the placeholders.
	 * @param composed True if the condition was composed by this clause.
	 */
	private void addCondition(Logical logOp, String expr, Value[] values, boolean composed) {
		checkFrom();
		Objects.requireNonNull(values, "Values can not be null");
		List<Value> list = Arrays.asList(values);
		Map<String, Table> tables = scope.resolve();
		groups.getLast().add(logOp, expr, list, tables, composed);
	}

	/**
	 * Attaches an EXISTS select to the last group, and reserves its aliases in
	 * this scope and the outer ones. Its aliases must not be used by this
	 * statement or the outer ones; sibling EXISTS selects can reuse them, since
	 * none sees the other. A rejected select leaves the statement as it was.
	 *
	 * @param logOp  The logical operator, or null for the first condition.
	 * @param exists The EXISTS select.
	 */
	private void addExists(Logical logOp, QSelect exists) {
		checkFrom();
		Objects.requireNonNull(exists, "EXISTS select can not be null");
		Set<String> aliases = exists.checkExists(scope);
		Map<String, Table> tables = scope.resolve();
		for (String alias : aliases) {
			if (tables.containsKey(alias)) {
				StringBuilder msg = new StringBuilder();
				msg.append("Alias ");
				msg.append(alias);
				msg.append(" of the EXISTS select is already used");
				throw new IllegalArgumentException(msg.toString());
			}
		}
		groups.getLast().add(logOp, exists);
		scope.reserve(aliases);
		exists.attach();
	}

	/**
	 * Checks whether the clause has no conditions, without writing it.
	 *
	 * @return True if no group has conditions.
	 */
	boolean isEmpty() {
		for (QConditionGroup group : groups) {
			if (group.size() > 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns the clause, with a line per group. A last group still empty is
	 * not written.
	 *
	 * @return The SQL, empty if there are no conditions.
	 */
	String toSQL() {
		DBEngine db = getDBEngine();
		List<QConditionGroup> written = new ArrayList<>();
		int conditions = 0;
		for (QConditionGroup group : groups) {
			if (group.size() > 0) {
				written.add(group);
				conditions += group.size();
			}
		}
		StringBuilder sql = new StringBuilder();
		if (written.isEmpty()) {
			return sql.toString();
		}
		sql.append("WHERE ");
		for (int i = 0; i < written.size(); i++) {
			QConditionGroup group = written.get(i);
			if (i > 0) {
				sql.append("\n");
			}
			boolean wrapGroup = written.size() > 1 && group.size() > 1;
			sql.append(group.toSQL(db, wrapGroup, conditions > 1));
		}
		return sql.toString();
	}

	/**
	 * Returns the clause as far as it is built, as {@link #toSQL()} writes it.
	 *
	 * @return The SQL.
	 */
	@Override
	public String toString() {
		return toSQL();
	}
}
