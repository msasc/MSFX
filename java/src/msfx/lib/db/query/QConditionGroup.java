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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import msfx.lib.db.DBEngine;
import msfx.lib.md.Table;
import msfx.lib.md.Value;

/**
 * A group of conditions of the WHERE clause: the logical operator that links
 * it to the previous group, none for the first one, and its conditions. The
 * first condition of the group has no logical operator, the next ones must
 * have one.
 * <p>
 * Created only by its WHERE clause.
 *
 * @author Miquel Sas
 */
class QConditionGroup {

	/**
	 * The logical operator, or null for the first group.
	 */
	private final Logical logOp;
	/**
	 * The conditions, in the order they were added.
	 */
	private final List<QCondition> conditions = new ArrayList<>();

	/**
	 * Constructor.
	 *
	 * @param logOp The logical operator, or null for the first group.
	 */
	QConditionGroup(Logical logOp) {
		this.logOp = logOp;
	}

	/**
	 * Adds a condition. A rejected condition leaves the group as it was.
	 *
	 * @param logOp    The logical operator, null only for the first condition.
	 * @param expr     The expression.
	 * @param values   The values of the placeholders, in order.
	 * @param tables   The tables the expression can use, by alias.
	 * @param composed True if the expression was composed by the select.
	 * @throws IllegalStateException                If the logical operator is
	 *                                              given for the first condition,
	 *                                              or missing for the next ones.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	void add(Logical logOp, String expr, List<Value> values, Map<String, Table> tables, boolean composed) {
		checkLogical(logOp);
		conditions.add(new QCondition(logOp, expr, values, tables, composed));
	}

	/**
	 * Adds an EXISTS condition.
	 *
	 * @param logOp  The logical operator, null only for the first condition.
	 * @param exists The EXISTS select, already validated.
	 * @throws IllegalStateException If the logical operator is given for the
	 *                               first condition, or missing for the next
	 *                               ones.
	 */
	void add(Logical logOp, QSelect exists) {
		checkLogical(logOp);
		conditions.add(new QCondition(logOp, exists));
	}

	/**
	 * Checks that the first condition has no logical operator or NOT, and the
	 * next ones have a logical operator other than NOT.
	 *
	 * @param logOp The logical operator.
	 * @throws IllegalStateException If the check fails.
	 */
	private void checkLogical(Logical logOp) {
		if (conditions.isEmpty() && logOp != null && logOp != Logical.NOT) {
			throw new IllegalStateException("The first condition of a group has no logical operator, or only NOT");
		}
		if (!conditions.isEmpty() && logOp == null) {
			throw new IllegalStateException("A logical operator is required after the first condition of a group");
		}
		if (!conditions.isEmpty() && logOp == Logical.NOT) {
			throw new IllegalStateException("NOT is only accepted in the first condition of a group, use AND_NOT or OR_NOT");
		}
	}

	/**
	 * Returns the number of conditions.
	 *
	 * @return The number of conditions.
	 */
	int size() {
		return conditions.size();
	}

	/**
	 * Returns the group preceded by its logical operator, if any.
	 *
	 * @param db             The database engine.
	 * @param wrapGroup      True to write the conditions in parentheses.
	 * @param wrapConditions True to write in parentheses the conditions whose
	 *                       root is AND or OR.
	 * @return The SQL.
	 */
	String toSQL(DBEngine db, boolean wrapGroup, boolean wrapConditions) {
		StringBuilder sql = new StringBuilder();
		if (logOp != null) {
			sql.append(logOp.getOperator());
			sql.append(" ");
		}
		if (wrapGroup) {
			sql.append("(");
		}
		for (int i = 0; i < conditions.size(); i++) {
			if (i > 0) {
				sql.append(" ");
			}
			sql.append(conditions.get(i).toSQL(db, wrapConditions));
		}
		if (wrapGroup) {
			sql.append(")");
		}
		return sql.toString();
	}

	/**
	 * Returns the group as written, preceded by its logical operator, if any,
	 * with its conditions in parentheses.
	 *
	 * @return The group as written.
	 */
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		if (logOp != null) {
			b.append(logOp.getOperator());
			b.append(" ");
		}
		b.append("(");
		for (int i = 0; i < conditions.size(); i++) {
			if (i > 0) {
				b.append(" ");
			}
			b.append(conditions.get(i));
		}
		b.append(")");
		return b.toString();
	}
}
