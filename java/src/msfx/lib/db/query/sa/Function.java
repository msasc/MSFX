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
 * Functions accepted in hand-written expressions. The name is the name of the
 * constant, accepted in any case, and the arguments are those of our own
 * dialect, the same for every database. Each database engine writes the
 * function in its own form.
 * <p>
 * Functions are always called with parentheses, even with no arguments.
 *
 * @author Miquel Sas
 */
public enum Function {

	/*
	 * Aggregates.
	 */

	/** COUNT(*), COUNT(x) or COUNT(DISTINCT x): number of rows or values. */
	COUNT(1, 1, true),
	/** SUM(x): sum of the values. */
	SUM(1, 1, true),
	/** AVG(x): average of the values. */
	AVG(1, 1, true),
	/** MIN(x): minimum value. */
	MIN(1, 1, true),
	/** MAX(x): maximum value. */
	MAX(1, 1, true),

	/*
	 * Strings.
	 */

	/**
	 * LENGTH(s): number of characters, trailing spaces included, 0 for null or
	 * empty strings.
	 */
	LENGTH(1, 1),
	/** SUBSTRING(s, start, length): part of the string, start from 1. */
	SUBSTRING(3, 3),
	/** LEFT(s, n): first n characters. */
	LEFT(2, 2),
	/** RIGHT(s, n): last n characters. */
	RIGHT(2, 2),
	/** POSITION(s, sub): position of sub in s from 1, or 0 if not found. */
	POSITION(2, 2),
	/** UPPER(s): upper case. */
	UPPER(1, 1),
	/** LOWER(s): lower case. */
	LOWER(1, 1),
	/** TRIM(s): without leading and trailing spaces. */
	TRIM(1, 1),
	/** LTRIM(s): without leading spaces. */
	LTRIM(1, 1),
	/** RTRIM(s): without trailing spaces. */
	RTRIM(1, 1),
	/** REPLACE(s, from, to): occurrences of from replaced by to. */
	REPLACE(3, 3),
	/**
	 * LPAD(s, n [, c]): left padded to n characters with the single character c,
	 * a space by default, or cut to its first n characters if longer.
	 */
	LPAD(2, 3),
	/**
	 * RPAD(s, n [, c]): right padded to n characters with the single character
	 * c, a space by default, or cut to its first n characters if longer.
	 */
	RPAD(2, 3),

	/*
	 * Numbers.
	 */

	/** ABS(n): absolute value. */
	ABS(1, 1),
	/** SIGN(n): -1, 0 or 1. */
	SIGN(1, 1),
	/** SQRT(n): square root. */
	SQRT(1, 1),
	/** FLOOR(n): largest integer less than or equal to n. */
	FLOOR(1, 1),
	/** CEILING(n): smallest integer greater than or equal to n. */
	CEILING(1, 1),
	/** ROUND(n [, d]): rounded to d decimals, 0 by default. */
	ROUND(1, 2),
	/** TRUNCATE(n [, d]): truncated to d decimals, 0 by default. */
	TRUNCATE(1, 2),
	/** MOD(a, b): remainder of a divided by b. */
	MOD(2, 2),
	/** POWER(a, b): a raised to b. */
	POWER(2, 2),

	/*
	 * Nulls.
	 */

	/** COALESCE(a, b, ...): first argument not null. */
	COALESCE(2, Integer.MAX_VALUE),
	/** NULLIF(a, b): null if a equals b, else a. */
	NULLIF(2, 2),

	/*
	 * Dates and times.
	 */

	/** CURRENT_DATE(): current date of the database server. */
	CURRENT_DATE(0, 0),
	/** CURRENT_TIMESTAMP(): current date and time of the database server. */
	CURRENT_TIMESTAMP(0, 0),
	/** YEAR(d): year of the date. */
	YEAR(1, 1),
	/** MONTH(d): month of the date, 1 to 12. */
	MONTH(1, 1),
	/** DAY(d): day of the month. */
	DAY(1, 1),
	/** HOUR(t): hour, 0 to 23. */
	HOUR(1, 1),
	/** MINUTE(t): minute. */
	MINUTE(1, 1),
	/** SECOND(t): second. */
	SECOND(1, 1),
	/** ADD_DAYS(d, n): date plus n days. */
	ADD_DAYS(2, 2),
	/** ADD_MONTHS(d, n): date plus n months. */
	ADD_MONTHS(2, 2),
	/** DAYS_BETWEEN(d1, d2): days from d1 to d2. */
	DAYS_BETWEEN(2, 2),

	/*
	 * Conversions, with ISO formats for dates and times.
	 */

	/** TO_STRING(x): x as a string, dates as YYYY-MM-DD. */
	TO_STRING(1, 1),
	/** TO_NUMBER(s): the string as a number. */
	TO_NUMBER(1, 1),
	/** TO_DATE(s): the string YYYY-MM-DD as a date. */
	TO_DATE(1, 1),
	/**
	 * TO_TIMESTAMP(s): the string YYYY-MM-DD HH:MI[:SS[.FFF]] as a timestamp,
	 * with a space or a T between date and time, and 00 seconds when they are
	 * missing.
	 */
	TO_TIMESTAMP(1, 1);

	/**
	 * Map of functions by name, ignoring case.
	 */
	private static final Map<String, Function> BY_NAME = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	static {
		for (Function function : values()) {
			BY_NAME.put(function.name(), function);
		}
	}

	/**
	 * Returns the function with the name, in any case.
	 *
	 * @param name The name.
	 * @return The function, or null if the name is not a function.
	 */
	public static Function of(String name) {
		return BY_NAME.get(name);
	}

	/**
	 * Minimum number of arguments.
	 */
	private final int minArguments;
	/**
	 * Maximum number of arguments, Integer.MAX_VALUE if unbounded.
	 */
	private final int maxArguments;
	/**
	 * A flag that indicates whether the function is an aggregate.
	 */
	private final boolean aggregate;

	/**
	 * Constructor of a function that is not an aggregate.
	 *
	 * @param minArguments Minimum number of arguments.
	 * @param maxArguments Maximum number of arguments.
	 */
	private Function(int minArguments, int maxArguments) {
		this(minArguments, maxArguments, false);
	}

	/**
	 * Constructor.
	 *
	 * @param minArguments Minimum number of arguments.
	 * @param maxArguments Maximum number of arguments.
	 * @param aggregate    A flag that indicates whether it is an aggregate.
	 */
	private Function(int minArguments, int maxArguments, boolean aggregate) {
		this.minArguments = minArguments;
		this.maxArguments = maxArguments;
		this.aggregate = aggregate;
	}

	/**
	 * Returns the minimum number of arguments.
	 *
	 * @return The minimum number of arguments.
	 */
	public int getMinArguments() {
		return minArguments;
	}

	/**
	 * Returns the maximum number of arguments.
	 *
	 * @return The maximum number of arguments, Integer.MAX_VALUE if unbounded.
	 */
	public int getMaxArguments() {
		return maxArguments;
	}

	/**
	 * Check whether the function is an aggregate, accepted only in the select
	 * list and in HAVING.
	 *
	 * @return A boolean.
	 */
	public boolean isAggregate() {
		return aggregate;
	}
}
