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
import java.util.Objects;

import msfx.lib.db.DBEngine;
import msfx.lib.md.Table;
import msfx.lib.md.Value;

/**
 * WHERE clause of a statement: a list of groups of conditions. It starts with
 * an empty first group, without logical operator; conditions are added to the
 * last group, and a new group is linked to the previous ones by its logical
 * operator.
 * <p>
 * Groups are written in parentheses when there are several and they have more
 * than one condition, and conditions whose root is AND or OR are written in
 * parentheses when they are not alone, so that the logical operators link
 * them as they were added. Within a group, AND binds before OR, as in SQL.
 * <p>
 * Created only by its statement.
 *
 * @author Miquel Sas
 */
public class QWhere extends QBase {

	/**
	 * The groups, in the order they were added, the first one always present.
	 */
	private final List<QConditionGroup> groups = new ArrayList<>();

	/**
	 * Constructor.
	 *
	 * @param db The database engine.
	 */
	QWhere(DBEngine db) {
		super(db);
		groups.add(new QConditionGroup(null));
	}

	/**
	 * Adds a condition to the last group.
	 *
	 * @param logOp    The logical operator, null only for the first condition of
	 *                 the group.
	 * @param expr     The expression.
	 * @param values   The values of the placeholders, in order.
	 * @param tables   The tables the expression can use, by alias.
	 * @param composed True if the expression was composed by the statement.
	 * @throws IllegalStateException                If the logical operator is
	 *                                              given for the first condition
	 *                                              of the group, or missing for
	 *                                              the next ones.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	void add(Logical logOp, String expr, List<Value> values, Map<String, Table> tables, boolean composed) {
		groups.getLast().add(logOp, expr, values, tables, composed);
	}

	/**
	 * Adds an EXISTS condition to the last group.
	 *
	 * @param logOp  The logical operator, null only for the first condition of
	 *               the group.
	 * @param exists The EXISTS select, already validated.
	 * @throws IllegalStateException If the logical operator is given for the
	 *                               first condition of the group, or missing for
	 *                               the next ones.
	 */
	void add(Logical logOp, QSelect exists) {
		groups.getLast().add(logOp, exists);
	}

	/**
	 * Adds a new group, linked to the previous ones by a logical operator.
	 *
	 * @param logOp The logical operator.
	 * @throws NullPointerException     If the logical operator is null.
	 * @throws IllegalArgumentException If the logical operator is NOT.
	 * @throws IllegalStateException    If the last group has no conditions.
	 */
	void group(Logical logOp) {
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
