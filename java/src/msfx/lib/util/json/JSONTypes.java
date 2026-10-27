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

package msfx.lib.util.json;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * JSON types.
 *
 * @author Miquel Sas
 */
public enum JSONTypes {

	/**
	 * Standard JSON object type.
	 */
	OBJECT(null),
	/**
	 * Standard JSON array type.
	 */
	ARRAY(null),
	/**
	 * Standard string type.
	 */
	STRING(null),
	/**
	 * Standard number type.
	 */
	NUMBER(null),
	/**
	 * Standard boolean type.
	 */
	BOOLEAN(null),
	/**
	 * Standard NULL type.
	 */
	NULL(null),

	/**
	 * Extended binary type.
	 */
	BINARY("%bin%"),
	/**
	 * Extended date type.
	 */
	DATE("%dt%"),
	/**
	 * Extended time type.
	 */
	TIME("%tm%"),
	/**
	 * Extended timestamp (date-time) type.
	 */
	TIMESTAMP("%ts%");

	/**
	 * Extended types, read-only.
	 */
	public static final Set<JSONTypes> EXTENDED_TYPES = Collections.unmodifiableSet(
			EnumSet.of(BINARY, DATE, TIME, TIMESTAMP));

	/**
	 * Key to store extended types, null for standard types.
	 */
	public final String key;

	/**
	 * @param key The key for extended types, null for standard types.
	 */
	JSONTypes(String key) {
		this.key = key;
	}

	/**
	 * @return Whether this is an extended type.
	 */
	public boolean isExtended() {
		return key != null;
	}

	/**
	 * @param key The key.
	 * @return The extended type that uses the key, or null if none does.
	 */
	public static JSONTypes fromKey(String key) {
		for (JSONTypes type : EXTENDED_TYPES) {
			if (type.key.equals(key)) {
				return type;
			}
		}
		return null;
	}
}
