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

import msfx.lib.db.DBEngine;
import msfx.lib.md.Column;
import msfx.lib.md.Table;
import msfx.lib.md.Value;

/**
 * A validated expression: its tree, the values of its placeholders, and the
 * table alias and the column each column node resolves to. Created by the
 * {@link Validator}, used by the writer.
 *
 * @author Miquel Sas
 */
public class Analysis {

	/**
	 * Parses and validates an expression: as a value for SELECT, as a condition
	 * for the other clauses. This is the entry point of the query classes.
	 *
	 * @param expression The expression.
	 * @param clause     The clause.
	 * @param tables     The tables in scope, by alias.
	 * @param values     The values of the placeholders, empty if none.
	 * @param selectList The validated expressions of the select list, used only
	 *                   by HAVING, empty otherwise.
	 * @return The analysis.
	 * @throws SyntaxException If the expression is wrong, at the position of the
	 *                         error.
	 */
	public static Analysis analyze(
			String expression,
			Clause clause,
			Map<String, Table> tables,
			List<Value> values,
			List<Analysis> selectList) {
		Parser parser = new Parser(expression);
		Node root = switch (clause) {
			case SELECT:
				yield parser.parseExpression();
			case ON:
			case WHERE:
			case HAVING:
				yield parser.parseCondition();
		};
		return new Validator(expression, root, clause, tables, values, selectList).validate();
	}

	/**
	 * The expression as written.
	 */
	private final String expression;
	/**
	 * The clause the expression was validated for.
	 */
	private final Clause clause;
	/**
	 * The root of the tree.
	 */
	private final Node root;
	/**
	 * The values of the placeholders, in order.
	 */
	private final List<Value> values;
	/**
	 * The registered table alias of each column node, with the case it was
	 * registered with.
	 */
	private final Map<Node, String> aliases;
	/**
	 * The column of each column node.
	 */
	private final Map<Node, Column> columns;

	/**
	 * Constructor, used by the validator.
	 *
	 * @param expression The expression.
	 * @param clause     The clause.
	 * @param root       The root of the tree.
	 * @param values     The values of the placeholders.
	 * @param aliases    The alias of each column node.
	 * @param columns    The column of each column node.
	 */
	Analysis(
			String expression,
			Clause clause,
			Node root,
			List<Value> values,
			IdentityHashMap<Node, String> aliases,
			IdentityHashMap<Node, Column> columns) {
		this.expression = expression;
		this.clause = clause;
		this.root = root;
		this.values = List.copyOf(values);
		this.aliases = aliases;
		this.columns = columns;
	}

	/**
	 * Returns the expression as written.
	 *
	 * @return The expression.
	 */
	public String getExpression() {
		return expression;
	}

	/**
	 * Returns the clause the expression was validated for.
	 *
	 * @return The clause.
	 */
	public Clause getClause() {
		return clause;
	}

	/**
	 * Returns the root of the tree.
	 *
	 * @return The root node.
	 */
	public Node getRoot() {
		return root;
	}

	/**
	 * Returns the values of the placeholders.
	 *
	 * @return The values, in the order of the placeholders.
	 */
	public List<Value> getValues() {
		return values;
	}

	/**
	 * Returns the registered table alias a column node resolves to.
	 *
	 * @param node The column node.
	 * @return The alias, with the case it was registered with, or null if the
	 *         node is not a column of this expression.
	 */
	public String getAlias(Node node) {
		return aliases.get(node);
	}

	/**
	 * Returns the column a column node resolves to.
	 *
	 * @param node The column node.
	 * @return The column, or null if the node is not a column of this
	 *         expression.
	 */
	public Column getColumn(Node node) {
		return columns.get(node);
	}

	/**
	 * Returns the expression written in the SQL of a database.
	 *
	 * @param db The database engine.
	 * @return The SQL.
	 */
	public String toSQL(DBEngine db) {
		return new Writer(db, this).write();
	}

	/**
	 * Returns the aggregate that is the whole expression, ignoring the
	 * parentheses around it.
	 *
	 * @return The aggregate call, or null if the expression is not an aggregate.
	 */
	public Node getAggregate() {
		Node node = root;
		while (node.getKind() == Node.Kind.GROUP) {
			node = node.getChildren().get(0);
		}
		if (node.getKind() == Node.Kind.CALL && node.getFunction().isAggregate()) {
			return node;
		}
		return null;
	}
}
