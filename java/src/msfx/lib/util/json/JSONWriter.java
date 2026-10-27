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

import msfx.lib.util.Strings;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.HexFormat;
import java.util.Iterator;

/**
 * A writer of JSON objects.
 *
 * @author Miquel Sas
 */
public class JSONWriter {

	/**
	 * Tab size, default is 3.
	 */
	private final int tabSize = 3;
	/**
	 * Tab level.
	 */
	private int tabLevel = 0;
	/**
	 * Maximum tab level.
	 */
	private int maxTabLevel = Integer.MAX_VALUE;
	/**
	 * A boolean that indicates whether the format should be readable.
	 */
	private boolean readable = false;

	/**
	 * Constructor of a writer with non readable format.
	 */
	public JSONWriter() {
	}
	/**
	 * @param readable A boolean that indicates whether the format should be readable.
	 */
	public JSONWriter(boolean readable) {
		this.readable = readable;
	}

	/**
	 * @param maxTabLevel The maximum tab level, deeper levels are written in compact form.
	 */
	public void setMaxTabLevel(int maxTabLevel) {
		this.maxTabLevel = maxTabLevel;
	}

	/**
	 * @param w The writer.
	 * @param o The JSONObject to write.
	 * @throws IOException If an error occurs.
	 */
	public void write(Writer w, JSONObject o) throws IOException {
		boolean readable = this.readable && tabLevel < maxTabLevel;
		w.write("{");
		if (readable) {
			tabLevel++;
		}
		Iterator<String> i = o.keys().iterator();
		while (i.hasNext()) {
			if (readable) {
				w.write("\n" + Strings.repeat(" ", tabLevel * tabSize));
			}
			String key = i.next();
			writeString(w, key);
			w.write(":");
			JSONEntry e = o.get(key);
			write(w, e);
			if (i.hasNext()) w.write(",");
		}
		if (readable) {
			tabLevel--;
			w.write("\n" + Strings.repeat(" ", tabLevel * tabSize));
		}
		w.write("}");
	}
	/**
	 * @param w The writer.
	 * @param a The JSONArray to write.
	 * @throws IOException If an error occurs.
	 */
	public void write(Writer w, JSONArray a) throws IOException {
		boolean readable = this.readable && tabLevel < maxTabLevel;
		w.write("[");
		if (readable) {
			tabLevel++;
		}
		Iterator<JSONEntry> i = a.iterator();
		while (i.hasNext()) {
			if (readable) {
				w.write("\n" + Strings.repeat(" ", tabLevel * tabSize));
			}
			write(w, i.next());
			if (i.hasNext()) w.write(",");
		}
		if (readable) {
			tabLevel--;
			w.write("\n" + Strings.repeat(" ", tabLevel * tabSize));
		}
		w.write("]");
	}
	/**
	 * @param w The writer.
	 * @param e The JSONEntry to write.
	 * @throws IOException If an error occurs.
	 */
	public void write(Writer w, JSONEntry e) throws IOException {

		/* Standard types. */
		if (e.isObject()) {
			write(w, e.getObject());
			return;
		}
		if (e.isArray()) {
			write(w, e.getArray());
			return;
		}
		if (e.isString()) {
			writeString(w, e.getString());
			return;
		}
		if (e.isNumber()) {
			w.write(e.getNumber().toPlainString());
			return;
		}
		if (e.isBoolean()) {
			w.write(Boolean.valueOf(e.getBoolean()).toString());
			return;
		}
		if (e.isNullType()) {
			w.write("null");
			return;
		}

		/*
		 * Extended types, written as an object with the type key, for instance
		 * {"%dt%":"2026-01-01"}, or {"%dt%":null} for a null value.
		 */
		if (e.type.isExtended()) {
			w.write("{\"");
			w.write(e.type.key);
			w.write("\":");
			if (e.value == null) {
				w.write("null");
			} else if (e.isBinary()) {
				w.write("\"" + HexFormat.of().formatHex(e.getBinary()) + "\"");
			} else {
				w.write("\"" + e.value + "\"");
			}
			w.write("}");
		}
	}

	/**
	 * @param w The writer.
	 * @param s The string to write, quoted and escaped as JSON requires.
	 * @throws IOException If an error occurs.
	 */
	private void writeString(Writer w, String s) throws IOException {
		w.write('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> w.write("\\\"");
				case '\\' -> w.write("\\\\");
				case '\b' -> w.write("\\b");
				case '\f' -> w.write("\\f");
				case '\n' -> w.write("\\n");
				case '\r' -> w.write("\\r");
				case '\t' -> w.write("\\t");
				default -> {
					if (c < 0x20) {
						w.write(String.format("\\u%04x", (int) c));
					} else {
						w.write(c);
					}
				}
			}
		}
		w.write('"');
	}

	/**
	 * @param arr The JSON array.
	 * @return The string representation.
	 */
	public String toString(JSONArray arr) {
		try {
			StringWriter w = new StringWriter();
			write(w, arr);
			return w.toString();
		} catch (IOException exc) {
			/* A StringWriter never throws, but just in case. */
			throw new UncheckedIOException(exc);
		}
	}

	/**
	 * @param obj The JSON object.
	 * @return The string representation.
	 */
	public String toString(JSONObject obj) {
		try {
			StringWriter w = new StringWriter();
			write(w, obj);
			return w.toString();
		} catch (IOException exc) {
			/* A StringWriter never throws, but just in case. */
			throw new UncheckedIOException(exc);
		}
	}
}
