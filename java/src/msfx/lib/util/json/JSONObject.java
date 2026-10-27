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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON Object.
 *
 * @author Miquel Sas
 */
public class JSONObject {

	/**
	 * @param obj The string JSON object to parse.
	 * @return The parsed JSON object.
	 * @throws IllegalArgumentException If the string is not a valid JSON object.
	 */
	public static JSONObject parse(String obj) {
		try {
			StringReader reader = new StringReader(obj);
			return parse(reader);
		} catch (IOException exc) {
			throw new IllegalArgumentException("Invalid JSON object string: " + exc.getMessage(), exc);
		}
	}
	/**
	 * @param reader The reader to parse.
	 * @return The parsed JSON object.
	 * @throws IOException If an IO or format error occurs.
	 */
	public static JSONObject parse(Reader reader) throws IOException {
		JSONParser parser = new JSONParser();
		return parser.parse(reader);
	}

	/**
	 * Map ordered by insertion.
	 */
	private final Map<String, JSONEntry> entries = new LinkedHashMap<>();
	/**
	 * @param key The key.
	 * @return The {@link JSONEntry} with the given key.
	 * @throws IllegalArgumentException If the key is not present.
	 */
	public JSONEntry get(String key) {
		JSONEntry entry = entries.get(key);
		if (entry == null) throw new IllegalArgumentException("Invalid key: " + key);
		return entry;
	}

	/**
	 * @param key   The key.
	 * @param entry The entry to put.
	 * @throws IllegalArgumentException If the key or the entry is null.
	 */
	public void put(String key, JSONEntry entry) {
		if (key == null) throw new IllegalArgumentException("Key can not be null");
		if (entry == null) throw new IllegalArgumentException("Entry can not be null");
		entries.put(key, entry);
	}
	/**
	 * @param key The key to put with a NULL value.
	 */
	public void put(String key) { put(key, new JSONEntry()); }
	/**
	 * @param key   The key.
	 * @param value A {@link JSONObject}.
	 */
	public void put(String key, JSONObject value) { put(key, new JSONEntry(value)); }
	/**
	 * @param key   The key.
	 * @param value A {@link JSONArray}.
	 */
	public void put(String key, JSONArray value) { put(key, new JSONEntry(value)); }
	/**
	 * @param key   The key.
	 * @param value A {@link String}.
	 */
	public void put(String key, String value) { put(key, new JSONEntry(value)); }
	/**
	 * @param key   The key.
	 * @param value A {@link Number}.
	 */
	public void put(String key, Number value) { put(key, new JSONEntry(value)); }
	/**
	 * @param key   The key.
	 * @param value A {@link Number}.
	 * @param scale The scale or number of decimals.
	 */
	public void put(String key, Number value, int scale) { put(key, new JSONEntry(value, scale)); }
	/**
	 * @param key   The key.
	 * @param value A boolean.
	 */
	public void put(String key, boolean value) { put(key, new JSONEntry(value)); }
	/**
	 * @param key   The key.
	 * @param value A byte[].
	 */
	public void put(String key, byte[] value) { put(key, new JSONEntry(value)); }
	/**
	 * @param key   The key.
	 * @param value A {@link LocalDate}.
	 */
	public void put(String key, LocalDate value) { put(key, new JSONEntry(value)); }
	/**
	 * @param key   The key.
	 * @param value A {@link LocalTime}.
	 */
	public void put(String key, LocalTime value) { put(key, new JSONEntry(value)); }
	/**
	 * @param key   The key.
	 * @param value A {@link LocalDateTime}.
	 */
	public void put(String key, LocalDateTime value) { put(key, new JSONEntry(value)); }

	/**
	 * @return The collection of keys, in insertion order.
	 */
	public Collection<String> keys() { return entries.keySet(); }

	/**
	 * @param key The key.
	 * @return The removed entry that corresponds to the key, or null if not present.
	 */
	public JSONEntry remove(String key) { return entries.remove(key); }
	/**
	 * @param key The key.
	 * @return Whether the key is contained.
	 */
	public boolean contains(String key) { return entries.containsKey(key); }
	/**
	 * @return Whether this object is empty.
	 */
	public boolean isEmpty() { return entries.isEmpty(); }
	/**
	 * @return The number of entries.
	 */
	public int size() { return entries.size(); }

	/**
	 * Check equals. Uses the string representation.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof JSONObject json) {
			return toString().equals(json.toString());
		}
		return false;
	}
	/**
	 * Hash code. Uses the string representation.
	 */
	@Override
	public int hashCode() {
		return toString().hashCode();
	}

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
