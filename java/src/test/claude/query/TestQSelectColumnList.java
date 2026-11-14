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

package test.claude.query;

import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.db.query.QSelect;
import msfx.lib.md.Column;
import msfx.lib.md.ColumnList;
import msfx.lib.md.Domain;
import msfx.lib.md.Table;
import msfx.lib.md.Types;

/**
 * Tests {@link QSelect#getColumnList()}, the columns of the records
 * read by a select, built from the domains of its columns. Nothing is
 * executed.
 *
 * @author Miquel Sas
 */
public class TestQSelectColumnList {

	/**
	 * SQL Server engine.
	 */
	private static final DBEngineSQLServer SQLSERVER = new DBEngineSQLServer();
	/**
	 * Number of checks that failed.
	 */
	private static int failed = 0;
	/**
	 * Number of checks that passed.
	 */
	private static int passed = 0;
	/**
	 * The table SALES.
	 */
	private static Table sales;
	/**
	 * The domain of company codes.
	 */
	private static Domain company;
	/**
	 * The domain of quantities.
	 */
	private static Domain quantity;
	/**
	 * The domain of counts.
	 */
	private static Domain count;

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
	 * Runs an action that must fail with an exception of the expected class,
	 * and prints its message.
	 *
	 * @param title    What is checked.
	 * @param expected The expected class of exception.
	 * @param action   The action.
	 */
	private static void error(String title, Class<? extends Exception> expected, Runnable action) {
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
			result = b.toString();
			ok = expected.isInstance(exc);
		}
		report(ok, title, result, expected.getSimpleName());
	}

	/**
	 * Describes a column list: alias, column name, type, length, decimals and
	 * label of each column, one per line.
	 *
	 * @param list The column list.
	 * @return The description.
	 */
	private static String describe(ColumnList list) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			Column column = list.get(i);
			if (i > 0) {
				b.append("\n");
			}
			b.append(list.getAlias(i));
			b.append(" ");
			b.append(column.getName());
			b.append(" ");
			b.append(column.getType());
			b.append(" ");
			b.append(column.getLength());
			b.append(" ");
			b.append(column.getDecimals());
			b.append(" ");
			b.append(column.getLabel());
			b.append(" ");
			b.append(column.getTable() == null ? "no table" : column.getTable().getName());
		}
		return b.toString();
	}

	/**
	 * Compares the description of a column list with the expected one.
	 *
	 * @param title    What is checked.
	 * @param list     The column list.
	 * @param expected The expected description.
	 */
	private static void check(String title, ColumnList list, String expected) {
		String result = describe(list);
		report(expected.equals(result), title, result, expected);
	}

	/**
	 * Returns a select of SALES S with the company, the quantity and the
	 * number of sales, all with a domain.
	 *
	 * @return The select.
	 */
	private static QSelect select() {
		QSelect select = new QSelect(SQLSERVER);
		select.from(sales, "S");
		select.addColumn("S.CCOMPANY", "CCOMPANY", company);
		select.addColumn("SUM(S.QSALES)", "QSales", quantity);
		select.addColumn("COUNT(*)", "NSALES", count);
		select.groupBy();
		return select;
	}

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {

		sales = new Table("SALES");
		sales.setSchema("dbo");
		sales.columns().add(new Column("CCOMPANY", Types.STRING, 10, -1));
		sales.columns().add(new Column("QSALES", Types.DECIMAL, 16, 2));

		company = new Domain("CCOMPANY", Types.STRING, 10, -1);
		company.setLabel("Company");
		quantity = new Domain("QUANTITY", Types.DECIMAL, 20, 4);
		quantity.setLabel("Quantity");
		count = new Domain("NCOUNT", Types.INTEGER, -1, -1);
		count.setLabel("Count");

		System.out.println("== Column lists");
		QSelect select = select();
		check("All the columns, in the order of the select, named after the aliases", select.getColumnList(), String.join("\n",
				"CCOMPANY CCOMPANY STRING 10 -1 Company no table",
				"QSales QSales DECIMAL 20 4 Quantity no table",
				"NSALES NSALES INTEGER -1 -1 Count no table"));
		ColumnList all = select.getColumnList();
		String shared = all == select.getColumnList() ? "same list" : "another list";
		report(shared.equals("same list"), "The list of the select, shared", shared, "same list");

		all.get(0).setLabel("Changed");
		String labels;
		{
			StringBuilder b = new StringBuilder();
			b.append(select.getColumnList().get(0).getLabel());
			b.append(", ");
			b.append(company.getLabel());
			labels = b.toString();
		}
		report(labels.equals("Changed, Company"), "A change shows in the list, not in the domain", labels, "Changed, Company");

		System.out.println("== Errors");
		error("A column without domain after columns with one", IllegalArgumentException.class, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(sales, "S");
			s.addColumn("S.CCOMPANY", "CCOMPANY", company);
			s.addColumn("S.QSALES", "QSALES");
		});
		error("A column with domain after columns without", IllegalArgumentException.class, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(sales, "S");
			s.addColumn("S.CCOMPANY", "CCOMPANY");
			s.addColumn("S.QSALES", "QSALES", quantity);
		});
		error("Columns without domain", IllegalStateException.class, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(sales, "S");
			s.addColumn("S.CCOMPANY", "CCOMPANY");
			s.addColumn("S.QSALES", "QSALES");
			s.getColumnList();
		});

		select = new QSelect(SQLSERVER);
		select.from(sales, "S");
		select.addColumn("S.CCOMPANY", "CCOMPANY", company);
		String rejected;
		try {
			select.addColumn("S.QSALES", "QSALES");
			rejected = "accepted";
		} catch (IllegalArgumentException exc) {
			rejected = describe(select.getColumnList());
		}
		report(rejected.equals("CCOMPANY CCOMPANY STRING 10 -1 Company no table"), "A rejected column leaves the select as it was",
				rejected, "CCOMPANY CCOMPANY STRING 10 -1 Company no table");
		error("No columns", IllegalStateException.class, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(sales, "S");
			s.getColumnList();
		});

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
