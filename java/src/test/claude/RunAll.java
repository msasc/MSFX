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

import test.claude.db.TestDB;
import test.claude.md.TestMetadata;
import test.claude.query.TestQDML;
import test.claude.query.TestQSelect;
import test.claude.query.TestQSelectRecords;
import test.claude.query.TestQSelectUnion;
import test.claude.sa.TestParser;
import test.claude.sa.TestValidator;
import test.claude.sa.TestWriter;
import test.claude.util.TestFunctions;

/**
 * Runs all the tests of test.claude, printing only the failures and the
 * totals. Run after every set of changes.
 *
 * @author Miquel Sas
 */
public class RunAll {

	/**
	 * Entry point.
	 *
	 * @param args "db" to also run the checks on the local SQL Server, the test
	 *             database; "verbose" to print every check.
	 */
	public static void main(String[] args) {
		boolean db = false;
		boolean verbose = false;
		for (String arg : args) {
			db |= arg.equals("db");
			verbose |= arg.equals("verbose");
		}
		Check.setQuiet(!verbose);
		long start = System.currentTimeMillis();

		TestFunctions.run();
		TestMetadata.run();
		TestParser.run();
		TestValidator.run();
		TestWriter.run(db);
		TestQSelect.run(db);
		TestQSelectUnion.run(db);
		TestQSelectRecords.run(db);
		TestQDML.run(db);
		TestDB.run(db);

		long elapsed = System.currentTimeMillis() - start;
		Check.summary();
		StringBuilder b = new StringBuilder();
		b.append(db ? "With" : "Without");
		b.append(" the database, in ");
		b.append(elapsed);
		b.append(" ms");
		System.out.println(b);
		int status = Check.getFailed() == 0 ? 0 : 1;
		System.exit(status);
	}
}
