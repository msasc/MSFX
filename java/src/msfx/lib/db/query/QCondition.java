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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import msfx.lib.db.DBEngine;
import msfx.lib.db.query.sa.Analysis;
import msfx.lib.db.query.sa.Clause;
import msfx.lib.db.query.sa.Node;
import msfx.lib.db.query.sa.SyntaxException;
import msfx.lib.md.Table;
import msfx.lib.md.Value;

/**
 * A condition of a group of the WHERE clause: the logical operator that links
 * it to the previous condition, none for the first one, and either an
 * expression or an EXISTS select.
 * <p>
 * The expression is validated when the condition is created, against the
 * tables registered at that moment, with the values of its placeholders. The
 * EXISTS select has validated its own expressions as they were added.
 * <p>
 * Created only by its group.
 *
 * @author Miquel Sas
 */
class QCondition {

	/**
	 * The logical operator, or null for the first condition of the group.
	 */
	private final Logical logOp;
	/**
	 * The validated expression, or null for an EXISTS condition.
	 */
	private final Analysis analysis;
	/**
	 * The EXISTS select, or null for an expression.
	 */
	private final QSelect exists;

	/**
	 * Constructor.
	 *
	 * @param logOp    The logical operator, or null for the first condition of
	 *                 the group.
	 * @param expr     The expression.
	 * @param values   The values of the placeholders, in order.
	 * @param tables   The tables the expression can use, by alias.
	 * @param composed True if the expression was composed by the select from a
	 *                 comparison or an IN, whose root must then be that
	 *                 comparison or IN.
	 * @throws NullPointerException                 If the expression is null.
	 * @throws IllegalArgumentException             If a value is null.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	QCondition(Logical logOp, String expr, List<Value> values, Map<String, Table> tables, boolean composed) {
		Objects.requireNonNull(expr, "Expression can not be null");
		for (Value value : values) {
			if (value == null || value.isNull()) {
				throw new IllegalArgumentException("A null value has no literal, use IS NULL");
			}
		}
		this.logOp = logOp;
		this.exists = null;
		this.analysis = Analysis.analyze(expr, Clause.WHERE, tables, values, List.of());
		if (composed) {
			Node root = analysis.getRoot();
			boolean single = switch (root.getKind()) {
				case BINARY:
					yield root.getOperator().isComparison();
				case IN:
					yield true;
				case COLUMN:
				case LITERAL:
				case PARAMETER:
				case STAR:
				case CALL:
				case UNARY:
				case AND:
				case OR:
				case NOT:
				case IS_NULL:
				case BETWEEN:
				case LIKE:
				case CASE:
				case GROUP:
					yield false;
			};
			if (!single) {
				StringBuilder msg = new StringBuilder();
				msg.append("Single comparison expected, ");
				msg.append("an operand has a condition operator not in parentheses");
				throw new SyntaxException(msg.toString(), expr, root.getPosition());
			}
		}
	}

	/**
	 * Constructor of an EXISTS condition.
	 *
	 * @param logOp  The logical operator, or null for the first condition of the
	 *               group.
	 * @param exists The EXISTS select, already validated.
	 */
	QCondition(Logical logOp, QSelect exists) {
		this.logOp = logOp;
		this.analysis = null;
		this.exists = exists;
	}

	/**
	 * Returns the condition preceded by its logical operator, if any. An EXISTS
	 * select is written on its own lines, indented one tab.
	 *
	 * @param db   The database engine.
	 * @param wrap True to write an expression whose root is AND or OR in
	 *             parentheses, because it is not alone in the clause. It is
	 *             always written in parentheses after NOT.
	 * @return The SQL.
	 */
	String toSQL(DBEngine db, boolean wrap) {
		StringBuilder sql = new StringBuilder();
		if (logOp != null) {
			sql.append(logOp.getOperator());
			sql.append(" ");
		}
		if (exists != null) {
			sql.append("EXISTS (\n");
			for (String line : exists.toString().split("\n")) {
				sql.append("\t");
				sql.append(line);
				sql.append("\n");
			}
			sql.append(")");
			return sql.toString();
		}
		Node.Kind kind = analysis.getRoot().getKind();
		boolean parens = (wrap || logOp == Logical.NOT) && (kind == Node.Kind.AND || kind == Node.Kind.OR);
		if (parens) {
			sql.append("(");
		}
		sql.append(analysis.toSQL(db));
		if (parens) {
			sql.append(")");
		}
		return sql.toString();
	}

	/**
	 * Returns the condition as written, preceded by its logical operator, if
	 * any. The SQL of the database is shown by the toString() of the select; an
	 * EXISTS select is shown with its own SQL.
	 *
	 * @return The condition as written.
	 */
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		if (logOp != null) {
			b.append(logOp.getOperator());
			b.append(" ");
		}
		if (exists != null) {
			b.append("EXISTS (");
			b.append(exists);
			b.append(")");
		} else {
			b.append(analysis.getExpression());
		}
		return b.toString();
	}
}
