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
import msfx.lib.md.Table;

/**
 * Join of a table to the statement, LEFT or INNER, with the ON condition that
 * links it to the tables added before it.
 * <p>
 * The condition is validated when the join is created, against the tables in
 * scope at that moment: those of the FROM clause and of the previous joins, and
 * the joined table itself. It accepts no placeholders and no aggregates.
 * <p>
 * Created only by its statement.
 *
 * @author Miquel Sas
 */
public class QJoin extends QBase {

	/**
	 * Types of join. OUTER and CROSS joins are not supported.
	 */
	public enum Type {
		/** LEFT JOIN: all the rows of the previous tables, with or without match. */
		LEFT,
		/** INNER JOIN: only the rows with a match. */
		INNER
	}

	/**
	 * The type of join.
	 */
	private final Type type;
	/**
	 * The joined table.
	 */
	private final Table table;
	/**
	 * The alias of the joined table.
	 */
	private final String alias;
	/**
	 * The validated ON condition.
	 */
	private final Analysis link;

	/**
	 * Constructor.
	 *
	 * @param db       The database engine.
	 * @param type     The type of join.
	 * @param table    The joined table.
	 * @param alias    The alias of the joined table.
	 * @param linkExpr The ON condition.
	 * @param scope    The tables the condition can use, by alias: those of the
	 *                 FROM clause, of the previous joins, and the joined table.
	 * @throws NullPointerException                      If any argument is null.
	 * @throws IllegalArgumentException                  If the alias is blank.
	 * @throws msfx.lib.db.query.sa.SyntaxException If the condition is wrong.
	 */
	QJoin(DBEngine db, Type type, Table table, String alias, String linkExpr, Map<String, Table> scope) {
		super(db);
		this.type = Objects.requireNonNull(type, "Type can not be null");
		this.table = Objects.requireNonNull(table, "Table can not be null");
		this.alias = Objects.requireNonNull(alias, "Alias can not be null");
		if (alias.isBlank()) {
			throw new IllegalArgumentException("Alias can not be blank");
		}
		Objects.requireNonNull(linkExpr, "Link expression can not be null");
		this.link = Analysis.analyze(linkExpr, Clause.ON, scope, List.of(), List.of());
	}

	/**
	 * Returns the join: LEFT JOIN table alias ON condition, or INNER JOIN. The
	 * alias is written without AS, which Oracle does not accept for tables.
	 *
	 * @return The join.
	 */
	String toSQL() {
		DBEngine db = getDBEngine();
		String join = switch (type) {
			case LEFT:
				yield "LEFT JOIN ";
			case INNER:
				yield "INNER JOIN ";
		};
		StringBuilder sql = new StringBuilder();
		sql.append(join);
		sql.append(getQualifiedName(table));
		sql.append(" ");
		sql.append(db.quote(alias));
		sql.append(" ON ");
		sql.append(link.toSQL(db));
		return sql.toString();
	}

	/**
	 * Returns the join, as {@link #toSQL()} writes it.
	 *
	 * @return The join.
	 */
	@Override
	public String toString() {
		return toSQL();
	}
}
