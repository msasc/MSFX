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

package msfx.lib.db.query.sa;

import java.util.Map;
import java.util.TreeMap;

/**
 * Words with a meaning of their own in hand-written expressions. The word is
 * the name of the constant, and is accepted in any case.
 *
 * @author Miquel Sas
 */
public enum Keyword {

	/** Logical conjunction. */
	AND,
	/** Logical disjunction. */
	OR,
	/** Logical negation, also in NOT IN, NOT LIKE, NOT BETWEEN, IS NOT NULL. */
	NOT,
	/** Null check: IS NULL, IS NOT NULL. */
	IS,
	/** The null value, only after IS. */
	NULL,
	/** Membership in a list of values. */
	IN,
	/** Range: BETWEEN low AND high. */
	BETWEEN,
	/** Pattern matching. */
	LIKE,
	/** Start of a CASE expression. */
	CASE,
	/** Condition of a CASE branch. */
	WHEN,
	/** Result of a CASE branch. */
	THEN,
	/** Default result of a CASE expression. */
	ELSE,
	/** End of a CASE expression. */
	END,
	/** Different values only, as the first word inside an aggregate. */
	DISTINCT;

	/**
	 * Map of keywords by word, ignoring case.
	 */
	private static final Map<String, Keyword> BY_WORD = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	static {
		for (Keyword keyword : values()) {
			BY_WORD.put(keyword.name(), keyword);
		}
	}

	/**
	 * Returns the keyword written with the word, in any case.
	 *
	 * @param word The word.
	 * @return The keyword, or null if the word is not a keyword.
	 */
	public static Keyword of(String word) {
		return BY_WORD.get(word);
	}
}
