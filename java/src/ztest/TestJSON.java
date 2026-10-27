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
package ztest;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import msfx.lib.util.json.JSONArray;
import msfx.lib.util.json.JSONEntry;
import msfx.lib.util.json.JSONObject;
import msfx.lib.util.json.JSONParser;
import msfx.lib.util.json.JSONTypes;
import msfx.lib.util.json.JSONWriter;

/**
 * Tests of the JSON package. Run as a Java application, it prints every check
 * and a summary, and exits with code 1 if any check fails.
 *
 * @author Miquel Sas
 */
public class TestJSON {

	/**
	 * Number of checks passed.
	 */
	private static int passed = 0;
	/**
	 * Names of the checks failed.
	 */
	private static final List<String> failed = new ArrayList<>();

	/**
	 * A block of code that may throw any exception.
	 */
	@FunctionalInterface
	private interface Block {
		/**
		 * @throws Exception Any exception.
		 */
		void run() throws Exception;
	}

	/**
	 * @param args Not used.
	 */
	public static void main(String[] args) {
		run("Entries and types", TestJSON::testEntries);
		run("Numbers", TestJSON::testNumbers);
		run("Strings and escapes", TestJSON::testStrings);
		run("Extended types", TestJSON::testExtendedTypes);
		run("JSONObject operations", TestJSON::testObject);
		run("JSONArray operations", TestJSON::testArray);
		run("Equals and hash code", TestJSON::testEquals);
		run("Writer", TestJSON::testWriter);
		run("Round trip", TestJSON::testRoundTrip);
		run("Parser, valid input", TestJSON::testParserValid);
		run("Parser, invalid input", TestJSON::testParserInvalid);
		run("Parser, error positions", TestJSON::testErrorPositions);
		run("Stress", TestJSON::testStress);

		System.out.println();
		System.out.println("Passed: " + passed + ", failed: " + failed.size());
		for (String name : failed) {
			System.out.println("   FAILED: " + name);
		}
		System.exit(failed.isEmpty() ? 0 : 1);
	}

	/* ---------------------------------------------------------------------------
	 * Tests.
	 * ------------------------------------------------------------------------ */

	/**
	 * Entry constructors, type checks and typed getters.
	 */
	private static void testEntries() {
		checkEquals("default entry is NULL", JSONTypes.NULL, new JSONEntry().getType());
		check("NULL entry has null value", new JSONEntry().isNullValue());
		checkEquals("object type", JSONTypes.OBJECT, new JSONEntry(new JSONObject()).getType());
		checkEquals("array type", JSONTypes.ARRAY, new JSONEntry(new JSONArray()).getType());
		checkEquals("string type", JSONTypes.STRING, new JSONEntry("x").getType());
		checkEquals("number type", JSONTypes.NUMBER, new JSONEntry(1).getType());
		checkEquals("boolean type", JSONTypes.BOOLEAN, new JSONEntry(true).getType());
		checkEquals("binary type", JSONTypes.BINARY, new JSONEntry(new byte[] { 1 }).getType());
		checkEquals("date type", JSONTypes.DATE, new JSONEntry(LocalDate.now()).getType());
		checkEquals("time type", JSONTypes.TIME, new JSONEntry(LocalTime.now()).getType());
		checkEquals("timestamp type", JSONTypes.TIMESTAMP, new JSONEntry(LocalDateTime.now()).getType());

		JSONEntry str = new JSONEntry("text");
		check("isString", str.isString());
		check("not isNumber", !str.isNumber());
		check("not isNullType", !str.isNullType());
		checkEquals("getString", "text", str.getString());
		checkThrows("getNumber on a string", IllegalStateException.class, str::getNumber);
		checkThrows("getObject on a string", IllegalStateException.class, str::getObject);
		checkThrows("getArray on a string", IllegalStateException.class, str::getArray);
		checkThrows("getBoolean on a string", IllegalStateException.class, str::getBoolean);
		checkThrows("getBinary on a string", IllegalStateException.class, str::getBinary);
		checkThrows("getDate on a string", IllegalStateException.class, str::getDate);
		checkThrows("getTime on a string", IllegalStateException.class, str::getTime);
		checkThrows("getTimestamp on a string", IllegalStateException.class, str::getTimestamp);
		checkThrows("getString on a number", IllegalStateException.class, () -> new JSONEntry(1).getString());

		checkThrows("null string", NullPointerException.class, () -> new JSONEntry((String) null));
		checkThrows("null number", NullPointerException.class, () -> new JSONEntry((Number) null));
		checkThrows("null object", NullPointerException.class, () -> new JSONEntry((JSONObject) null));
		checkThrows("null array", NullPointerException.class, () -> new JSONEntry((JSONArray) null));

		/* Extended types accept null and keep their type. */
		JSONEntry nullDate = new JSONEntry((LocalDate) null);
		check("null date keeps DATE type", nullDate.isDate());
		check("null date has null value", nullDate.isNullValue());
		check("null date is not NULL type", !nullDate.isNullType());
		checkEquals("null date getDate", null, nullDate.getDate());
		check("null binary keeps BINARY type", new JSONEntry((byte[]) null).isBinary());

		/* Types enum. */
		check("BINARY is extended", JSONTypes.BINARY.isExtended());
		check("STRING is not extended", !JSONTypes.STRING.isExtended());
		checkEquals("fromKey %dt%", JSONTypes.DATE, JSONTypes.fromKey("%dt%"));
		checkEquals("fromKey %ts%", JSONTypes.TIMESTAMP, JSONTypes.fromKey("%ts%"));
		checkEquals("fromKey unknown", null, JSONTypes.fromKey("x"));
		checkEquals("four extended types", 4, JSONTypes.EXTENDED_TYPES.size());
		checkThrows("EXTENDED_TYPES is read-only", UnsupportedOperationException.class,
				() -> JSONTypes.EXTENDED_TYPES.add(JSONTypes.STRING));
	}

	/**
	 * Number conversion, scale and output.
	 */
	private static void testNumbers() {
		checkNumber("int", 42, "42");
		checkNumber("long max", Long.MAX_VALUE, "9223372036854775807");
		checkNumber("short", (short) -7, "-7");
		checkNumber("byte", (byte) 5, "5");
		checkNumber("double", 3.25, "3.25");
		checkNumber("float keeps short form", 0.1f, "0.1");
		checkNumber("double keeps short form", 0.1, "0.1");
		checkNumber("big integer", new BigInteger("123456789012345678901234567890"),
				"123456789012345678901234567890");
		checkNumber("big decimal keeps scale", new BigDecimal("1.500"), "1.500");
		checkNumber("small double", 1e-7, "0.00000010");

		checkEquals("scale rounds half up", new BigDecimal("2.35"), new JSONEntry(2.345, 2).getNumber());
		checkEquals("scale 0 rounds 2.5 to 3", new BigDecimal("3"), new JSONEntry(2.5, 0).getNumber());
		checkEquals("scale 0 rounds -2.5 to -3", new BigDecimal("-3"), new JSONEntry(-2.5, 0).getNumber());
		checkEquals("scale pads decimals", new BigDecimal("7.000"), new JSONEntry(7, 3).getNumber());

		checkThrows("NaN rejected", IllegalArgumentException.class, () -> new JSONEntry(Double.NaN));
		checkThrows("infinity rejected", IllegalArgumentException.class,
				() -> new JSONEntry(Double.NEGATIVE_INFINITY));
		checkThrows("float NaN rejected", IllegalArgumentException.class, () -> new JSONEntry(Float.NaN));

		/* Numbers are written in plain form, never with an exponent. */
		JSONObject obj = JSONObject.parse("{\"a\":1.5e3,\"b\":-2E-2,\"c\":0.10}");
		checkEquals("exponent written plain", "{\"a\":1500,\"b\":-0.02,\"c\":0.10}", obj.toString());
	}

	/**
	 * String escaping on write and unescaping on parse.
	 */
	private static void testStrings() {
		checkWrite("quote", "a\"b", "\"a\\\"b\"");
		checkWrite("backslash", "a\\b", "\"a\\\\b\"");
		checkWrite("newline", "a\nb", "\"a\\nb\"");
		checkWrite("carriage return", "a\rb", "\"a\\rb\"");
		checkWrite("tab", "a\tb", "\"a\\tb\"");
		checkWrite("backspace", "a\bb", "\"a\\bb\"");
		checkWrite("form feed", "a\fb", "\"a\\fb\"");
		checkWrite("control char", "a\u0001b", "\"a\\u0001b\"");
		checkWrite("slash not escaped", "a/b", "\"a/b\"");
		checkWrite("non ascii kept", "é€", "\"é€\"");
		checkWrite("empty", "", "\"\"");

		checkParsedString("escaped quote", "\\\"", "\"");
		checkParsedString("escaped backslash", "\\\\", "\\");
		checkParsedString("escaped slash", "\\/", "/");
		checkParsedString("escaped controls", "\\b\\f\\n\\r\\t", "\b\f\n\r\t");
		checkParsedString("unicode lower", "\\u00e9", "é");
		checkParsedString("unicode upper", "\\u00E9", "é");
		checkParsedString("surrogate pair", "\\ud83d\\ude00", "😀");
		checkParsedString("empty string", "", "");
		checkParsedString("raw non ascii", "é€", "é€");

		/* Keys are escaped too. */
		JSONObject obj = new JSONObject();
		obj.put("k\"e\\y\n", 1);
		checkEquals("escaped key written", "{\"k\\\"e\\\\y\\n\":1}", obj.toString());
		check("escaped key parsed", JSONObject.parse(obj.toString()).contains("k\"e\\y\n"));

		/* Every char from 0 to 0x7F survives the round trip. */
		StringBuilder all = new StringBuilder();
		for (char c = 0; c < 0x80; c++) {
			all.append(c);
		}
		JSONObject ascii = new JSONObject();
		ascii.put("s", all.toString());
		checkEquals("all ascii chars round trip", all.toString(),
				JSONObject.parse(ascii.toString()).get("s").getString());
	}

	/**
	 * Extended types, written as an object with a single type key.
	 */
	private static void testExtendedTypes() {
		JSONObject obj = new JSONObject();
		obj.put("d", LocalDate.of(2026, 10, 27));
		obj.put("t", LocalTime.of(10, 30, 15, 500_000_000));
		obj.put("ts", LocalDateTime.of(2026, 10, 27, 10, 30));
		obj.put("b", new byte[] { 0, 1, 127, (byte) 0x80, (byte) 0xFF });
		checkEquals("extended written",
				"{\"d\":{\"%dt%\":\"2026-10-27\"},\"t\":{\"%tm%\":\"10:30:15.500\"},"
						+ "\"ts\":{\"%ts%\":\"2026-10-27T10:30\"},\"b\":{\"%bin%\":\"00017f80ff\"}}",
				obj.toString());

		JSONObject back = JSONObject.parse(obj.toString());
		checkEquals("date parsed", LocalDate.of(2026, 10, 27), back.get("d").getDate());
		checkEquals("time parsed", LocalTime.of(10, 30, 15, 500_000_000), back.get("t").getTime());
		checkEquals("timestamp parsed", LocalDateTime.of(2026, 10, 27, 10, 30), back.get("ts").getTimestamp());
		check("binary parsed", Arrays.equals(new byte[] { 0, 1, 127, (byte) 0x80, (byte) 0xFF },
				back.get("b").getBinary()));

		/* Every byte value survives the round trip. */
		byte[] bytes = new byte[256];
		for (int i = 0; i < 256; i++) {
			bytes[i] = (byte) i;
		}
		JSONObject bin = new JSONObject();
		bin.put("b", bytes);
		check("all byte values round trip", Arrays.equals(bytes,
				JSONObject.parse(bin.toString()).get("b").getBinary()));
		check("empty binary round trip", Arrays.equals(new byte[0],
				JSONObject.parse("{\"b\":{\"%bin%\":\"\"}}").get("b").getBinary()));
		check("upper case hex accepted", Arrays.equals(new byte[] { (byte) 0xAB, 0x01 },
				JSONObject.parse("{\"b\":{\"%bin%\":\"AB01\"}}").get("b").getBinary()));

		/* Null extended values. */
		JSONObject nulls = new JSONObject();
		nulls.put("d", (LocalDate) null);
		nulls.put("t", (LocalTime) null);
		nulls.put("ts", (LocalDateTime) null);
		nulls.put("b", (byte[]) null);
		checkEquals("null extended written",
				"{\"d\":{\"%dt%\":null},\"t\":{\"%tm%\":null},\"ts\":{\"%ts%\":null},\"b\":{\"%bin%\":null}}",
				nulls.toString());
		JSONObject nullsBack = JSONObject.parse(nulls.toString());
		check("null date parsed", nullsBack.get("d").isDate() && nullsBack.get("d").isNullValue());
		check("null time parsed", nullsBack.get("t").isTime() && nullsBack.get("t").isNullValue());
		check("null timestamp parsed", nullsBack.get("ts").isTimestamp() && nullsBack.get("ts").isNullValue());
		check("null binary parsed", nullsBack.get("b").isBinary() && nullsBack.get("b").isNullValue());

		/* Objects that look extended but are not stay plain objects. */
		checkPlainObject("invalid date", "{\"a\":{\"%dt%\":\"2026-13-01\"}}");
		checkPlainObject("invalid time", "{\"a\":{\"%tm%\":\"25:00\"}}");
		checkPlainObject("odd length hex", "{\"a\":{\"%bin%\":\"abc\"}}");
		checkPlainObject("non hex chars", "{\"a\":{\"%bin%\":\"zz\"}}");
		checkPlainObject("number value", "{\"a\":{\"%dt%\":5}}");
		checkPlainObject("two keys", "{\"a\":{\"%dt%\":\"2026-01-01\",\"x\":1}}");
		checkPlainObject("unknown key", "{\"a\":{\"%xx%\":\"2026-01-01\"}}");

		/* Extended types inside arrays. */
		JSONArray arr = JSONArray.parse("[{\"%dt%\":\"2026-01-01\"},{\"%tm%\":\"08:00\"}]");
		check("date in array", arr.get(0).isDate());
		check("time in array", arr.get(1).isTime());
	}

	/**
	 * JSONObject put, get, remove, keys and order.
	 */
	private static void testObject() {
		JSONObject obj = new JSONObject();
		check("new object is empty", obj.isEmpty());
		obj.put("b", 1);
		obj.put("a", "x");
		obj.put("c");
		checkEquals("size", 3, obj.size());
		checkEquals("insertion order", List.of("b", "a", "c"), new ArrayList<>(obj.keys()));
		obj.put("b", 2);
		checkEquals("replace keeps position", List.of("b", "a", "c"), new ArrayList<>(obj.keys()));
		checkEquals("replaced value", new BigDecimal(2), obj.get("b").getNumber());
		check("put(key) is NULL", obj.get("c").isNullType());
		check("contains", obj.contains("a"));
		check("not contains", !obj.contains("z"));
		checkEquals("remove returns entry", "x", obj.remove("a").getString());
		checkEquals("remove missing returns null", null, obj.remove("a"));
		checkEquals("size after remove", 2, obj.size());
		checkThrows("get missing key", IllegalArgumentException.class, () -> obj.get("z"));
		checkThrows("null key", IllegalArgumentException.class, () -> obj.put(null, 1));
		checkThrows("null entry", IllegalArgumentException.class, () -> obj.put("k", (JSONEntry) null));

		JSONObject typed = new JSONObject();
		typed.put("o", new JSONObject());
		typed.put("a", new JSONArray());
		typed.put("n", 1.5, 1);
		typed.put("bool", false);
		check("put object", typed.get("o").isObject());
		check("put array", typed.get("a").isArray());
		checkEquals("put number with scale", new BigDecimal("1.5"), typed.get("n").getNumber());
		check("put boolean", !typed.get("bool").getBoolean());
	}

	/**
	 * JSONArray add, insert, set, remove and iteration.
	 */
	private static void testArray() {
		JSONArray arr = new JSONArray();
		arr.add(1);
		arr.add("b");
		arr.add(true);
		arr.add(0, "first");
		arr.set(2, "B");
		checkEquals("array content", "[\"first\",1,\"B\",true]", arr.toString());
		checkEquals("remove returns entry", "first", arr.remove(0).getString());
		checkEquals("size", 3, arr.size());
		int count = 0;
		for (JSONEntry e : arr) {
			check("iterated entry not null", e != null);
			count++;
		}
		checkEquals("iteration count", 3, count);
		checkThrows("get out of range", IndexOutOfBoundsException.class, () -> arr.get(10));
		checkThrows("add null entry", IllegalArgumentException.class, () -> arr.add((JSONEntry) null));
		checkThrows("set null entry", IllegalArgumentException.class, () -> arr.set(0, (JSONEntry) null));

		JSONArray typed = new JSONArray();
		typed.add(new JSONObject());
		typed.add(new JSONArray());
		typed.add(LocalDate.of(2026, 1, 1));
		typed.add(LocalTime.of(1, 2));
		typed.add(LocalDateTime.of(2026, 1, 1, 1, 2));
		typed.add(new byte[] { 1 });
		typed.add(2.555, 2);
		checkEquals("typed array", "[{},[],{\"%dt%\":\"2026-01-01\"},{\"%tm%\":\"01:02\"},"
				+ "{\"%ts%\":\"2026-01-01T01:02\"},{\"%bin%\":\"01\"},2.56]", typed.toString());
	}

	/**
	 * Equality is based on the compact string representation.
	 */
	@SuppressWarnings("unlikely-arg-type")
	private static void testEquals() {
		JSONObject a = JSONObject.parse("{\"x\":1,\"y\":[1,2]}");
		JSONObject b = JSONObject.parse("{ \"x\" : 1 , \"y\" : [ 1 , 2 ] }");
		checkEquals("equal objects", a, b);
		checkEquals("equal hash codes", a.hashCode(), b.hashCode());
		check("key order matters", !a.equals(JSONObject.parse("{\"y\":[1,2],\"x\":1}")));
		check("number scale matters", !JSONObject.parse("{\"x\":1}").equals(JSONObject.parse("{\"x\":1.0}")));
		check("not equal to null", !a.equals(null));
		check("not equal to other class", !a.equals("x"));
		checkEquals("equal arrays", JSONArray.parse("[1,\"a\"]"), JSONArray.parse("[ 1, \"a\" ]"));
	}

	/**
	 * Readable format, maximum tab level and writing to a Writer.
	 */
	private static void testWriter() throws Exception {
		JSONObject obj = JSONObject.parse("{\"a\":1,\"b\":[1,2]}");
		checkEquals("readable format", "{\n   \"a\":1,\n   \"b\":[\n      1,\n      2\n   ]\n}", obj.toString(true));

		JSONWriter limited = new JSONWriter(true);
		limited.setMaxTabLevel(1);
		checkEquals("max tab level", "{\n   \"a\":1,\n   \"b\":[1,2]\n}", limited.toString(obj));

		StringWriter sw = new StringWriter();
		new JSONWriter().write(sw, obj);
		checkEquals("write to Writer", obj.toString(), sw.toString());

		checkEquals("empty object", "{}", new JSONObject().toString());
		checkEquals("empty array", "[]", new JSONArray().toString());
		checkEquals("NULL value", "{\"n\":null}", JSONObject.parse("{\"n\":null}").toString());
		checkEquals("booleans", "[true,false]", JSONArray.parse("[true,false]").toString());
	}

	/**
	 * A document with every type survives write and parse, compact and readable.
	 */
	private static void testRoundTrip() {
		JSONObject obj = buildFullObject();
		for (boolean readable : new boolean[] { false, true }) {
			String text = obj.toString(readable);
			JSONObject back = JSONObject.parse(text);
			checkEquals("round trip equal, readable=" + readable, obj, back);
			checkEquals("same text again, readable=" + readable, text, back.toString(readable));
		}
		JSONArray arr = new JSONArray();
		arr.add(obj);
		arr.add(new JSONArray());
		checkEquals("array round trip", arr, JSONArray.parse(arr.toString(true)));
	}

	/**
	 * Valid inputs, including whitespace, nesting and top level arrays.
	 */
	private static void testParserValid() throws Exception {
		checkEquals("empty source object", 0, JSONObject.parse("").size());
		checkEquals("blank source object", 0, JSONObject.parse(" \t\r\n ").size());
		checkEquals("empty source array", 0, JSONArray.parse("").size());
		checkEquals("empty object", "{}", JSONObject.parse("{}").toString());
		checkEquals("empty nested", "{\"a\":{},\"b\":[]}", JSONObject.parse("{\"a\":{},\"b\":[]}").toString());
		checkEquals("whitespace everywhere", "{\"a\":[1,2],\"b\":null}",
				JSONObject.parse("\t\r\n { \"a\" : [ 1 , 2 ] ,\n\"b\" :\tnull } \n").toString());
		checkEquals("nested arrays", "[[[]],[1,[2]]]", JSONArray.parse("[[[]],[1,[2]]]").toString());
		checkEquals("duplicate key last wins", "{\"a\":2}", JSONObject.parse("{\"a\":1,\"a\":2}").toString());

		checkParsedNumber("zero", "0", "0");
		checkParsedNumber("negative zero", "-0", "0");
		checkParsedNumber("fraction", "-12.50", "-12.50");
		checkParsedNumber("exponent", "1e2", "100");
		checkParsedNumber("exponent plus", "1E+2", "100");
		checkParsedNumber("exponent minus", "25e-1", "2.5");
		checkParsedNumber("huge", "1e400", "1" + "0".repeat(400));

		JSONObject fromReader = JSONObject.parse(new StringReader("{\"a\":true}"));
		check("parse from Reader", fromReader.get("a").getBoolean());
		JSONArray arrFromReader = JSONArray.parse(new StringReader("[false]"));
		check("parse array from Reader", !arrFromReader.get(0).getBoolean());

		JSONParser parser = new JSONParser();
		parser.parse(new StringReader("{\"a\":1}"));
		checkEquals("parser reusable", 2, parser.parse(new StringReader("{\"b\":1,\"c\":2}")).size());
		checkThrows("parser reusable after error", java.io.IOException.class,
				() -> parser.parse(new StringReader("{\"a\":")));
		checkEquals("parser works after error", 1, parser.parse(new StringReader("{\"a\":1}")).size());
	}

	/**
	 * Invalid inputs must be rejected, never silently accepted.
	 */
	private static void testParserInvalid() {
		String[] invalidObjects = {
				/* Structure. */
				"{", "}", "{\"a\":1", "{\"a\":1,}", "{,}", "{\"a\"}", "{\"a\" 1}", "{\"a\":}",
				"{\"a\":,\"b\":1}", "{\"a\":1 \"b\":2}", "{\"a\":1:\"b\":2}", "{\"a\":null null}",
				"{a:1}", "{'a':1}", "{1:1}", "{\"a\":[1}", "{\"a\":[1,]}", "{\"a\":[,1]}", "{\"a\":{]}",
				/* Top level. */
				"[1]", "\"a\"", "1", "{}{}", "{} x", "{\"a\":1} xyz",
				/* Literals. */
				"{\"a\":tru}", "{\"a\":truex}", "{\"a\":nul}", "{\"a\":False}", "{\"a\":NULL}", "{\"a\":undefined}",
				/* Numbers. */
				"{\"a\":01}", "{\"a\":1.}", "{\"a\":.5}", "{\"a\":+1}", "{\"a\":-}", "{\"a\":1e}", "{\"a\":1e+}",
				"{\"a\":1.2.3}", "{\"a\":1x2}", "{\"a\":1 2}", "{\"a\":0x10}", "{\"a\":NaN}", "{\"a\":Infinity}",
				"{\"a\":--1}",
				/* Strings. */
				"{\"a\":\"open}", "{\"a\":\"x\\q\"}", "{\"a\":\"x\\u12g4\"}", "{\"a\":\"x\\u12\"}",
				"{\"a\":\"line\nbreak\"}", "{\"a\":\"tab\there\"}", "{\"a\":'x'}",
				/* Whitespace that JSON does not allow. */
				"\f{}", "{ }",
		};
		for (String s : invalidObjects) {
			checkThrows("rejects object " + visible(s), IllegalArgumentException.class, () -> JSONObject.parse(s));
		}

		String[] invalidArrays = { "[", "]", "[1,]", "[,]", "[1 2]", "{}", "[1]]", "[1] [2]" };
		for (String s : invalidArrays) {
			checkThrows("rejects array " + visible(s), IllegalArgumentException.class, () -> JSONArray.parse(s));
		}
	}

	/**
	 * Format errors report the line and column.
	 */
	private static void testErrorPositions() {
		checkErrorContains("single line", "{\"a\":1 2}", "Expected ',' or '}' at line 1, column 8");
		checkErrorContains("multiple lines", "{\n  \"a\": 1,\n  \"b\" 2\n}", "Expected ':' at line 3, column 7");
		checkErrorContains("invalid number", "{\"a\":01}", "Invalid number '01'");
		checkErrorContains("unterminated string", "{\"a\":\"x", "Unterminated string");
		checkErrorContains("trailing content", "{} x", "Unexpected content after the end");
	}

	/**
	 * Large and deeply nested documents.
	 */
	private static void testStress() {
		JSONArray big = new JSONArray();
		for (int i = 0; i < 10_000; i++) {
			JSONObject item = new JSONObject();
			item.put("id", i);
			item.put("name", "item \"" + i + "\"");
			item.put("value", i * 0.5);
			big.add(item);
		}
		JSONArray bigBack = JSONArray.parse(big.toString());
		checkEquals("10000 items round trip", big, bigBack);
		checkEquals("last item", "item \"9999\"", bigBack.get(9999).getObject().get("name").getString());

		int depth = 500;
		String nested = "[".repeat(depth) + "]".repeat(depth);
		checkEquals("deep nesting round trip", nested, JSONArray.parse(nested).toString());
	}

	/* ---------------------------------------------------------------------------
	 * Helpers to build data.
	 * ------------------------------------------------------------------------ */

	/**
	 * @return An object with every type, nested objects and arrays.
	 */
	private static JSONObject buildFullObject() {
		JSONObject obj = new JSONObject();
		obj.put("string", "quote\" backslash\\ slash/ newline\n tab\t control\u0001 unicodeé€");
		obj.put("int", 42);
		obj.put("negative", -3.5);
		obj.put("big", new BigInteger("123456789012345678901234567890"));
		obj.put("scaled", 1.23456, 2);
		obj.put("true", true);
		obj.put("false", false);
		obj.put("null");
		obj.put("binary", new byte[] { 0, 1, 127, (byte) 0x80, (byte) 0xFF });
		obj.put("date", LocalDate.of(2026, 10, 27));
		obj.put("time", LocalTime.of(10, 30, 15));
		obj.put("timestamp", LocalDateTime.of(2026, 10, 27, 10, 30));
		obj.put("nullDate", (LocalDate) null);
		obj.put("emptyObject", new JSONObject());
		obj.put("emptyArray", new JSONArray());
		JSONObject inner = new JSONObject();
		inner.put("list", new JSONArray());
		inner.put("key \"quoted\"", "value");
		JSONArray arr = new JSONArray();
		arr.add(1);
		arr.add("x");
		arr.add(inner);
		arr.add(LocalTime.of(1, 2));
		obj.put("array", arr);
		return obj;
	}

	/* ---------------------------------------------------------------------------
	 * Check helpers.
	 * ------------------------------------------------------------------------ */

	/**
	 * @param section The section name.
	 * @param block   The tests of the section.
	 */
	private static void run(String section, Block block) {
		System.out.println();
		System.out.println("== " + section);
		try {
			block.run();
		} catch (Throwable exc) {
			fail(section + ": unexpected " + exc);
			exc.printStackTrace(System.out);
		}
	}

	/**
	 * @param name The check name.
	 * @param ok   Whether the check passed.
	 */
	private static void check(String name, boolean ok) {
		if (ok) {
			passed++;
			System.out.println("   ok    " + name);
		} else {
			fail(name);
		}
	}

	/**
	 * @param name     The check name.
	 * @param expected The expected value.
	 * @param actual   The actual value.
	 */
	private static void checkEquals(String name, Object expected, Object actual) {
		if (Objects.equals(expected, actual)) {
			check(name, true);
		} else {
			fail(name + "\n            expected: " + visible(String.valueOf(expected))
					+ "\n            actual:   " + visible(String.valueOf(actual)));
		}
	}

	/**
	 * @param name  The check name.
	 * @param type  The exception type expected.
	 * @param block The code that must throw it.
	 */
	private static void checkThrows(String name, Class<? extends Throwable> type, Block block) {
		try {
			block.run();
			fail(name + ": nothing thrown, expected " + type.getSimpleName());
		} catch (Throwable exc) {
			if (type.isInstance(exc)) {
				check(name, true);
			} else {
				fail(name + ": thrown " + exc + ", expected " + type.getSimpleName());
			}
		}
	}

	/**
	 * @param name     The check name.
	 * @param number   The number to put in an entry.
	 * @param expected The expected written form.
	 */
	private static void checkNumber(String name, Number number, String expected) {
		JSONArray arr = new JSONArray();
		arr.add(number);
		checkEquals(name, "[" + expected + "]", arr.toString());
	}

	/**
	 * @param name     The check name.
	 * @param json     The JSON number text.
	 * @param expected The expected written form after parsing.
	 */
	private static void checkParsedNumber(String name, String json, String expected) {
		checkEquals("number " + name, "[" + expected + "]", JSONArray.parse("[" + json + "]").toString());
	}

	/**
	 * @param name     The check name.
	 * @param value    The string value to write.
	 * @param expected The expected JSON text.
	 */
	private static void checkWrite(String name, String value, String expected) {
		JSONArray arr = new JSONArray();
		arr.add(value);
		checkEquals("write " + name, "[" + expected + "]", arr.toString());
	}

	/**
	 * @param name     The check name.
	 * @param content  The JSON string content, without quotes.
	 * @param expected The expected parsed string.
	 */
	private static void checkParsedString(String name, String content, String expected) {
		checkEquals("parse " + name, expected, JSONArray.parse("[\"" + content + "\"]").get(0).getString());
	}

	/**
	 * @param name The check name.
	 * @param json A JSON object whose key "a" must stay a plain object.
	 */
	private static void checkPlainObject(String name, String json) {
		check("plain object, " + name, JSONObject.parse(json).get("a").isObject());
	}

	/**
	 * @param name     The check name.
	 * @param json     The invalid JSON.
	 * @param expected The text the error message must contain.
	 */
	private static void checkErrorContains(String name, String json, String expected) {
		try {
			JSONObject.parse(json);
			fail("error " + name + ": nothing thrown");
		} catch (IllegalArgumentException exc) {
			if (exc.getMessage().contains(expected)) {
				check("error " + name + ": " + exc.getMessage(), true);
			} else {
				fail("error " + name + ": message '" + exc.getMessage() + "' does not contain '" + expected + "'");
			}
		}
	}

	/**
	 * @param name The check name and failure details.
	 */
	private static void fail(String name) {
		failed.add(name);
		System.out.println("   FAIL  " + name);
	}

	/**
	 * @param s A string.
	 * @return The string with control chars made visible, for messages.
	 */
	private static String visible(String s) {
		StringBuilder b = new StringBuilder();
		for (char c : s.toCharArray()) {
			switch (c) {
				case '\n' -> b.append("\\n");
				case '\r' -> b.append("\\r");
				case '\t' -> b.append("\\t");
				default -> {
					if (c < 0x20 || c == 0xa0) {
						b.append(String.format("\\u%04x", (int) c));
					} else {
						b.append(c);
					}
				}
			}
		}
		return b.toString();
	}
}
