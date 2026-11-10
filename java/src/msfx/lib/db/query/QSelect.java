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

import msfx.lib.db.DBEngine;
import msfx.lib.md.Table;

/**
 * SELECT statement. The main table is set when created, so that the columns
 * referenced later can be validated, and the other tables are added by joins.
 * Its parts share the engine and the tables.
 *
 * @author Miquel Sas
 */
public class QSelect extends QBase {

	/**
	 * Tables involved, the main table first.
	 */
	private final QFrom from;
	/**
	 * Joins, in the order they were added.
	 */
	private final List<QJoin> joins = new ArrayList<>();
	/**
	 * WHERE conditions.
	 */
	private final QWhere where;
	/**
	 * GROUP BY part.
	 */
	private final QGroupBy groupBy;
	/**
	 * HAVING conditions.
	 */
	private final QWhere having;
	/**
	 * ORDER BY part.
	 */
	private final QOrderBy orderBy;

	/**
	 * Constructor, with the name of the main table as alias.
	 *
	 * @param db    The database engine.
	 * @param table The main table.
	 * @throws NullPointerException If the engine or the table is null.
	 */
	public QSelect(DBEngine db, Table table) {
		this(db, table, null);
	}

	/**
	 * Constructor.
	 *
	 * @param db    The database engine.
	 * @param table The main table.
	 * @param alias The alias, or null to use the name of the table.
	 * @throws NullPointerException If the engine or the table is null.
	 */
	public QSelect(DBEngine db, Table table, String alias) {
		super(db);
		from = new QFrom(db, table, alias);
		where = new QWhere(from);
		groupBy = new QGroupBy(from);
		having = new QWhere(from);
		orderBy = new QOrderBy(from);
	}

	/**
	 * @return The tables involved, the main table first.
	 */
	public QFrom getFrom() {
		return from;
	}

	/**
	 * Adds a join, whose ON conditions can reference the tables added so far.
	 *
	 * @param table The joined table.
	 * @param alias The alias, or null to use the name of the table.
	 * @return The join, to set its ON conditions.
	 * @throws NullPointerException     If the table is null.
	 * @throws IllegalArgumentException If the alias is duplicated.
	 */
	public QJoin addJoin(Table table, String alias) {
		QJoin join = new QJoin(from, table, alias);
		joins.add(join);
		return join;
	}

	/**
	 * @return The joins, in the order they were added.
	 */
	public List<QJoin> getJoins() {
		return List.copyOf(joins);
	}

	/**
	 * @return The WHERE conditions.
	 */
	public QWhere getWhere() {
		return where;
	}

	/**
	 * @return The GROUP BY part.
	 */
	public QGroupBy getGroupBy() {
		return groupBy;
	}

	/**
	 * @return The HAVING conditions.
	 */
	public QWhere getHaving() {
		return having;
	}

	/**
	 * @return The ORDER BY part.
	 */
	public QOrderBy getOrderBy() {
		return orderBy;
	}
}
