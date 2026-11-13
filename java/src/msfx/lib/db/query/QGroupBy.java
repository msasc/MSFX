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

/**
 * GROUP BY part of a select.
 * <p>
 * Created only by its statement.
 *
 * @author Miquel Sas
 */
public class QGroupBy extends QBase {

	/**
	 * Tables of the statement, to validate column references.
	 */
	private final QFrom from;

	/**
	 * Constructor.
	 *
	 * @param from The tables of the statement.
	 */
	QGroupBy(QFrom from) {
		super(from.getDBEngine());
		this.from = from;
	}

	/**
	 * @return The tables of the statement.
	 */
	QFrom getFrom() {
		return from;
	}

	/**
	 * Returns the SQL, not generated yet.
	 *
	 * @return The SQL.
	 */
	@Override
	public String toString() {
		throw new UnsupportedOperationException("Not implemented yet");
	}
}
