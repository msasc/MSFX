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

package msfx.lib.db.query.sa;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import msfx.lib.md.Column;
import msfx.lib.md.Table;
import msfx.lib.md.Value;

/**
 * Checks the tree of an expression against the tables in scope and the rules of
 * its clause, and resolves its columns.
 * <ul>
 * <li>A table alias must be in scope, and a column must belong to its table.
 * Both are compared ignoring case. A column without alias is accepted only
 * when a single table is in scope.</li>
 * <li>SELECT: one aggregate at most, being the whole expression.</li>
 * <li>ON and WHERE: no aggregates.</li>
 * <li>HAVING: aggregates not inside the arguments of a function, each one
 * being an aggregate of the select list, and no column outside them.</li>
 * <li>Aggregates can not be nested.</li>
 * <li>IN accepts 1000 values at most, the limit of Oracle.</li>
 * <li>CASE can be nested 10 levels at most, the limit of SQL Server.</li>
 * <li>Each placeholder must have a value, and each value a placeholder. ON
 * accepts no placeholders.</li>
 * </ul>
 *
 * @author Miquel Sas
 */
public class Validator {

	/**
	 * Maximum number of values of an IN list.
	 */
	public static final int MAX_IN_VALUES = 1000;
	/**
	 * Maximum nesting of CASE expressions.
	 */
	public static final int MAX_CASE_NESTING = 10;

	/**
	 * The expression as written, for the error messages.
	 */
	private final String expression;
	/**
	 * The root of the tree.
	 */
	private final Node root;
	/**
	 * The clause.
	 */
	private final Clause clause;
	/**
	 * The tables in scope, by alias.
	 */
	private final Map<String, Table> tables;
	/**
	 * The values of the placeholders.
	 */
	private final List<Value> values;
	/**
	 * The validated expressions of the select list, for HAVING.
	 */
	private final List<Analysis> selectList;
	/**
	 * The alias of each column node, filled while validating.
	 */
	private final IdentityHashMap<Node, String> aliases = new IdentityHashMap<>();
	/**
	 * The column of each column node, filled while validating.
	 */
	private final IdentityHashMap<Node, Column> columns = new IdentityHashMap<>();
	/**
	 * The number of placeholders found.
	 */
	private int parameters;
	/**
	 * The first aggregate found, to locate the error when it is not the whole
	 * expression of a SELECT.
	 */
	private Node firstAggregate;

	/**
	 * Constructor.
	 *
	 * @param expression The expression as written.
	 * @param root       The root of its tree, from the {@link Parser}.
	 * @param clause     The clause.
	 * @param tables     The tables in scope, by alias.
	 * @param values     The values of the placeholders, empty if none.
	 * @param selectList The validated expressions of the select list, used only
	 *                   by HAVING, empty otherwise.
	 * @throws NullPointerException If any argument is null.
	 */
	public Validator(
			String expression,
			Node root,
			Clause clause,
			Map<String, Table> tables,
			List<Value> values,
			List<Analysis> selectList) {
		this.expression = Objects.requireNonNull(expression, "Expression can not be null");
		this.root = Objects.requireNonNull(root, "Root can not be null");
		this.clause = Objects.requireNonNull(clause, "Clause can not be null");
		this.tables = Objects.requireNonNull(tables, "Tables can not be null");
		this.values = Objects.requireNonNull(values, "Values can not be null");
		this.selectList = Objects.requireNonNull(selectList, "Select list can not be null");
	}

	/**
	 * Validates the expression.
	 *
	 * @return The analysis, with the columns resolved.
	 * @throws SyntaxException If a rule is broken, at the position of the node
	 *                         that breaks it.
	 */
	public Analysis validate() {
		aliases.clear();
		columns.clear();
		parameters = 0;
		firstAggregate = null;

		int aggregates = visit(root, 0, false, false);

		/* SELECT: the aggregate must be the whole expression. */
		if (clause == Clause.SELECT && aggregates > 0) {
			Node node = root;
			while (node.getKind() == Node.Kind.GROUP) {
				node = node.getChildren().get(0);
			}
			if (node != firstAggregate) {
				throw error("An aggregate must be the whole expression in SELECT", firstAggregate);
			}
		}

		/* Values without placeholder. */
		if (values.size() > parameters) {
			StringBuilder msg = new StringBuilder();
			msg.append(values.size());
			msg.append(" values given for ");
			msg.append(parameters);
			msg.append(" placeholders");
			throw new SyntaxException(msg.toString(), expression, expression.length());
		}

		return new Analysis(expression, clause, root, values, aliases, columns);
	}

	/**
	 * Validates a node and its children.
	 *
	 * @param node        The node.
	 * @param caseDepth   The number of CASE expressions that contain the node.
	 * @param inAggregate Whether the node is inside the arguments of an
	 *                    aggregate.
	 * @param inFunction  Whether the node is inside the arguments of a function
	 *                    that is not an aggregate.
	 * @return The number of aggregates in the node and its children.
	 */
	private int visit(Node node, int caseDepth, boolean inAggregate, boolean inFunction) {
		int aggregates = switch (node.getKind()) {
			case COLUMN:
				resolve(node);
				if (clause == Clause.HAVING && !inAggregate) {
					throw error("Columns are only accepted inside aggregates in HAVING, move the condition to WHERE", node);
				}
				yield 0;
			case LITERAL:
			case STAR:
				yield 0;
			case PARAMETER:
				if (clause == Clause.ON) {
					throw error("Placeholders are not accepted in ON", node);
				}
				if (node.getIndex() >= values.size()) {
					throw error("No value for this placeholder", node);
				}
				parameters++;
				yield 0;
			case CALL:
				if (node.getFunction().isAggregate()) {
					checkAggregate(node, inAggregate, inFunction);
					visitChildren(node, caseDepth, true, inFunction);
					if (clause == Clause.HAVING) {
						checkInSelectList(node);
					}
					yield 1;
				}
				yield visitChildren(node, caseDepth, inAggregate, true);
			case IN:
				if (node.getChildren().size() - 1 > MAX_IN_VALUES) {
					StringBuilder msg = new StringBuilder();
					msg.append("IN accepts ");
					msg.append(MAX_IN_VALUES);
					msg.append(" values at most");
					throw error(msg.toString(), node.getChildren().get(MAX_IN_VALUES + 1));
				}
				yield visitChildren(node, caseDepth, inAggregate, inFunction);
			case CASE:
				if (caseDepth + 1 > MAX_CASE_NESTING) {
					StringBuilder msg = new StringBuilder();
					msg.append("CASE can be nested ");
					msg.append(MAX_CASE_NESTING);
					msg.append(" levels at most");
					throw error(msg.toString(), node);
				}
				yield visitChildren(node, caseDepth + 1, inAggregate, inFunction);
			case UNARY:
			case BINARY:
			case AND:
			case OR:
			case NOT:
			case IS_NULL:
			case BETWEEN:
			case LIKE:
			case GROUP:
				yield visitChildren(node, caseDepth, inAggregate, inFunction);
		};
		return aggregates;
	}

	/**
	 * Validates the children of a node.
	 *
	 * @param node        The node.
	 * @param caseDepth   The CASE nesting of the children.
	 * @param inAggregate Whether the children are inside an aggregate.
	 * @param inFunction  Whether the children are inside a function.
	 * @return The number of aggregates in the children.
	 */
	private int visitChildren(Node node, int caseDepth, boolean inAggregate, boolean inFunction) {
		int aggregates = 0;
		for (Node child : node.getChildren()) {
			aggregates += visit(child, caseDepth, inAggregate, inFunction);
		}
		return aggregates;
	}

	/**
	 * Checks that an aggregate is accepted where it is.
	 *
	 * @param node        The aggregate call.
	 * @param inAggregate Whether it is inside another aggregate.
	 * @param inFunction  Whether it is inside a function.
	 */
	private void checkAggregate(Node node, boolean inAggregate, boolean inFunction) {
		if (inAggregate) {
			throw error("Aggregates can not be nested", node);
		}
		if (firstAggregate == null) {
			firstAggregate = node;
		}
		boolean accepted = switch (clause) {
			case SELECT:
			case HAVING:
				yield true;
			case ON:
			case WHERE:
				yield false;
		};
		if (!accepted) {
			StringBuilder msg = new StringBuilder();
			msg.append("Aggregates are not accepted in ");
			msg.append(clause.name());
			throw error(msg.toString(), node);
		}
		if (clause == Clause.HAVING && inFunction) {
			throw error("Aggregates can not be inside a function in HAVING", node);
		}
	}

	/**
	 * Checks that an aggregate of the HAVING clause is one of the aggregates of
	 * the select list.
	 *
	 * @param node The aggregate call, with its columns already resolved.
	 */
	private void checkInSelectList(Node node) {
		for (Analysis select : selectList) {
			Node aggregate = select.getAggregate();
			if (aggregate != null && same(node, aggregate, select)) {
				return;
			}
		}
		throw error("Aggregate not found in the select list", node);
	}

	/**
	 * Returns whether a node of this expression and a node of another analysis
	 * are the same, ignoring positions and parentheses, and comparing columns by
	 * what they resolve to.
	 *
	 * @param node  The node of this expression.
	 * @param other The node of the other analysis.
	 * @param from  The other analysis.
	 * @return A boolean.
	 */
	private boolean same(Node node, Node other, Analysis from) {
		while (node.getKind() == Node.Kind.GROUP) {
			node = node.getChildren().get(0);
		}
		while (other.getKind() == Node.Kind.GROUP) {
			other = other.getChildren().get(0);
		}
		if (node.getKind() != other.getKind()
				|| node.getOperator() != other.getOperator()
				|| node.getFunction() != other.getFunction()
				|| node.isDistinct() != other.isDistinct()
				|| node.isNegated() != other.isNegated()
				|| node.getChildren().size() != other.getChildren().size()) {
			return false;
		}
		boolean same = switch (node.getKind()) {
			case COLUMN:
				yield columns.get(node) == from.getColumn(other)
						&& aliases.get(node).equalsIgnoreCase(from.getAlias(other));
			case LITERAL:
				yield node.getValue().equals(other.getValue());
			case PARAMETER:
				yield values.get(node.getIndex()).equals(from.getValues().get(other.getIndex()));
			case STAR:
			case CALL:
			case UNARY:
			case BINARY:
			case AND:
			case OR:
			case NOT:
			case IS_NULL:
			case IN:
			case BETWEEN:
			case LIKE:
			case CASE:
			case GROUP:
				yield true;
		};
		for (int i = 0; same && i < node.getChildren().size(); i++) {
			same = same(node.getChildren().get(i), other.getChildren().get(i), from);
		}
		return same;
	}

	/**
	 * Resolves the alias and the column of a column node.
	 *
	 * @param node The column node.
	 */
	private void resolve(Node node) {
		String alias = null;
		Table table = null;
		if (node.getAlias() == null) {
			if (tables.size() != 1) {
				StringBuilder msg = new StringBuilder();
				msg.append("Column ");
				msg.append(node.getName());
				msg.append(" needs a table alias");
				throw error(msg.toString(), node);
			}
			Map.Entry<String, Table> entry = tables.entrySet().iterator().next();
			alias = entry.getKey();
			table = entry.getValue();
		} else {
			for (Map.Entry<String, Table> entry : tables.entrySet()) {
				if (entry.getKey().equalsIgnoreCase(node.getAlias())) {
					alias = entry.getKey();
					table = entry.getValue();
					break;
				}
			}
			if (table == null) {
				StringBuilder msg = new StringBuilder();
				msg.append("Unknown table alias ");
				msg.append(node.getAlias());
				throw error(msg.toString(), node);
			}
		}
		Column column = table.columns().getIgnoreCase(node.getName());
		if (column == null) {
			StringBuilder msg = new StringBuilder();
			msg.append("Column ");
			msg.append(node.getName());
			msg.append(" not found in table ");
			msg.append(table.getName());
			throw error(msg.toString(), node);
		}
		aliases.put(node, alias);
		columns.put(node, column);
	}

	/**
	 * Returns an error at the position of a node.
	 *
	 * @param description The description.
	 * @param node        The node.
	 * @return The exception.
	 */
	private SyntaxException error(String description, Node node) {
		return new SyntaxException(description, expression, node.getPosition());
	}
}
