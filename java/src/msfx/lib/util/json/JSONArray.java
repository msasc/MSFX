/*
 * Copyright (c) 2026 Miquel Sas.
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

package msfx.lib.util.json;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * JSON Array.
 *
 * @author Miquel Sas
 */
public class JSONArray implements Iterable<JSONEntry> {

	/**
	 * List of entries.
	 */
	private final List<JSONEntry> entries = new ArrayList<>();

	/**
	 * @param arr The string JSON array to parse.
	 * @return The parsed JSON array.
	 * @throws IllegalArgumentException If the string is not a valid JSON array.
	 */
	public static JSONArray parse(String arr) {
		try {
			return parse(new StringReader(arr));
		} catch (IOException exc) {
			throw new IllegalArgumentException("Invalid JSON array string: " + exc.getMessage(), exc);
		}
	}
	/**
	 * @param reader The reader to parse.
	 * @return The parsed JSON array.
	 * @throws IOException If an IO or format error occurs.
	 */
	public static JSONArray parse(Reader reader) throws IOException {
		return new JSONParser().parseArray(reader);
	}

	/**
	 * Constructor.
	 */
	public JSONArray() { }

	/**
	 * @param entry The entry to add.
	 * @throws IllegalArgumentException If the entry is null.
	 */
	public void add(JSONEntry entry) {
		if (entry == null) throw new IllegalArgumentException("Entry can not be null");
		entries.add(entry);
	}
	/**
	 * @param value A {@link JSONObject}.
	 */
	public void add(JSONObject value) { add(new JSONEntry(value)); }
	/**
	 * @param value A {@link JSONArray}.
	 */
	public void add(JSONArray value) { add(new JSONEntry(value)); }
	/**
	 * @param value A {@link String}.
	 */
	public void add(String value) { add(new JSONEntry(value)); }
	/**
	 * @param value A {@link Number}.
	 */
	public void add(Number value) { add(new JSONEntry(value)); }
	/**
	 * @param value A {@link Number}.
	 * @param scale The scale or number of decimals.
	 */
	public void add(Number value, int scale) { add(new JSONEntry(value, scale)); }
	/**
	 * @param value A boolean.
	 */
	public void add(boolean value) { add(new JSONEntry(value)); }
	/**
	 * @param value A byte[].
	 */
	public void add(byte[] value) { add(new JSONEntry(value)); }
	/**
	 * @param value A {@link LocalDate}.
	 */
	public void add(LocalDate value) { add(new JSONEntry(value)); }
	/**
	 * @param value A {@link LocalTime}.
	 */
	public void add(LocalTime value) { add(new JSONEntry(value)); }
	/**
	 * @param value A {@link LocalDateTime}.
	 */
	public void add(LocalDateTime value) { add(new JSONEntry(value)); }

	/**
	 * @param index The index where to insert.
	 * @param entry The entry to insert.
	 * @throws IllegalArgumentException If the entry is null.
	 */
	public void add(int index, JSONEntry entry) {
		if (entry == null) throw new IllegalArgumentException("Entry can not be null");
		entries.add(index, entry);
	}
	/**
	 * @param index The index where to insert.
	 * @param value A {@link JSONObject}.
	 */
	public void add(int index, JSONObject value) { add(index, new JSONEntry(value)); }
	/**
	 * @param index The index where to insert.
	 * @param value A {@link JSONArray}.
	 */
	public void add(int index, JSONArray value) { add(index, new JSONEntry(value)); }
	/**
	 * @param index The index where to insert.
	 * @param value A {@link String}.
	 */
	public void add(int index, String value) { add(index, new JSONEntry(value)); }
	/**
	 * @param index The index where to insert.
	 * @param value A {@link Number}.
	 */
	public void add(int index, Number value) { add(index, new JSONEntry(value)); }
	/**
	 * @param index The index where to insert.
	 * @param value A {@link Number}.
	 * @param scale The scale or number of decimals.
	 */
	public void add(int index, Number value, int scale) { add(index, new JSONEntry(value, scale)); }
	/**
	 * @param index The index where to insert.
	 * @param value A boolean.
	 */
	public void add(int index, boolean value) { add(index, new JSONEntry(value)); }
	/**
	 * @param index The index where to insert.
	 * @param value A byte[].
	 */
	public void add(int index, byte[] value) { add(index, new JSONEntry(value)); }
	/**
	 * @param index The index where to insert.
	 * @param value A {@link LocalDate}.
	 */
	public void add(int index, LocalDate value) { add(index, new JSONEntry(value)); }
	/**
	 * @param index The index where to insert.
	 * @param value A {@link LocalTime}.
	 */
	public void add(int index, LocalTime value) { add(index, new JSONEntry(value)); }
	/**
	 * @param index The index where to insert.
	 * @param value A {@link LocalDateTime}.
	 */
	public void add(int index, LocalDateTime value) { add(index, new JSONEntry(value)); }

	/**
	 * @param index The index of the entry to replace.
	 * @param entry The new entry.
	 * @throws IllegalArgumentException If the entry is null.
	 */
	public void set(int index, JSONEntry entry) {
		if (entry == null) throw new IllegalArgumentException("Entry can not be null");
		entries.set(index, entry);
	}
	/**
	 * @param index The index of the entry to replace.
	 * @param value A {@link JSONObject}.
	 */
	public void set(int index, JSONObject value) { set(index, new JSONEntry(value)); }
	/**
	 * @param index The index of the entry to replace.
	 * @param value A {@link JSONArray}.
	 */
	public void set(int index, JSONArray value) { set(index, new JSONEntry(value)); }
	/**
	 * @param index The index of the entry to replace.
	 * @param value A {@link String}.
	 */
	public void set(int index, String value) { set(index, new JSONEntry(value)); }
	/**
	 * @param index The index of the entry to replace.
	 * @param value A {@link Number}.
	 */
	public void set(int index, Number value) { set(index, new JSONEntry(value)); }
	/**
	 * @param index The index of the entry to replace.
	 * @param value A {@link Number}.
	 * @param scale The scale or number of decimals.
	 */
	public void set(int index, Number value, int scale) { set(index, new JSONEntry(value, scale)); }
	/**
	 * @param index The index of the entry to replace.
	 * @param value A boolean.
	 */
	public void set(int index, boolean value) { set(index, new JSONEntry(value)); }
	/**
	 * @param index The index of the entry to replace.
	 * @param value A byte[].
	 */
	public void set(int index, byte[] value) { set(index, new JSONEntry(value)); }
	/**
	 * @param index The index of the entry to replace.
	 * @param value A {@link LocalDate}.
	 */
	public void set(int index, LocalDate value) { set(index, new JSONEntry(value)); }
	/**
	 * @param index The index of the entry to replace.
	 * @param value A {@link LocalTime}.
	 */
	public void set(int index, LocalTime value) { set(index, new JSONEntry(value)); }
	/**
	 * @param index The index of the entry to replace.
	 * @param value A {@link LocalDateTime}.
	 */
	public void set(int index, LocalDateTime value) { set(index, new JSONEntry(value)); }

	/**
	 * @param index The index.
	 * @return The entry at index.
	 */
	public JSONEntry get(int index) { return entries.get(index); }
	/**
	 * @param index The index.
	 * @return The removed entry.
	 */
	public JSONEntry remove(int index) { return entries.remove(index); }
	/**
	 * @return The number of entries.
	 */
	public int size() { return entries.size(); }

	/**
	 * @return An iterator over the entries.
	 */
	@Override
	public Iterator<JSONEntry> iterator() {
		return entries.iterator();
	}

	/**
	 * Check equals. Uses the string representation.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof JSONArray arr) {
			return toString().equals(arr.toString());
		}
		return false;
	}
	/**
	 * Hash code. Uses the string representation.
	 */
	@Override
	public int hashCode() { return toString().hashCode(); }

	/**
	 * Returns the string representation.
	 */
	@Override
	public String toString() {
		return toString(false);
	}
	/**
	 * @param readable A boolean indicating that the output should be formatted in a more readable
	 *                 form.
	 * @return The string representation.
	 */
	public String toString(boolean readable) {
		JSONWriter w = new JSONWriter(readable);
		return w.toString(this);
	}
}
