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

import msfx.lib.md.Column;
import msfx.lib.md.Domain;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import msfx.lib.md.Value;

/**
 * Tests {@link Column#getDomain()}: a new domain with the definition of the
 * column, only the values explicitly set, and the same data type; and a column
 * created from it under another name.
 *
 * @author Miquel Sas
 */
public class TestColumnDomain {

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
	private static void check(String title, String result, String expected) {
		boolean ok = expected.equals(result);
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
	 * Describes a domain: name, data type, header, label, description, possible
	 * values and default value, as its getters return them.
	 *
	 * @param domain The domain.
	 * @return The description.
	 */
	private static String describe(Domain domain) {
		StringBuilder b = new StringBuilder();
		b.append(domain.getName());
		b.append(" ");
		b.append(domain.getDataType());
		b.append("\nheader ");
		b.append(domain.getHeader());
		b.append(", label ");
		b.append(domain.getLabel());
		b.append(", description ");
		b.append(domain.getDescription());
		b.append("\npossible ");
		b.append(domain.getPossibleValues());
		b.append(", default ");
		b.append(domain.getDefaultValue());
		return b.toString();
	}

	/**
	 * Describes a column: name, data type, header, label, description, possible
	 * values, default value and nullable flag, as its getters return them.
	 *
	 * @param column The column.
	 * @return The description.
	 */
	private static String describe(Column column) {
		StringBuilder b = new StringBuilder();
		b.append(column.getName());
		b.append(" ");
		b.append(column.getDataType());
		b.append("\nheader ");
		b.append(column.getHeader());
		b.append(", label ");
		b.append(column.getLabel());
		b.append(", description ");
		b.append(column.getDescription());
		b.append("\npossible ");
		b.append(column.getPossibleValues());
		b.append(", default ");
		b.append(column.getDefaultValue());
		b.append(", nullable ");
		b.append(column.isNullable());
		b.append(", table ");
		b.append(column.getTable() == null ? "none" : column.getTable().getName());
		return b.toString();
	}

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {

		Table articles = new Table("ARTICLES");
		Column status = new Column("CSTATUS", Types.STRING, 1, -1);
		status.setLabel("Status");
		status.setPossibleValue(new Value("A"), "Active");
		status.setPossibleValue(new Value("B"), "Blocked");
		status.setDefaultValue(new Value("B"));
		articles.columns().add(status);

		Column quantity = new Column("QSALES", Types.DECIMAL, 16, 2);
		articles.columns().add(quantity);

		Column created = new Column("DCREATED", Types.DATE, -1, -1);
		created.setNullable(false);
		articles.columns().add(created);

		System.out.println("== Domain of a column");
		Domain statusDomain = status.getDomain();
		check("Explicit values only: no header fallback, no description fallback", describe(statusDomain), String.join("\n",
				"CSTATUS DataType[type=STRING, length=1, decimals=-1]",
				"header null, label Status, description null",
				"possible {A=Active, B=Blocked}, default B"));
		check("The data type is shared", String.valueOf(statusDomain.getDataType() == status.getDataType()), "true");

		Domain quantityDomain = quantity.getDomain();
		check("A plain column", describe(quantityDomain), String.join("\n",
				"QSALES DataType[type=DECIMAL, length=16, decimals=2]",
				"header null, label null, description null",
				"possible {}, default null"));

		Domain createdDomain = created.getDomain();
		Column createdCopy = new Column("DSTART", createdDomain);
		check("No nullable flag: a column from the domain is nullable as its type", describe(createdCopy), String.join("\n",
				"DSTART DataType[type=DATE, length=-1, decimals=-1]",
				"header DSTART, label DSTART, description DSTART",
				"possible {}, default null, nullable true, table none"));

		System.out.println("== Column from the domain of a column");
		Column alias = new Column("CSTATE", statusDomain);
		check("Same definition under another name, fallbacks to the new name", describe(alias), String.join("\n",
				"CSTATE DataType[type=STRING, length=1, decimals=-1]",
				"header CSTATE, label Status, description Status",
				"possible {A=Active, B=Blocked}, default B, nullable false, table none"));

		System.out.println("== A new domain on each call");
		Domain first = status.getDomain();
		status.setHeader("St");
		Domain second = status.getDomain();
		StringBuilder headers = new StringBuilder();
		headers.append(first.getHeader());
		headers.append(", ");
		headers.append(second.getHeader());
		headers.append(", ");
		headers.append(first == second ? "same domain" : "new domain");
		check("The current definition of the column", headers.toString(), "null, St, new domain");

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
