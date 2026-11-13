/*
 * Copyright (c) 2026 Miquel Sas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package msfx.lib.md;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * List of columns accessible by alias or by index.
 * <p>
 * Each column is added under an alias, which defaults to the column name and
 * must be unique in the list. The same column may be added more than once under
 * different aliases, as in a view that repeats a column. In that case all the
 * aliases share the same column object, so changing its properties through one
 * alias changes them for all.
 * <p>
 * Lookups by alias are case-sensitive. However, an alias that differs from an
 * existing one only in upper or lower case is rejected, since many databases
 * would treat both as the same name. Indexes follow the order in which the
 * columns were added.
 * <p>
 * Columns are added in an initial step, after which the list remains stable
 * and is mostly read. Every add rebuilds the internal structures, so that
 * access by alias or by index is direct.
 *
 * @author Miquel Sas
 */
public class ColumnList implements Iterable<Column> {

	/**
	 * Called before a column is added, to configure its properties or to reject
	 * it.
	 */
	@FunctionalInterface
	public interface Interceptor {
		/**
		 * @param alias  The alias the column is about to be added under.
		 * @param column The column about to be added.
		 * @throws RuntimeException Any exception rejects the column, which is then
		 *                          not added. Use IllegalArgumentException for an
		 *                          invalid column and IllegalStateException when no
		 *                          column can be added in the current state.
		 */
		void accept(String alias, Column column);
	}

	/**
	 * Empty column array.
	 */
	private static final Column[] NO_COLUMNS = new Column[0];
	/**
	 * Empty alias array.
	 */
	private static final String[] NO_ALIASES = new String[0];

	/**
	 * Columns by alias, in the order they were added. Source of all the other
	 * structures.
	 */
	private final LinkedHashMap<String, Column> columnsByAlias = new LinkedHashMap<>();
	/**
	 * Indexes by alias. Rebuilt.
	 */
	private final Map<String, Integer> indexesByAlias = new HashMap<>();
	/**
	 * Aliases by their upper case key, to find them ignoring case. Rebuilt.
	 */
	private final Map<String, String> aliasesByKey = new HashMap<>();
	/**
	 * Columns by index. Rebuilt.
	 */
	private Column[] columnsByIndex = NO_COLUMNS;
	/**
	 * Aliases by index. Rebuilt.
	 */
	private String[] aliasesByIndex = NO_ALIASES;
	/**
	 * Unmodifiable view of the columns by index. Rebuilt.
	 */
	private List<Column> columnList = List.of();
	/**
	 * Unmodifiable view of the aliases by index. Rebuilt.
	 */
	private List<String> aliasList = List.of();
	/**
	 * Column interceptor, may be null.
	 */
	private Interceptor interceptor = null;

	/**
	 * Constructor.
	 */
	public ColumnList() {
	}

	/**
	 * @param interceptor The interceptor called before each column is added, to
	 *                    configure its properties or to reject it. May be null.
	 */
	public void setInterceptor(Interceptor interceptor) {
		this.interceptor = interceptor;
	}

	/**
	 * Adds a column using its name as alias.
	 *
	 * @param column The column to add.
	 * @throws NullPointerException     If the column or its name is null.
	 * @throws IllegalArgumentException If the name is blank, already exists as an
	 *                                  alias ignoring case, or the interceptor
	 *                                  rejects the column.
	 */
	public void add(Column column) {
		Objects.requireNonNull(column, "Column can not be null");
		add(column.getName(), column);
	}

	/**
	 * Adds a column under an alias.
	 *
	 * @param alias  The alias of the column.
	 * @param column The column to add.
	 * @throws NullPointerException     If the alias or the column is null.
	 * @throws IllegalArgumentException If the alias is blank, already exists
	 *                                  ignoring case, or the interceptor rejects
	 *                                  the column.
	 */
	public void add(String alias, Column column) {
		Objects.requireNonNull(alias, "Alias can not be null");
		Objects.requireNonNull(column, "Column can not be null");
		if (alias.isBlank()) {
			throw new IllegalArgumentException("Alias can not be blank");
		}
		String existing = aliasesByKey.get(toKey(alias));
		if (existing != null) {
			StringBuilder msg = new StringBuilder();
			if (existing.equals(alias)) {
				msg.append("Alias already exists: ");
				msg.append(alias);
			} else {
				msg.append("Alias ");
				msg.append(alias);
				msg.append(" differs only in case from existing alias ");
				msg.append(existing);
			}
			throw new IllegalArgumentException(msg.toString());
		}
		if (interceptor != null) {
			interceptor.accept(alias, column);
		}
		columnsByAlias.put(alias, column);
		rebuildInternals();
	}

	/**
	 * @param alias The alias.
	 * @return The column with the alias, or null if not found.
	 */
	public Column get(String alias) {
		return columnsByAlias.get(alias);
	}

	/**
	 * Returns the column with the alias compared ignoring case. Aliases that
	 * differ only in case are rejected when added, so the result is never
	 * ambiguous.
	 *
	 * @param alias The alias, in any case.
	 * @return The column, or null if not found.
	 */
	public Column getIgnoreCase(String alias) {
		String existing = aliasesByKey.get(toKey(alias));
		return existing == null ? null : columnsByAlias.get(existing);
	}

	/**
	 * Returns the key of an alias in the map of aliases ignoring case.
	 *
	 * @param alias The alias.
	 * @return The alias in upper case.
	 */
	private static String toKey(String alias) {
		return alias.toUpperCase(Locale.ROOT);
	}

	/**
	 * @param index The index.
	 * @return The column at the index.
	 * @throws IndexOutOfBoundsException If the index is out of range.
	 */
	public Column get(int index) {
		return columnsByIndex[index];
	}

	/**
	 * @param index The index.
	 * @return The alias of the column at the index.
	 * @throws IndexOutOfBoundsException If the index is out of range.
	 */
	public String getAlias(int index) {
		return aliasesByIndex[index];
	}

	/**
	 * @param alias The alias.
	 * @return The index of the column with the alias, or -1 if not found.
	 */
	public int indexOf(String alias) {
		Integer index = indexesByAlias.get(alias);
		return index == null ? -1 : index;
	}

	/**
	 * @param alias The alias.
	 * @return Whether the list contains a column with the alias.
	 */
	public boolean contains(String alias) {
		return columnsByAlias.containsKey(alias);
	}

	/**
	 * @return An unmodifiable list of the columns, in index order. A column added
	 *         under several aliases appears once per alias.
	 */
	public List<Column> columns() {
		return Collections.unmodifiableList(columnList);
	}

	/**
	 * @return An iterator over the aliases, in index order, that does not support
	 *         removal.
	 */
	public Iterator<String> aliases() {
		return aliasList.iterator();
	}

	/**
	 * @return An iterator over the indexes, from 0 to size - 1.
	 */
	public Iterator<Integer> indexes() {
		return IntStream.range(0, columnsByIndex.length).iterator();
	}

	/**
	 * @return The number of columns.
	 */
	public int size() {
		return columnsByIndex.length;
	}

	/**
	 * @return Whether the list has no columns.
	 */
	public boolean isEmpty() {
		return columnsByIndex.length == 0;
	}

	/**
	 * Rebuilds all the access structures from the alias map.
	 */
	private void rebuildInternals() {
		int size = columnsByAlias.size();
		columnsByIndex = new Column[size];
		aliasesByIndex = new String[size];
		indexesByAlias.clear();
		aliasesByKey.clear();
		int index = 0;
		for (Map.Entry<String, Column> entry : columnsByAlias.entrySet()) {
			columnsByIndex[index] = entry.getValue();
			aliasesByIndex[index] = entry.getKey();
			indexesByAlias.put(entry.getKey(), index);
			aliasesByKey.put(toKey(entry.getKey()), entry.getKey());
			index++;
		}
		columnList = Collections.unmodifiableList(Arrays.asList(columnsByIndex));
		aliasList = Collections.unmodifiableList(Arrays.asList(aliasesByIndex));
	}

	/**
	 * @return An iterator over the columns in index order, that does not support
	 *         removal.
	 */
	@Override
	public Iterator<Column> iterator() {
		return columnList.iterator();
	}

	/**
	 * Two lists are equal if they have the same aliases with equal columns, in
	 * the same order.
	 *
	 * @param obj The object to compare.
	 * @return Whether the object is an equal column list.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		return obj instanceof ColumnList other
				&& Arrays.equals(aliasesByIndex, other.aliasesByIndex)
				&& Arrays.equals(columnsByIndex, other.columnsByIndex);
	}

	/**
	 * @return A hash code based on the aliases and columns, in order.
	 */
	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(aliasesByIndex) + Arrays.hashCode(columnsByIndex);
	}
}
