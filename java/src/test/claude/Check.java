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

package test.claude;

/**
 * Checks shared by the tests: each check is counted and printed, the result
 * under its title and, when it fails, the expected result. In quiet mode, set
 * by {@link RunAll}, only the failures are printed, preceded by the test that
 * runs them.
 *
 * @author Miquel Sas
 */
public final class Check {

	/**
	 * An action checked for the exception it throws.
	 */
	@FunctionalInterface
	public interface Action {
		/**
		 * Runs the action.
		 *
		 * @throws Exception If the action fails.
		 */
		void run() throws Exception;
	}

	/**
	 * Number of checks that passed.
	 */
	private static int passed = 0;
	/**
	 * Number of checks that failed.
	 */
	private static int failed = 0;
	/**
	 * True to print only the failures.
	 */
	private static boolean quiet = false;
	/**
	 * The test running, printed before its first failure in quiet mode.
	 */
	private static String test = "";
	/**
	 * True once the test running has printed a failure in quiet mode.
	 */
	private static boolean testShown = false;

	/**
	 * Private constructor, only static methods.
	 */
	private Check() {
	}

	/**
	 * Sets the quiet mode.
	 *
	 * @param quiet True to print only the failures.
	 */
	public static void setQuiet(boolean quiet) {
		Check.quiet = quiet;
	}

	/**
	 * Starts a test, whose name precedes its failures in quiet mode.
	 *
	 * @param name The name of the test.
	 */
	public static void start(String name) {
		test = name;
		testShown = false;
		if (!quiet) {
			StringBuilder b = new StringBuilder();
			b.append("#### ");
			b.append(name);
			System.out.println(b);
		}
	}

	/**
	 * Prints the title of a section of checks, except in quiet mode.
	 *
	 * @param title The title.
	 */
	public static void section(String title) {
		if (!quiet) {
			StringBuilder b = new StringBuilder();
			b.append("== ");
			b.append(title);
			System.out.println(b);
		}
	}

	/**
	 * Counts and prints the result of a check.
	 *
	 * @param ok       Whether the check passed.
	 * @param title    What is checked.
	 * @param result   The result.
	 * @param expected The expected result, printed when the check fails.
	 */
	public static void report(boolean ok, String title, String result, String expected) {
		if (ok) {
			passed++;
		} else {
			failed++;
		}
		if (ok && quiet) {
			return;
		}
		StringBuilder line = new StringBuilder();
		if (!ok && quiet && !testShown) {
			line.append("#### ");
			line.append(test);
			line.append("\n");
			testShown = true;
		}
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
	 * Checks that a result equals the expected one.
	 *
	 * @param title    What is checked.
	 * @param result   The result.
	 * @param expected The expected result.
	 */
	public static void equal(String title, String result, String expected) {
		boolean ok = expected.equals(result);
		report(ok, title, result, expected);
	}

	/**
	 * Checks the SQL of a statement, written by its toString().
	 *
	 * @param title     What is checked.
	 * @param statement The statement.
	 * @param expected  The expected SQL.
	 */
	public static void sql(String title, Object statement, String expected) {
		String result = statement.toString();
		equal(title, result, expected);
	}

	/**
	 * Checks that an action fails with an exception of the expected class.
	 *
	 * @param title    What is checked.
	 * @param expected The expected class of exception.
	 * @param action   The action.
	 */
	public static void error(String title, Class<? extends Exception> expected, Action action) {
		error(title, expected, null, action);
	}

	/**
	 * Checks that an action fails with an exception of the expected class, with
	 * a cause of the expected class.
	 *
	 * @param title         What is checked.
	 * @param expected      The expected class of exception.
	 * @param expectedCause The expected class of the cause, or null not to check
	 *                      it.
	 * @param action        The action.
	 */
	public static void error(String title, Class<? extends Exception> expected, Class<? extends Exception> expectedCause,
			Action action) {
		String result;
		boolean ok;
		try {
			action.run();
			result = "no error";
			ok = false;
		} catch (Exception exc) {
			StringBuilder b = new StringBuilder();
			b.append(exc.getClass().getSimpleName());
			b.append(": ");
			b.append(exc.getMessage());
			Throwable cause = exc.getCause();
			if (cause != null) {
				b.append("\ncause ");
				b.append(cause.getClass().getSimpleName());
				b.append(": ");
				b.append(cause.getMessage());
			}
			result = b.toString();
			ok = expected.isInstance(exc);
			if (expectedCause != null) {
				ok &= expectedCause.isInstance(cause);
			}
		}
		StringBuilder exp = new StringBuilder();
		exp.append(expected.getSimpleName());
		if (expectedCause != null) {
			exp.append(" caused by ");
			exp.append(expectedCause.getSimpleName());
		}
		report(ok, title, result, exp.toString());
	}

	/**
	 * Joins lines with new lines, as statements write their clauses.
	 *
	 * @param lines The lines.
	 * @return The text.
	 */
	public static String lines(String... lines) {
		String text = String.join("\n", lines);
		return text;
	}

	/**
	 * Returns the number of checks that failed.
	 *
	 * @return The number of failures.
	 */
	public static int getFailed() {
		return failed;
	}

	/**
	 * Prints the number of checks passed and failed.
	 */
	public static void summary() {
		StringBuilder b = new StringBuilder();
		b.append("Passed: ");
		b.append(passed);
		b.append(", failed: ");
		b.append(failed);
		System.out.println(b);
	}
}
