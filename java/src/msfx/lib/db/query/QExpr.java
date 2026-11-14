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
import msfx.lib.md.Domain;
import msfx.lib.md.Table;

/**
 * A column of the select list: an expression, the alias of the column in the
 * result, and optionally the domain that defines its type, to read the result
 * into records.
 * <p>
 * The expression is validated when the column is created, against the tables
 * registered at that moment: it can use their columns, and have one aggregate
 * at most, being the whole expression.
 * <p>
 * Created only by its select.
 *
 * @author Miquel Sas
 */
public class QExpr {

	/**
	 * The validated expression.
	 */
	private final Analysis analysis;
	/**
	 * The alias of the column in the result.
	 */
	private final String alias;
	/**
	 * The domain that defines the type of the column, or null.
	 */
	private final Domain domain;

	/**
	 * Constructor.
	 *
	 * @param expr   The expression.
	 * @param alias  The alias of the column in the result.
	 * @param domain The domain, or null.
	 * @param tables The tables the expression can use, by alias.
	 * @throws NullPointerException                 If the expression or the alias
	 *                                              is null.
	 * @throws IllegalArgumentException             If the alias is blank.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the expression is wrong.
	 */
	QExpr(String expr, String alias, Domain domain, Map<String, Table> tables) {
		Objects.requireNonNull(expr, "Expression can not be null");
		this.alias = Objects.requireNonNull(alias, "Alias can not be null");
		if (alias.isBlank()) {
			throw new IllegalArgumentException("Alias can not be blank");
		}
		this.domain = domain;
		this.analysis = Analysis.analyze(expr, Clause.SELECT, tables, List.of(), List.of());
	}

	/**
	 * Returns the validated expression, used by the select to validate HAVING
	 * against its select list.
	 *
	 * @return The analysis.
	 */
	Analysis getAnalysis() {
		return analysis;
	}

	/**
	 * Checks whether the expression is an aggregate.
	 *
	 * @return True if the expression is an aggregate.
	 */
	boolean isAggregate() {
		return analysis.getAggregate() != null;
	}

	/**
	 * Checks whether the expression goes to the GROUP BY clause: it is not an
	 * aggregate, and uses at least one column of the given tables.
	 *
	 * @param tables The tables of the select, by alias, without the outer ones.
	 * @return True if the expression is grouped.
	 */
	boolean isGrouped(Map<String, Table> tables) {
		if (isAggregate()) {
			return false;
		}
		return usesTables(analysis.getRoot(), tables);
	}

	/**
	 * Checks whether a node or its children use a column of the given tables.
	 *
	 * @param node   The node.
	 * @param tables The tables, by alias.
	 * @return True if a column of the tables is used.
	 */
	private boolean usesTables(Node node, Map<String, Table> tables) {
		if (node.getKind() == Node.Kind.COLUMN) {
			return tables.containsKey(analysis.getAlias(node));
		}
		for (Node child : node.getChildren()) {
			if (usesTables(child, tables)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the expression, without the alias.
	 *
	 * @param db The database engine.
	 * @return The expression.
	 */
	String toExprSQL(DBEngine db) {
		return analysis.toSQL(db);
	}

	/**
	 * Returns the alias, quoted.
	 *
	 * @param db The database engine.
	 * @return The alias.
	 */
	String toAliasSQL(DBEngine db) {
		return db.quote(alias);
	}

	/**
	 * Returns the column: expression AS alias, the alias quoted.
	 *
	 * @param db The database engine.
	 * @return The column.
	 */
	String toSQL(DBEngine db) {
		StringBuilder sql = new StringBuilder();
		sql.append(analysis.toSQL(db));
		sql.append(" AS ");
		sql.append(db.quote(alias));
		return sql.toString();
	}

	/**
	 * Returns the column as written, expression AS alias. The SQL of the database
	 * is shown by the toString() of the select.
	 *
	 * @return The column as written.
	 */
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(analysis.getExpression());
		b.append(" AS ");
		b.append(alias);
		return b.toString();
	}
}
