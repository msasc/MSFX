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

package test.claude.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import msfx.lib.util.function.Consumer;
import msfx.lib.util.function.Function;

/**
 * Tests {@link Consumer} and {@link Function}: the exception type parameter is
 * inferred as RuntimeException when the lambda throws no checked exception, so
 * the caller has nothing to catch, and as the checked exception thrown
 * otherwise. That this class compiles is part of the test: the calls without
 * a checked exception have no try block.
 *
 * @author Miquel Sas
 */
public class TestFunctions {

	/**
	 * Number of checks that failed.
	 */
	private static int failed = 0;
	/**
	 * Number of checks that passed.
	 */
	private static int passed = 0;

	/**
	 * Prints the result of a check and counts it.
	 *
	 * @param ok       Whether the check passed.
	 * @param title    What is checked.
	 * @param result   The result.
	 * @param expected The expected result.
	 */
	private static void report(boolean ok, String title, String result, String expected) {
		if (ok) {
			passed++;
		} else {
			failed++;
		}
		StringBuilder line = new StringBuilder();
		line.append(ok ? "   ok    " : "   FAIL  ");
		line.append(title);
		line.append("\n");
		line.append(result.indent(9));
		if (!ok) {
			line.append("         expected:\n");
			line.append(expected.indent(9));
		}
		System.out.print(line);
	}

	/**
	 * Calls a consumer for each item, as a reader would for each record.
	 *
	 * @param <E>    The exception thrown by the consumer.
	 * @param items  The items.
	 * @param action The consumer of the index and the item.
	 * @throws E If the consumer throws it.
	 */
	private static <E extends Exception> void forEach(List<String> items, Consumer.P2<Integer, String, E> action) throws E {
		for (int i = 0; i < items.size(); i++) {
			action.call(i, items.get(i));
		}
	}

	/**
	 * Maps each item with a function.
	 *
	 * @param <R>    The type of the result.
	 * @param <E>    The exception thrown by the function.
	 * @param items  The items.
	 * @param mapper The function.
	 * @return The results.
	 * @throws E If the function throws it.
	 */
	private static <R, E extends Exception> List<R> map(List<String> items, Function.P1<String, R, E> mapper) throws E {
		List<R> results = new ArrayList<>();
		for (String item : items) {
			results.add(mapper.call(item));
		}
		return results;
	}

	/**
	 * Returns the length of a text, failing with a checked exception if it is
	 * empty.
	 *
	 * @param text The text.
	 * @return The length.
	 * @throws IOException If the text is empty.
	 */
	private static int lengthOf(String text) throws IOException {
		if (text.isEmpty()) {
			throw new IOException("Empty text");
		}
		return text.length();
	}

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {

		List<String> items = List.of("LCZ", "RSA", "ABCD");

		System.out.println("== Without checked exceptions: no try block");
		StringBuilder visited = new StringBuilder();
		forEach(items, (index, item) -> {
			visited.append(index);
			visited.append(item);
		});
		report(visited.toString().equals("0LCZ1RSA2ABCD"), "Consumer.P2", visited.toString(), "0LCZ1RSA2ABCD");

		List<Integer> lengths = map(items, item -> item.length());
		report(lengths.toString().equals("[3, 3, 4]"), "Function.P1", lengths.toString(), "[3, 3, 4]");

		Function.P6<Integer, Integer, Integer, Integer, Integer, Integer, Integer, RuntimeException> sum;
		sum = (a, b, c, d, e, f) -> a + b + c + d + e + f;
		String total = String.valueOf(sum.call(1, 2, 3, 4, 5, 6));
		report(total.equals("21"), "Function.P6 in a variable, E given as RuntimeException", total, "21");

		System.out.println("== With a checked exception: inferred and caught");
		String result;
		try {
			result = map(items, item -> lengthOf(item)).toString();
		} catch (IOException exc) {
			result = exc.getMessage();
		}
		report(result.equals("[3, 3, 4]"), "Function.P1 throwing IOException, no error", result, "[3, 3, 4]");

		try {
			result = map(List.of("LCZ", ""), item -> lengthOf(item)).toString();
		} catch (IOException exc) {
			result = exc.getMessage();
		}
		report(result.equals("Empty text"), "Function.P1 throwing IOException, caught as IOException", result, "Empty text");

		Consumer.P0<IOException> failing = () -> {
			throw new IOException("Failed");
		};
		try {
			failing.call();
			result = "no error";
		} catch (IOException exc) {
			result = exc.getMessage();
		}
		report(result.equals("Failed"), "Consumer.P0 in a variable, E given as IOException", result, "Failed");

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
