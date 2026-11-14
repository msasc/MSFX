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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import msfx.lib.db.DBEngine;

/**
 * ORDER BY part of a select: a list of segments, each one the alias of a
 * column of the select list, optionally followed by ASC or DESC, as in
 * <code>"NMONTH DESC"</code>. Segments are validated when the part is created.
 * <p>
 * Created only by its statement.
 *
 * @author Miquel Sas
 */
public class QOrderBy extends QBase {

	/**
	 * The columns ordered by, in order.
	 */
	private final List<QExpr> columns = new ArrayList<>();
	/**
	 * Whether each column is in descending order.
	 */
	private final List<Boolean> descending = new ArrayList<>();

	/**
	 * Constructor.
	 *
	 * @param db       The database engine.
	 * @param select   The columns of the select list, by alias in upper case.
	 * @param segments The segments.
	 * @throws NullPointerException     If the segments or one of them is null.
	 * @throws IllegalArgumentException If there are no segments, or a segment
	 *                                  is blank, names an unknown alias, has a
	 *                                  word other than ASC or DESC after the
	 *                                  alias, or repeats a column.
	 */
	QOrderBy(DBEngine db, Map<String, QExpr> select, String... segments) {
		super(db);
		Objects.requireNonNull(segments, "Segments can not be null");
		if (segments.length == 0) {
			throw new IllegalArgumentException("ORDER BY requires at least one column");
		}
		for (String segment : segments) {
			Objects.requireNonNull(segment, "Segment can not be null");
			String[] words = segment.trim().split("\\s+");
			if (words[0].isEmpty()) {
				throw new IllegalArgumentException("ORDER BY segment can not be blank");
			}
			String alias = words[0];
			QExpr column = select.get(alias.toUpperCase(Locale.ROOT));
			if (column == null) {
				StringBuilder msg = new StringBuilder();
				msg.append("Unknown column alias ");
				msg.append(alias);
				msg.append(" in ORDER BY");
				throw new IllegalArgumentException(msg.toString());
			}
			boolean desc = false;
			if (words.length == 2 && words[1].equalsIgnoreCase("DESC")) {
				desc = true;
			} else if (words.length > 2 || (words.length == 2 && !words[1].equalsIgnoreCase("ASC"))) {
				StringBuilder msg = new StringBuilder();
				msg.append("ASC or DESC expected after ");
				msg.append(alias);
				msg.append(" in ORDER BY segment '");
				msg.append(segment);
				msg.append("'");
				throw new IllegalArgumentException(msg.toString());
			}
			if (columns.contains(column)) {
				StringBuilder msg = new StringBuilder();
				msg.append("Column ");
				msg.append(alias);
				msg.append(" is already in ORDER BY");
				throw new IllegalArgumentException(msg.toString());
			}
			columns.add(column);
			descending.add(desc);
		}
	}

	/**
	 * Returns the clause, with the registered aliases quoted, and DESC where
	 * required; ASC, the default, is not written.
	 *
	 * @return The SQL.
	 */
	String toSQL() {
		DBEngine db = getDBEngine();
		StringBuilder sql = new StringBuilder();
		sql.append("ORDER BY ");
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append(columns.get(i).toAliasSQL(db));
			if (descending.get(i)) {
				sql.append(" DESC");
			}
		}
		return sql.toString();
	}

	/**
	 * Returns the clause, as {@link #toSQL()} writes it.
	 *
	 * @return The SQL.
	 */
	@Override
	public String toString() {
		return toSQL();
	}
}
