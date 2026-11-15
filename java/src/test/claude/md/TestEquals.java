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

package test.claude.md;

import java.util.HashSet;
import java.util.Set;

import msfx.lib.md.Column;
import msfx.lib.md.DataType;
import msfx.lib.md.Domain;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import msfx.lib.util.Copier;

/**
 * Tests the equality of {@link DataType}, {@link Domain} and {@link Column}:
 * by value of their database structure (name ignoring case, type, length,
 * decimals and, for columns, the table), not by reference, and with hash codes
 * consistent with it.
 *
 * @author Miquel Sas
 */
public class TestEquals {

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
	 * @param title    What is checked.
	 * @param result   The result.
	 * @param expected The expected result.
	 */
	private static void check(String title, boolean result, boolean expected) {
		boolean ok = result == expected;
		if (ok) {
			passed++;
		} else {
			failed++;
		}
		StringBuilder line = new StringBuilder();
		line.append(ok ? "   ok    " : "   FAIL  ");
		line.append(title);
		line.append(": ");
		line.append(result);
		System.out.println(line);
	}

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {

		System.out.println("== DataType (record)");
		DataType dt1 = new DataType(Types.STRING, 10, -1);
		DataType dt2 = new DataType(Types.STRING, 10, -1);
		DataType dt3 = new DataType(Types.STRING, 20, -1);
		check("Same type, length and decimals", dt1.equals(dt2) && dt1.hashCode() == dt2.hashCode(), true);
		check("Another length", dt1.equals(dt3), false);

		System.out.println("== Domain");
		Domain d1 = new Domain("CCOMPANY", Types.STRING, 10, -1);
		Domain d2 = new Domain("ccompany", Types.STRING, 10, -1);
		d2.setLabel("Company");
		d2.setHeader("Comp");
		Domain d3 = new Domain("CCOMPANY", Types.STRING, 12, -1);
		Domain d4 = new Domain("CBUSINESS", Types.STRING, 10, -1);
		check("Name ignoring case, other properties not compared", d1.equals(d2), true);
		check("Hash codes of equal domains", d1.hashCode() == d2.hashCode(), true);
		check("Another length", d1.equals(d3), false);
		check("Another name", d1.equals(d4), false);
		Domain d1Copy = new Copier().copy(d1);
		check("A copy", d1.equals(d1Copy) && d1 != d1Copy, true);

		System.out.println("== Column");
		Column c1 = new Column("QSALES", Types.DECIMAL, 16, 2);
		Column c2 = new Column("qsales", Types.DECIMAL, 16, 2);
		c2.setLabel("Sales");
		Column c3 = new Column("QSALES", Types.DECIMAL, 16, 4);
		check("Without table, name ignoring case, label not compared", c1.equals(c2), true);
		check("Hash codes of equal columns", c1.hashCode() == c2.hashCode(), true);
		check("Other decimals", c1.equals(c3), false);

		Table sales = new Table("SALES");
		Set<Column> set = new HashSet<>();
		set.add(c1);
		int hashBefore = c1.hashCode();
		sales.columns().add(c1);
		check("Hash code unchanged when added to a table", c1.hashCode() == hashBefore, true);
		check("Still found in a hash set after being added to a table", set.contains(c1), true);
		check("A column in a table and one without", c1.equals(c2), false);

		Table other = new Table("SALES_OLD");
		other.columns().add(c2);
		check("Columns of two different tables (identity until Table.equals)", c1.equals(c2), false);

		Column c4 = new Column("QSALES", Types.DECIMAL, 16, 2);
		Column c5 = new Column("QSALES", Types.DECIMAL, 16, 2);
		Table t = new Table("T");
		t.columns().add(c4);
		Column c4Copy = new Copier().copy(c4);
		check("A copy belongs to no table, so it differs", c4.equals(c4Copy), false);
		check("Two columns without table, same structure", c5.equals(c4Copy), true);

		Domain fromColumn = c1.getDomain();
		Column fromDomain = new Column(fromColumn);
		check("Column from the domain of a column, no table", fromDomain.equals(c2), false);
		check("Column from the domain of a column, compared to a free one", fromDomain.equals(c5), true);

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
