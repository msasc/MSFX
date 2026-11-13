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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import msfx.lib.db.DBEngine;
import msfx.lib.md.Domain;
import msfx.lib.md.Table;

/**
 * SELECT statement.
 * <p>
 * Every table gets an alias, required and unique ignoring case, that is the
 * name the expressions of the statement use to refer to its columns.
 *
 * @author Miquel Sas
 */
public class QSelect extends QBase {

	/**
	 * Tables used, by alias. Aliases are unique ignoring case, and keep the case
	 * of their first spelling. Filled by the FROM clause, the joins and the
	 * EXISTS clauses of the WHERE part.
	 */
	private final Map<String, Table> tables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	/**
	 * FROM clause, with the master table and its alias, or null while it is not
	 * set.
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
	 * Constructor.
	 *
	 * @param db The database engine.
	 * @throws NullPointerException If the engine is null.
	 */
	public QSelect(DBEngine db) {
		super(db);
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
	 *                                              already used.
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
	 * @param domain The domain, or null.
	 * @throws NullPointerException                 If the expression or the alias
	 *                                              is null.
	 * @throws IllegalArgumentException             If the alias is blank or
	 *                                              already used.
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
		columns.put(key, new QExpr(expr, alias, domain, tables));
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
		Map<String, Table> scope = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		scope.putAll(tables);
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
	 * @throws IllegalArgumentException If the alias is blank, or is already used,
	 *                                  compared ignoring case.
	 */
	private void checkTable(Table table, String alias) {
		Objects.requireNonNull(table, "Table can not be null");
		Objects.requireNonNull(alias, "Alias can not be null");
		if (alias.isBlank()) {
			throw new IllegalArgumentException("Alias can not be blank");
		}
		if (tables.containsKey(alias)) {
			StringBuilder msg = new StringBuilder();
			msg.append("Alias ");
			msg.append(alias);
			msg.append(" is already used by table ");
			msg.append(tables.get(alias).getName());
			throw new IllegalArgumentException(msg.toString());
		}
	}

	/**
	 * Returns the statement as far as it is built, one clause per line: for now
	 * the select list, the FROM clause and the joins. The select list is written
	 * only when it has columns.
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
