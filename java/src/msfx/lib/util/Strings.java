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
package msfx.lib.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * String utilities.
 *
 * @author Miquel Sas
 */
public class Strings {
	private Strings() { }

	/**
	 * Sample list of consonants.
	 */
	public static final String CONSONANTS = "BCDFGHJKLMNPQRSTVWXYZ";
	/**
	 * Sample list of digits.
	 */
	public static final String DIGITS = "0123456789";
	/**
	 * Sample list of letters.
	 */
	public static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	/**
	 * Sample list of vowels.
	 */
	public static final String VOWELS = "AEIOU";
	/**
	 * A String for a space character.
	 */
	public static final String SPACE = " ";
	/**
	 * Represents a failed index search.
	 */
	public static final int INDEX_NOT_FOUND = -1;

	/**
	 * Return a blank string of the given size.
	 *
	 * @param size The size.
	 * @return The blank string.
	 */
	public static String blank(int size) {
		return repeat(" ", size);
	}

	/**
	 * Capitalize the string.
	 *
	 * @param str The string to capitalize.
	 * @return The capitalized string.
	 */
	public static String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		char first = str.charAt(0);
		char firstCap = Character.toUpperCase(first);
		if (first == firstCap) {
			return str;
		}
		StringBuilder b = new StringBuilder();
		b.append(firstCap);
		b.append(str.substring(1));
		return b.toString();
	}

	/**
	 * Center pad.
	 *
	 * @param str  The string to pad.
	 * @param size The size.
	 * @return The padded string
	 */
	public static String centerPad(String str, int size) {
		return centerPad(str, size, " ");
	}

	/**
	 * Center pad.
	 *
	 * @param str    The string to pad.
	 * @param size   The size.
	 * @param padChr The pad character.
	 * @return The padded string
	 */
	public static String centerPad(String str, int size, char padChr) {
		return centerPad(str, size, String.valueOf(padChr));
	}

	/**
	 * Center pad.
	 *
	 * @param str    The string to pad.
	 * @param size   The size.
	 * @param padStr The pad string.
	 * @return The padded string
	 */
	public static String centerPad(String str, int size, String padStr) {
		if (size < str.length()) {
			int start = (str.length() - size) / 2;
			return str.substring(start, start + size);
		}
		int pad = size - str.length();
		int left = pad / 2;
		int right = pad - left;
		String leftStr = repeat(padStr, left).substring(0, left);
		String rightStr = repeat(padStr, right).substring(0, right);
		return leftStr + str + rightStr;
	}

	/**
	 * Concatenate the strings.
	 *
	 * @param strs The list of string to concatenate to the source.
	 * @return The concatenated string.
	 */
	public static String concat(String... strs) {
		StringBuilder b = new StringBuilder();
		if (strs != null) {
			for (String str : strs)
				b.append(str);
		}
		return b.toString();
	}

	/**
	 * Check whether the string contains the search char.
	 *
	 * @param source Source string.
	 * @param search Search char.
	 * @return A boolean.
	 */
	public static boolean contains(String source, char search) {
		return source.contains(String.valueOf(search));
	}

	/**
	 * Check whether the string contains the search string.
	 *
	 * @param source Source string.
	 * @param search Search string.
	 * @return A boolean.
	 */
	public static boolean contains(String source, String search) {
		return source.contains(search);
	}

	/**
	 * Check whether the string containing only the valid chars.
	 *
	 * @param source     The source string.
	 * @param validChars The list of valid chars.
	 * @return A boolean.
	 */
	public static boolean containsOnly(String source, String validChars) {
		for (int i = 0; i < source.length(); i++) {
			if (validChars.indexOf(source.charAt(i)) < 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Count the matches.
	 *
	 * @param source Source string.
	 * @param search Search char.
	 * @return The number of matches.
	 */
	public static int countMatches(String source, char search) {
		if (source.isEmpty()) {
			return 0;
		}
		int count = 0;
		for (int i = 0; i < source.length(); i++) {
			if (search == source.charAt(i)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Count the matches.
	 *
	 * @param source Source string.
	 * @param search Search string.
	 * @return The number of matches.
	 */
	public static int countMatches(String source, String search) {
		if (isEmpty(source) || isEmpty(search)) {
			return 0;
		}
		int count = 0;
		int idx = 0;
		while ((idx = indexOf(source, search, idx)) != INDEX_NOT_FOUND) {
			count++;
			idx += search.length();
		}
		return count;
	}
	/**
	 * @param decimals The number of decimals.
	 * @return The default string number.
	 */
	public static String defaultStringNumber(int decimals) {
		StringBuffer b = new StringBuffer();
		b.append("0");
		if (decimals > 0) {
			b.append(".");
			b.append(repeat("0", decimals));
		}
		return b.toString();
	}

	/**
	 * Check for equality.
	 *
	 * @param a String a.
	 * @param b String b.
	 * @return A boolean.
	 */
	public static boolean equals(String a, String b) {
		return a.equals(b);
	}

	/**
	 * Returns the first string not null, or an empty string.
	 *
	 * @param strings The list of strings.
	 * @return The first string not null, or an empty string.
	 */
	public static String getFirstNotNull(String... strings) {
		StringBuilder b = new StringBuilder();
		for (String s : strings) {
			if (s != null) {
				b.append(s);
				break;
			}
		}
		return b.toString();
	}

	/**
	 * Return all the stack trace of the throwable.
	 *
	 * @param e The throwable.
	 * @return The stack trace.
	 */
	public static String getStackTrace(Throwable e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		return sw.toString();
	}

	/**
	 * Check if the string is contained in the list of options.
	 *
	 * @param string  The source string.
	 * @param strings The list of strings.
	 * @return A boolean.
	 */
	public static boolean in(String string, String... strings) {
		if (strings == null) {
			return false;
		}
		for (int i = 0; i < strings.length; i++) {
			if (strings[i].equals(string)) {
				return true;
			}
		}
		return false;
	}
	/**
	 * Check that the string is within the list of lists of strings.
	 *
	 * @param str   The string to check..
	 * @param strss The list of lists of strings.
	 */
	public static boolean in(String str, String[]... strss) {
		for (String[] strs : strss) {
			if (Strings.in(str, strs)) return true;
		}
		return false;
	}

	/**
	 * Returns the index of the search char.
	 *
	 * @param source Source string.
	 * @param search Search char.
	 * @return The search index.
	 */
	public static int indexOf(String source, char search) {
		return source.indexOf(search);
	}

	/**
	 * Returns the index of the search char.
	 *
	 * @param source    Source string.
	 * @param search    Search char.
	 * @param fromIndex From index.
	 * @return The search index.
	 */
	public static int indexOf(String source, char search, int fromIndex) {
		return source.indexOf(search, fromIndex);
	}

	/**
	 * Returns the index of the search char.
	 *
	 * @param source Source string.
	 * @param search Search string.
	 * @return The search index.
	 */
	public static int indexOf(String source, String search) {
		return source.indexOf(search);
	}

	/**
	 * Returns the index of the search string.
	 *
	 * @param source    Source string.
	 * @param search    Search string.
	 * @param fromIndex From index.
	 * @return The search index.
	 */
	public static int indexOf(String source, String search, int fromIndex) {
		return source.indexOf(search, fromIndex);
	}

	/**
	 * Check whether the string is empty. Null is empty.
	 *
	 * @param str The string.
	 * @return A boolean.
	 */
	public static boolean isEmpty(String str) {
		return (str == null ? true : str.isEmpty());
	}

	/**
	 * Check whether the string builder is empty. Null is empty.
	 *
	 * @param b The string builder.
	 * @return A boolean.
	 */
	public static boolean isEmpty(StringBuilder b) {
		return (b == null || b.isEmpty());
	}

	/**
	 * Left pad a number. Parent Apache does not work as expected.
	 *
	 * @param num  The number to pad out, can not be null.
	 * @param size The size to pad to
	 * @return The left padded string.
	 */
	public static String leftPad(Number num, int size) {
		if (num == null) throw new NullPointerException();
		return leftPad(num.toString(), size, SPACE);
	}

	/**
	 * Left pad a number. Parent Apache does not work as expected.
	 *
	 * @param num    The number to pad out, can not be null.
	 * @param size   The size to pad to
	 * @param padStr The string to pad with.
	 * @return The left padded string.
	 */
	public static String leftPad(Number num, int size, String padStr) {
		if (num == null) throw new NullPointerException();
		return leftPad(num.toString(), size, padStr);
	}

	/**
	 * Left pad a string. Parent Apache does not work as expected.
	 *
	 * @param str  The string to pad out, may be null.
	 * @param size The size to pad to
	 * @return The left padded string.
	 */
	public static String leftPad(String str, int size) {
		return leftPad(str, size, SPACE);
	}

	/**
	 * Left pad a string. Parent Apache does not work as expected.
	 *
	 * @param str    The string to pad out, may be null.
	 * @param size   The size to pad to
	 * @param padChr The char to pad with.
	 * @return The left padded string.
	 */
	public static String leftPad(String str, int size, char padChr) {
		return leftPad(str, size, String.valueOf(padChr));
	}

	/**
	 * Left pad a string. Parent Apache does not work as expected.
	 *
	 * @param str    The string to pad out, may be null.
	 * @param size   The size to pad to
	 * @param padStr The string to pad with, null or empty treated as single space.
	 * @return The left padded string.
	 */
	public static String leftPad(String str, int size, String padStr) {
		if (str == null) {
			return null;
		}
		if (isEmpty(padStr)) {
			padStr = SPACE;
		}
		while (str.length() < size) {
			str = concat(padStr, str);
		}
		return substring(str, str.length() - size);
	}

	/**
	 * Parse a string
	 *
	 * @param string    The string to parse.
	 * @param separator The separator.
	 * @return the list of tokens
	 */
	public static List<String> parse(String string, String separator) {
		StringTokenizer tokenizer = new StringTokenizer(string, separator);
		List<String> tokens = new ArrayList<>();
		while (tokenizer.hasMoreTokens()) {
			tokens.add(tokenizer.nextToken().trim());
		}
		return tokens;
	}
	/**
	 * Parse CSS styles (with ; as separator)
	 *
	 * @param styleArray The list of styles.
	 * @return A list of styles.
	 */
	public static List<String> parseCSS(String... styleArray) {
		List<String> styles = new ArrayList<>();
		for (String style : styleArray) {
			List<String> styleList = Strings.parse(style, ";");
			for (int i = 0; i < styleList.size(); i++) {
				styleList.set(i, styleList.get(i).trim());
			}
			styles.addAll(styleList);
		}
		return styles;
	}
	/**
	 * Parse CSS styles (with ; as separator)
	 *
	 * @param styles The list of styles.
	 * @return A list of styles.
	 */
	public static List<String> parseCSS(List<String> styles) {
		return parseCSS(styles.toArray(new String[styles.size()]));
	}

	/**
	 * Parse and capitalize.
	 *
	 * @param srcStr Source string.
	 * @param srcSep Source separator.
	 * @param dstSep Destination separator.
	 * @return The capitalized string.
	 */
	public static String parseCapitalize(String srcStr, String srcSep, String dstSep) {
		StringBuilder b = new StringBuilder();
		List<String> words = parse(srcStr, srcSep);
		for (int i = 0; i < words.size(); i++) {
			if (i > 0) {
				b.append(dstSep);
			}
			b.append(capitalize(words.get(i)));
		}
		return b.toString();
	}

	/**
	 * Remove all incidences.
	 *
	 * @param source The source string.
	 * @param remove The char to remove.
	 * @return The result string.
	 */
	public static String remove(String source, char remove) {
		return source.replace(String.valueOf(remove), "");
	}

	/**
	 * Remove all incidences of the list of characters.
	 *
	 * @param source The source string.
	 * @param remove The characters to remove.
	 * @return The result string.
	 */
	public static String remove(String source, char... remove) {
		String result = source;
		for (char c : remove) {
			result = result.replace(String.valueOf(c), "");
		}
		return result;
	}

	/**
	 * Remove all incidences.
	 *
	 * @param source The source string.
	 * @param remove The string to remove.
	 * @return The result string.
	 */
	public static String remove(String source, String remove) {
		return source.replace(remove, "");
	}

	/**
	 * Remove all incidences of the strings.
	 *
	 * @param source The source string.
	 * @param remove The strings to remove.
	 * @return The result string.
	 */
	public static String remove(String source, String... remove) {
		String result = source;
		for (String s : remove) {
			result = result.replace(s, "");
		}
		return result;
	}

	/**
	 * Returns a string whose value is the concatenation of the argument string repeated count
	 * times.
	 *
	 * @param str   The string to repeat.
	 * @param count The number times.
	 * @return The repeated string.
	 */
	public static String repeat(String str, int count) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < count; i++)
			b.append(str);
		return b.toString();
	}

	/**
	 * Replaces all incidences.
	 *
	 * @param source  Source string.
	 * @param search  Search char.
	 * @param replace Replace char.
	 * @return The result string.
	 */
	public static String replace(String source, char search, char replace) {
		return source.replace(search, replace);
	}

	/**
	 * Replaces all incidences.
	 *
	 * @param source  Source string.
	 * @param search  Search string.
	 * @param replace Replace string.
	 * @return The result string.
	 */
	public static String replace(String source, String search, String replace) {
		return source.replace(search, replace);
	}

	/**
	 * Reverses a string.
	 *
	 * @param str the String to reverse, may be null
	 * @return the reversed String, {@code null} if null String input
	 */
	public static String reverse(final String str) {
		if (str == null) {
			return null;
		}
		return new StringBuilder(str).reverse().toString();
	}

	/**
	 * Right pad a number. Parent Apache does not work as expected.
	 *
	 * @param num  The number to pad out, can not be null.
	 * @param size The size to pad to
	 * @return The right padded string.
	 */
	public static String rightPad(Number num, int size) {
		if (num == null) throw new NullPointerException();
		return rightPad(num.toString(), size, SPACE);
	}

	/**
	 * Right pad a string. Parent Apache does not work as expected.
	 *
	 * @param str  The string to pad out, may be null.
	 * @param size The size to pad to
	 * @return The right padded string.
	 */
	public static String rightPad(String str, int size) {
		return rightPad(str, size, SPACE);
	}

	/**
	 * Right pad a string. Parent Apache does not work as expected.
	 *
	 * @param str    The string to pad out, may be null.
	 * @param size   The size to pad to
	 * @param padChr The char to pad with.
	 * @return The right padded string.
	 */
	public static String rightPad(String str, int size, char padChr) {
		return rightPad(str, size, String.valueOf(padChr));
	}

	/**
	 * Right pad a string. Parent Apache does not work as expected.
	 *
	 * @param str    The string to pad out, may be null.
	 * @param size   The size to pad to.
	 * @param padStr The string to pad with, null or empty treated as single space.
	 * @return The right padded string.
	 */
	public static String rightPad(String str, int size, String padStr) {
		if (str == null) {
			return null;
		}
		if (isEmpty(padStr)) {
			padStr = SPACE;
		}
		while (str.length() < size) {
			str = concat(str, padStr);
		}
		return substring(str, 0, size);
	}

	/**
	 * Tests whether the argument string starts with the specified prefix.
	 *
	 * @param str    The string.
	 * @param prefix The prefix.
	 * @return A boolean.
	 */
	public static boolean startsWith(String str, String prefix) {
		return str.startsWith(prefix);
	}

	/**
	 * Return the substring.
	 *
	 * @param str        The source string.
	 * @param beginIndex Begin index.
	 * @return The substring.
	 */
	public static String substring(String str, int beginIndex) {
		beginIndex = Math.max(0, beginIndex);
		beginIndex = Math.min(beginIndex, str.length());
		return str.substring(beginIndex);
	}

	/**
	 * Return the substring.
	 *
	 * @param str        The source string.
	 * @param beginIndex Begin index.
	 * @param endIndex   End index.
	 * @return The substring.
	 */
	public static String substring(String str, int beginIndex, int endIndex) {
		beginIndex = Math.max(0, beginIndex);
		beginIndex = Math.min(beginIndex, str.length());
		endIndex = Math.min(endIndex, str.length());
		return str.substring(beginIndex, endIndex);
	}

	/**
	 * Return the string representation of the vector.
	 *
	 * @param a The vector.
	 * @return The string representation.
	 */
	public static String toString(int[] a) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < a.length; i++) {
			if (i > 0) {
				b.append(", ");
			}
			b.append(a[i]);
		}
		return b.toString();
	}

	/**
	 * Return the string representation of the vector.
	 *
	 * @param a The vector.
	 * @return The string representation.
	 */
	public static String toString(double[] a) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < a.length; i++) {
			if (i > 0) {
				b.append(", ");
			}
			b.append(a[i]);
		}
		return b.toString();
	}

	/**
	 * Return the string representation of the vector.
	 *
	 * @param <T> The type of the vector.
	 * @param a   The vector.
	 * @return The string representation.
	 */
	public static <T> String toString(T[] a) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < a.length; i++) {
			if (i > 0) {
				b.append(", ");
			}
			b.append(a[i]);
		}
		return b.toString();
	}

	/**
	 * Return the boolean value as a string.
	 *
	 * @param value    The boolean value.
	 * @param strTrue  The string when value is true.
	 * @param strFalse The string when value is false.
	 * @return The boolean string for value.
	 */
	public static String toBooleanString(boolean value, String strTrue, String strFalse) {
		return value ? strTrue : strFalse;
	}

	/**
	 * Returns a Boolean if str in strTrue or strFalse, null otherwise.
	 *
	 * @param str      The string to check.
	 * @param strTrue  The string that represents the true value.
	 * @param strFalse The string that represents the false value.
	 * @return A Boolean or null.
	 */
	public static Boolean fromBooleanString(String str, String strTrue, String strFalse) {
		if (!in(str, strTrue, strFalse)) return null;
		return str.toLowerCase().equals(strTrue.toLowerCase());
	}

	public static String toString(LocalDateTime time, boolean printDate) {
		StringBuilder b = new StringBuilder();
		if (printDate) {
			b.append(time.getYear());
			b.append("-");
			b.append(leftPad(time.getMonthValue(), 2, "0"));
			b.append("-");
			b.append(leftPad(time.getDayOfMonth(), 2, "0"));
			b.append(" ");
		}
		b.append(leftPad(time.getHour(), 2, "0"));
		b.append(":");
		b.append(leftPad(time.getMinute(), 2, "0"));
		b.append(":");
		b.append(leftPad(time.getSecond(), 2, "0"));
		return b.toString();
	}

	/**
	 * Returns a string representation of the duration.
	 *
	 * @param duration The duration.
	 * @return The string representation.
	 */
	public static String toString(Duration duration) {
		return toString(duration, false);
	}
	/**
	 * Returns a string representation of the duration.
	 *
	 * @param duration The duration.
	 * @param millis   A boolean indicating whether to show the millis part.
	 * @return The string representation.
	 */
	public static String toString(Duration duration, boolean millis) {

		// @formatter:off
		Object[][] info = new Object[][] {
			{ duration.toDaysPart(), "", "d" },
			{ duration.toHoursPart(), ":", "h" },
			{ duration.toMinutesPart(), ":", "m" },
			{ duration.toSecondsPart(), ":", "s" },
			{ duration.toMillisPart(), ".", "" },
		};
		// @formatter:on

		StringBuilder b = new StringBuilder();
		boolean include = false;
		for (int i = 0; i < info.length; i++) {
			if (!millis && i == info.length - 1) break;
			Number val = null;
			if (i == 0) val = (Long) info[i][0];
			else val = (Integer) info[i][0];
			String sep = (String) info[i][1];
			String unit = (String) info[i][2];
			if (include || val.longValue() > 0) {
				if (include) b.append(sep);
				if (i == 0) {
					b.append(val);
				} else if (i == info.length - 1) {
					b.append(leftPad(val, 3, "0"));
				} else {
					b.append(leftPad(val, 2, "0"));
				}
				b.append(unit);
				include = true;
			}
		}

		return b.toString();
	}

	/**
	 * Convert the character to the escaped unicode string of the form "\u0032".
	 *
	 * @param c The unicode character to convert.
	 * @return The unicode escaped string.
	 */
	public static String toUnicode(char c) {
		return "\\u" + leftPad(Integer.toHexString(c), 4, "0");
	}
}
