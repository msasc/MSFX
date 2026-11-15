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

import java.sql.SQLException;
import java.util.List;

import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.db.query.QSelect;
import msfx.lib.md.Catalog;
import msfx.lib.md.Column;
import msfx.lib.md.ColumnList;
import msfx.lib.md.Domain;
import msfx.lib.md.Record;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import test.db.CatalogCMA;

/**
 * Tests the columns of a select given as columns: a column of a table, for an
 * expression that is that very column, makes the records read refer to the
 * table; a column of no table only defines the value. Also tests the copy
 * constructor {@link Column#Column(Column)} used for them. With "db", reads
 * TPIECES on the local SQL Server.
 *
 * @author Miquel Sas
 */
public class TestQSelectTableColumns {

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
	 * The table TPIECES.
	 */
	private static Table pieces;
	/**
	 * The table UNITS.
	 */
	private static Table units;

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
		line.append(": ");
		line.append(result);
		if (!ok) {
			line.append(", expected ");
			line.append(expected);
		}
		System.out.println(line);
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
	 * Describes a record column: alias, name, table and label.
	 *
	 * @param list  The column list.
	 * @param index The index.
	 * @return The description.
	 */
	private static String describe(ColumnList list, int index) {
		Column column = list.get(index);
		StringBuilder b = new StringBuilder();
		b.append(list.getAlias(index));
		b.append(" ");
		b.append(column.getName());
		b.append(" ");
		b.append(column.getTable() == null ? "no table" : column.getTable().getName());
		b.append(" ");
		b.append(column.getLabel());
		return b.toString();
	}

	/**
	 * Returns a select from TPIECES P.
	 *
	 * @return The select.
	 */
	private static QSelect fromPieces() {
		QSelect select = new QSelect(SQLSERVER);
		select.from(pieces, "P");
		return select;
	}

	/**
	 * Entry point.
	 *
	 * @param args Pass "db" to also read TPIECES on the local SQL Server.
	 */
	public static void main(String[] args) {
		Catalog catalog = new Catalog();
		catalog.addProvider(new CatalogCMA());
		pieces = catalog.getTable(CatalogCMA.SCHEMA, "TPIECES");
		units = catalog.getTable(CatalogCMA.SCHEMA, "UNITS");
		Column cptype = pieces.columns().get("CPTYPE");
		Column dptype = pieces.columns().get("DPTYPE");
		Column spare = pieces.columns().get("ISPAREPART");

		System.out.println("== Copy of a column that keeps its table");
		Column copy = new Column(spare);
		copy.setLabel("Changed");
		StringBuilder copyResult = new StringBuilder();
		copyResult.append(copy.getTable().getName());
		copyResult.append(", ");
		copyResult.append(copy.equals(spare) ? "equal" : "different");
		copyResult.append(", ");
		copyResult.append(copy != spare ? "another object" : "same object");
		copyResult.append(", original label ");
		copyResult.append(spare.getLabel());
		copyResult.append(", in the table list ");
		copyResult.append(pieces.columns().get("ISPAREPART") == spare ? "the original" : "the copy");
		check("Copy", copyResult.toString(), "TPIECES, equal, another object, original label Spare part Y/N, in the table list the original");

		System.out.println("== Record columns");
		QSelect select = fromPieces();
		select.addColumn("P.CPTYPE", "CPTYPE", cptype);
		select.addColumn("(P.DPTYPE)", "DESCRIPTION", dptype);
		select.addColumn("UPPER(P.DPTYPE)", "UPPER_DESC", new Column("UPPER_DESC", dptype.getDomain()));
		ColumnList list = select.getColumnList();
		check("Table column, same alias", describe(list, 0), "CPTYPE CPTYPE TPIECES Type of piece code");
		check("Table column under another alias, in parentheses", describe(list, 1),
				"DESCRIPTION DPTYPE TPIECES Type of piece description");
		check("Free column for an expression", describe(list, 2), "UPPER_DESC UPPER_DESC no table Type of piece description");
		check("A copy kept, equal to the table column",
				String.valueOf(list.get(0) != cptype && list.get(0).equals(cptype)), "true");
		list.get(0).setLabel("Piece type");
		check("Label changed in the select, not in the table", cptype.getLabel(), "Type of piece code");
		check("SQL unchanged", select.toString().split("\n")[0],
				"SELECT [P].[CPTYPE] AS [CPTYPE], ([P].[DPTYPE]) AS [DESCRIPTION], UPPER([P].[DPTYPE]) AS [UPPER_DESC]");

		System.out.println("== Errors");
		error("Table column for an aggregate", IllegalArgumentException.class,
				() -> fromPieces().addColumn("MAX(P.DPTYPE)", "DPTYPE", dptype));
		error("Table column for another column", IllegalArgumentException.class,
				() -> fromPieces().addColumn("P.CPTYPE", "CPTYPE", dptype));
		error("Table column for a column of another table", IllegalArgumentException.class, () -> {
			QSelect s = fromPieces();
			s.leftJoin(units, "U", "P.CUNIT = U.CUNIT");
			s.addColumn("U.CUNIT", "CUNIT", pieces.columns().get("CUNIT"));
		});
		error("Table column for a column of a union wrap", IllegalArgumentException.class, () -> {
			QSelect inner = fromPieces();
			inner.addColumn("P.CPTYPE", "CPTYPE", cptype);
			QSelect s = new QSelect(SQLSERVER);
			s.wrapUnion("Q", inner);
			s.addColumn("Q.CPTYPE", "CPTYPE", cptype);
		});
		error("A column after columns without", IllegalArgumentException.class, () -> {
			QSelect s = fromPieces();
			s.addColumn("P.CPTYPE", "CPTYPE");
			s.addColumn("P.DPTYPE", "DPTYPE", dptype);
		});
		String mixed;
		try {
			QSelect s = fromPieces();
			s.addColumn("P.CPTYPE", "CPTYPE", cptype);
			s.addColumn("P.DPTYPE", "DPTYPE", new Domain("DPTYPE", Types.STRING, 120, -1));
			mixed = String.valueOf(s.getColumnList().size());
		} catch (RuntimeException exc) {
			mixed = exc.getMessage();
		}
		check("Columns and domains together", mixed, "2");

		if (args.length > 0 && args[0].equals("db")) {
			System.out.println("== Read on SQL Server");
			SQLSERVER.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");
			QSelect read = fromPieces();
			for (Column column : pieces.columns()) {
				StringBuilder expr = new StringBuilder();
				expr.append("P.");
				expr.append(column.getName());
				read.addColumn(expr.toString(), column.getName(), column);
			}
			try {
				List<Record> records = read.readList();
				boolean allOfTable = true;
				for (Record record : records) {
					for (Column column : record.columns()) {
						allOfTable &= pieces.equals(column.getTable());
					}
				}
				StringBuilder readResult = new StringBuilder();
				readResult.append(records.size());
				readResult.append(" records, ");
				readResult.append(allOfTable ? "all columns of TPIECES" : "SOME COLUMNS NOT OF TPIECES");
				check("Records read refer to their table", readResult.toString(), "771 records, all columns of TPIECES");
			} catch (SQLException exc) {
				check("Read", exc.getMessage(), "no error");
			}
		}

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
