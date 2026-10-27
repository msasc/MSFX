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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * JSON parser.
 * <p>
 * Parses standard JSON (RFC 8259) strictly, and recognizes the extended types
 * written by {@link JSONWriter}: an object with a single extended type key, for
 * instance <code>{"%dt%":"2026-01-01"}</code>, is read as a {@link LocalDate}
 * entry. Such an object whose value does not match its type is kept as a plain
 * object.
 * <p>
 * Format errors are reported as {@link IOException} with the line and column
 * where they were found.
 *
 * @author Miquel Sas
 */
public class JSONParser {

	/**
	 * Marker of no peeked char.
	 */
	private static final int NONE = -2;
	/**
	 * Valid JSON number.
	 */
	private static final Pattern NUMBER = Pattern.compile("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?");

	/**
	 * Reader.
	 */
	private Reader reader;
	/**
	 * Char read ahead and not yet consumed, or NONE.
	 */
	private int peeked = NONE;
	/**
	 * Current line, for error messages.
	 */
	private int line;
	/**
	 * Current column, for error messages.
	 */
	private int column;

	/**
	 * Constructor.
	 */
	public JSONParser() { }

	/**
	 * @param r The source reader.
	 * @return The parsed object, empty if the source is empty.
	 * @throws IOException If an IO or format error occurs.
	 */
	public JSONObject parse(Reader r) throws IOException {
		init(r);
		int c = nextNeat();
		if (c < 0) return new JSONObject();
		if (c != '{') throw error("Expected '{'");
		JSONObject obj = parseObject();
		checkEnd();
		return obj;
	}

	/**
	 * @param r The source reader.
	 * @return The parsed array, empty if the source is empty.
	 * @throws IOException If an IO or format error occurs.
	 */
	public JSONArray parseArray(Reader r) throws IOException {
		init(r);
		int c = nextNeat();
		if (c < 0) return new JSONArray();
		if (c != '[') throw error("Expected '['");
		JSONArray arr = parseArray();
		checkEnd();
		return arr;
	}

	/**
	 * @param r The new source reader to parse, resetting the state.
	 */
	private void init(Reader r) {
		reader = r;
		peeked = NONE;
		line = 1;
		column = 0;
	}

	/**
	 * @throws IOException If anything but whitespace follows the parsed value.
	 */
	private void checkEnd() throws IOException {
		if (nextNeat() >= 0) throw error("Unexpected content after the end");
	}

	/**
	 * @return The object, parsed after its opening '{' has been read.
	 * @throws IOException If an error occurs.
	 */
	private JSONObject parseObject() throws IOException {
		JSONObject obj = new JSONObject();
		int c = nextNeat();
		if (c == '}') return obj;
		while (true) {
			if (c != '"') throw error("Expected a key");
			String key = readString();
			if (nextNeat() != ':') throw error("Expected ':'");
			obj.put(key, readValue(nextNeat()));
			c = nextNeat();
			if (c == '}') return obj;
			if (c != ',') throw error("Expected ',' or '}'");
			c = nextNeat();
		}
	}

	/**
	 * @return The array, parsed after its opening '[' has been read.
	 * @throws IOException If an error occurs.
	 */
	private JSONArray parseArray() throws IOException {
		JSONArray arr = new JSONArray();
		int c = nextNeat();
		if (c == ']') return arr;
		while (true) {
			arr.add(readValue(c));
			c = nextNeat();
			if (c == ']') return arr;
			if (c != ',') throw error("Expected ',' or ']'");
			c = nextNeat();
		}
	}

	/**
	 * @param c The first char of the value, already read.
	 * @return The entry with the value.
	 * @throws IOException If an error occurs.
	 */
	private JSONEntry readValue(int c) throws IOException {
		switch (c) {
			case '{' -> {
				JSONObject obj = parseObject();
				JSONEntry extended = getExtendedTypeEntry(obj);
				return extended != null ? extended : new JSONEntry(obj);
			}
			case '[' -> {
				return new JSONEntry(parseArray());
			}
			case '"' -> {
				return new JSONEntry(readString());
			}
			case 't' -> {
				readWord("true");
				return new JSONEntry(true);
			}
			case 'f' -> {
				readWord("false");
				return new JSONEntry(false);
			}
			case 'n' -> {
				readWord("null");
				return new JSONEntry();
			}
			case -1 -> throw error("Unexpected end of input");
			default -> {
				if (c == '-' || (c >= '0' && c <= '9')) {
					return new JSONEntry(readNumber(c));
				}
				throw error("Unexpected character '" + (char) c + "'");
			}
		}
	}

	/**
	 * @param word The expected word, its first char already read.
	 * @throws IOException If the rest of the word does not follow.
	 */
	private void readWord(String word) throws IOException {
		for (int i = 1; i < word.length(); i++) {
			if (nextChar() != word.charAt(i)) throw error("Expected '" + word + "'");
		}
	}

	/**
	 * @param first The first char of the number, already read.
	 * @return The number.
	 * @throws IOException If the number is not valid.
	 */
	private BigDecimal readNumber(int first) throws IOException {
		StringBuilder b = new StringBuilder();
		b.append((char) first);
		while ("+-.0123456789eE".indexOf(peek()) >= 0) {
			b.append((char) nextChar());
		}
		String str = b.toString();
		if (!NUMBER.matcher(str).matches()) throw error("Invalid number '" + str + "'");
		return new BigDecimal(str);
	}

	/**
	 * @return The string, read after its opening quote, may be empty.
	 * @throws IOException If an error occurs.
	 */
	private String readString() throws IOException {
		StringBuilder b = new StringBuilder();
		while (true) {
			int c = nextChar();
			if (c < 0) throw error("Unterminated string");
			if (c < 0x20) throw error("Control character in string, must be escaped");
			if (c == '"') return b.toString();
			if (c != '\\') {
				b.append((char) c);
				continue;
			}
			int n = nextChar();
			switch (n) {
				case '"' -> b.append('"');
				case '\\' -> b.append('\\');
				case '/' -> b.append('/');
				case 'b' -> b.append('\b');
				case 'f' -> b.append('\f');
				case 'n' -> b.append('\n');
				case 'r' -> b.append('\r');
				case 't' -> b.append('\t');
				case 'u' -> {
					int code = 0;
					for (int i = 0; i < 4; i++) {
						int digit = Character.digit(nextChar(), 16);
						if (digit < 0) throw error("Invalid unicode escape");
						code = code * 16 + digit;
					}
					b.append((char) code);
				}
				default -> throw error("Invalid escape sequence");
			}
		}
	}

	/**
	 * @param obj The JSON object.
	 * @return The extended type entry the object represents, or null if it is not one.
	 */
	private JSONEntry getExtendedTypeEntry(JSONObject obj) {

		/* One and only one entry, with the key of an extended type. */
		if (obj.size() != 1) return null;
		String key = obj.keys().iterator().next();
		JSONTypes type = JSONTypes.fromKey(key);
		if (type == null) return null;

		/* Extended types accept null values. */
		JSONEntry entry = obj.get(key);
		if (entry.isNullType()) return new JSONEntry(type, null);
		if (!entry.isString()) return null;

		/* The string must comply with the type. */
		String str = entry.getString();
		try {
			return switch (type) {
				case BINARY -> new JSONEntry(HexFormat.of().parseHex(str));
				case DATE -> new JSONEntry(LocalDate.parse(str));
				case TIME -> new JSONEntry(LocalTime.parse(str));
				case TIMESTAMP -> new JSONEntry(LocalDateTime.parse(str));
				default -> null;
			};
		} catch (IllegalArgumentException | DateTimeParseException exc) {
			return null;
		}
	}

	/**
	 * @return The next char that is not JSON whitespace, or -1 at the end of the stream.
	 * @throws IOException If an error occurs.
	 */
	private int nextNeat() throws IOException {
		while (true) {
			int c = nextChar();
			if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return c;
		}
	}

	/**
	 * @return The next char without consuming it, or -1 at the end of the stream.
	 * @throws IOException If an error occurs.
	 */
	private int peek() throws IOException {
		if (peeked == NONE) peeked = reader.read();
		return peeked;
	}

	/**
	 * @return The next char, consumed and with the position tracked, or -1 at the end of the stream.
	 * @throws IOException If an error occurs.
	 */
	private int nextChar() throws IOException {
		int c = peek();
		peeked = NONE;
		if (c == '\n') {
			line++;
			column = 0;
		} else if (c >= 0) {
			column++;
		}
		return c;
	}

	/**
	 * @param message The message.
	 * @return The format error, with the current position appended to the message.
	 */
	private IOException error(String message) {
		return new IOException(message + " at line " + line + ", column " + column);
	}
}
