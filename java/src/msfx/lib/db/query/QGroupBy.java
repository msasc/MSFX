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

import java.util.Collection;
import java.util.Map;

import msfx.lib.db.DBEngine;
import msfx.lib.md.Table;

/**
 * GROUP BY part of a select, generated from its select list: the expressions
 * that are not aggregates. Expressions that use no column of the select, like
 * constants or, in an EXISTS select, outer columns only, are left out, since
 * databases reject them in GROUP BY.
 * <p>
 * It is written when the select is written, so it includes the columns added
 * after it was created.
 * <p>
 * Created only by its statement.
 *
 * @author Miquel Sas
 */
public class QGroupBy extends QBase {

	/**
	 * The columns of the select list, shared with the select.
	 */
	private final Collection<QExpr> columns;
	/**
	 * The tables of the select, by alias, shared with it, without the tables of
	 * the outer selects.
	 */
	private final Map<String, Table> tables;

	/**
	 * Constructor.
	 *
	 * @param db      The database engine.
	 * @param columns The columns of the select list.
	 * @param tables  The tables of the select, by alias.
	 */
	QGroupBy(DBEngine db, Collection<QExpr> columns, Map<String, Table> tables) {
		super(db);
		this.columns = columns;
		this.tables = tables;
	}

	/**
	 * Returns the clause.
	 *
	 * @return The SQL, empty if no expression has to be grouped.
	 */
	String toSQL() {
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		for (QExpr column : columns) {
			if (!column.isGrouped(tables)) {
				continue;
			}
			sql.append(sql.isEmpty() ? "GROUP BY " : ", ");
			sql.append(column.toExprSQL(db));
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
