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
