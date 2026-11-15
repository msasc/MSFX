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

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBEngine;
import msfx.lib.db.DBStatement;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.db.query.QInsert;
import msfx.lib.db.query.QSelect;
import msfx.lib.md.Catalog;
import msfx.lib.md.Column;
import msfx.lib.md.ColumnList;
import msfx.lib.md.Index;
import msfx.lib.md.Record;
import msfx.lib.md.Table;
import msfx.lib.md.Value;
import test.db.CatalogCMA;

/**
 * Tests {@link QInsert#execute(Record)}: the table and the columns taken from
 * the first record, the checks of the records, and the reuse of the insert for
 * records of the same structure. With "db", on the local SQL Server, the test
 * database: the records of TPIECES are copied into TPIECES_TMP, and the records
 * read from TPIECES_TMP with its own columns are written back to it after a
 * written DELETE.
 *
 * @author Miquel Sas
 */
public class TestQInsertRecord {

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
	 * The table TPIECES of the CMA catalog.
	 */
	private static Table pieces;
	/**
	 * The table TPIECES_TMP, a copy of TPIECES with its primary key CPTYPE.
	 */
	private static Table piecesTmp;

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
	 * An action that may throw an SQL exception.
	 */
	@FunctionalInterface
	private interface Action {
		/**
		 * Runs the action.
		 *
		 * @throws SQLException If a database access error occurs.
		 */
		void run() throws SQLException;
	}

	/**
	 * Runs an action that must fail with an exception of the expected class,
	 * and prints its message.
	 *
	 * @param title    What is checked.
	 * @param expected The expected class of exception.
	 * @param action   The action.
	 */
	private static void error(String title, Class<? extends Exception> expected, Action action) {
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
		line.append("\n");
		line.append(result.indent(9));
		System.out.print(line);
	}

	/**
	 * Returns a column list with columns of tables, each under its name.
	 *
	 * @param columns The columns.
	 * @return The column list.
	 */
	private static ColumnList list(Column... columns) {
		ColumnList list = new ColumnList();
		for (Column column : columns) {
			list.add(column.getName(), column);
		}
		return list;
	}

	/**
	 * Returns a record of a column list, with a string value per column.
	 *
	 * @param list The column list.
	 * @return The record.
	 */
	private static Record record(ColumnList list) {
		Value[] values = new Value[list.size()];
		for (int i = 0; i < values.length; i++) {
			values[i] = new Value("X");
		}
		return new Record(list, values);
	}

	/**
	 * Returns the values of a record as an array.
	 *
	 * @param record The record.
	 * @return The values.
	 */
	private static Value[] values(Record record) {
		Value[] values = new Value[record.size()];
		for (int i = 0; i < values.length; i++) {
			values[i] = record.getValue(i);
		}
		return values;
	}

	/**
	 * Returns a select of all the columns of a table, ordered by CPTYPE: with
	 * the domains of the columns, or with the columns themselves, so that the
	 * records refer to the table.
	 *
	 * @param db           The database engine.
	 * @param table        The table.
	 * @param tableColumns True to give the columns, false their domains.
	 * @return The select.
	 */
	private static QSelect selectAll(DBEngine db, Table table, boolean tableColumns) {
		QSelect select = new QSelect(db);
		select.from(table, "P");
		for (Column column : table.columns()) {
			StringBuilder expr = new StringBuilder();
			expr.append("P.");
			expr.append(column.getName());
			if (tableColumns) {
				select.addColumn(expr.toString(), column.getName(), column);
			} else {
				select.addColumn(expr.toString(), column.getName(), column.getDomain());
			}
		}
		select.orderBy("CPTYPE");
		return select;
	}

	/**
	 * Describes records, one per line, the values separated by a vertical bar.
	 *
	 * @param records The records.
	 * @return The description.
	 */
	private static String describe(List<Record> records) {
		StringBuilder b = new StringBuilder();
		for (Record record : records) {
			for (int i = 0; i < record.size(); i++) {
				if (i > 0) {
					b.append("|");
				}
				b.append(record.getValue(i));
			}
			b.append("\n");
		}
		return b.toString();
	}

	/**
	 * Executes a written statement that returns no rows.
	 *
	 * @param sql The statement.
	 * @throws SQLException If a database access error occurs.
	 */
	private static void execute(String sql) throws SQLException {
		try (DBConnection cn = SQLSERVER.getConnection(); DBStatement st = cn.createStatement()) {
			st.executeUpdate(sql);
		}
	}

	/**
	 * Entry point.
	 *
	 * @param args Pass "db" to also insert records on the local SQL Server.
	 */
	public static void main(String[] args) {

		Catalog catalog = new Catalog();
		catalog.addProvider(new CatalogCMA());
		pieces = catalog.getTable(CatalogCMA.SCHEMA, "TPIECES");
		piecesTmp = new Table("TPIECES_TMP");
		piecesTmp.setSchema(CatalogCMA.SCHEMA);
		for (Column column : pieces.columns()) {
			Column copy = new Column(column.getName(), column.getDomain());
			piecesTmp.columns().add(copy);
		}
		Index primaryKey = new Index(piecesTmp);
		primaryKey.addColumns("CPTYPE");
		piecesTmp.setPrimaryKey(primaryKey);

		Column tmpType = piecesTmp.columns().get("CPTYPE");
		Column tmpDesc = piecesTmp.columns().get("DPTYPE");

		System.out.println("== Checks of the records");
		error("Insert built by hand", IllegalStateException.class, () -> {
			QInsert insert = new QInsert(SQLSERVER);
			insert.into(piecesTmp);
			insert.columns();
			insert.execute(record(list(tmpType, tmpDesc)));
		});
		error("A record without columns", IllegalArgumentException.class,
				() -> new QInsert(SQLSERVER).execute(new Record(new ColumnList(), new Value[0])));
		QSelect free = selectAll(SQLSERVER, pieces, false);
		error("Columns of no table, as read with domains", IllegalArgumentException.class,
				() -> new QInsert(SQLSERVER).execute(record(free.getColumnList())));
		error("Columns of two tables", IllegalArgumentException.class,
				() -> new QInsert(SQLSERVER).execute(record(list(tmpType, pieces.columns().get("DPTYPE")))));
		error("Without the primary key column", IllegalArgumentException.class,
				() -> new QInsert(SQLSERVER).execute(record(list(tmpDesc))));
		ColumnList repeated = new ColumnList();
		repeated.add("CPTYPE", tmpType);
		repeated.add("TYPE_AGAIN", tmpType);
		error("A column repeated under two aliases", IllegalArgumentException.class,
				() -> new QInsert(SQLSERVER).execute(record(repeated)));

		if (args.length > 0 && args[0].equals("db")) {
			System.out.println("== Run on SQL Server");
			SQLSERVER.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");
			try {
				execute("DROP TABLE IF EXISTS EXPLMARG_QLT.TPIECES_TMP");
				execute("SELECT * INTO EXPLMARG_QLT.TPIECES_TMP FROM EXPLMARG_QLT.TPIECES WHERE 1 = 0");

				List<Record> source = selectAll(SQLSERVER, pieces, false).readList();
				QInsert copyInsert = new QInsert(SQLSERVER);
				ColumnList tmpColumns = piecesTmp.columns();
				int inserted = 0;
				long start = System.currentTimeMillis();
				for (Record record : source) {
					Record tmpRecord = new Record(tmpColumns, values(record));
					inserted += copyInsert.execute(tmpRecord);
				}
				long elapsed = System.currentTimeMillis() - start;
				List<Record> copied = selectAll(SQLSERVER, piecesTmp, false).readList();
				StringBuilder copyResult = new StringBuilder();
				copyResult.append(inserted);
				copyResult.append(" records inserted with one insert, ");
				copyResult.append(describe(source).equals(describe(copied)) ? "same values" : "DIFFERENT values");
				check("Copy of TPIECES into TPIECES_TMP, records sharing a column list", copyResult.toString(),
						"771 records inserted with one insert, same values");
				StringBuilder time = new StringBuilder();
				time.append(elapsed);
				time.append(" ms");
				System.out.println(time.toString().indent(9));

				Value[] sameValues = values(source.get(0));
				sameValues[0] = new Value("ZZ_SAME");
				ColumnList sameList = list(piecesTmp.columns().columns().toArray(new Column[0]));
				int sameRows = copyInsert.execute(new Record(sameList, sameValues));
				check("Same structure on another column list, accepted", String.valueOf(sameRows), "1");
				execute("DELETE FROM EXPLMARG_QLT.TPIECES_TMP WHERE CPTYPE = 'ZZ_SAME'");
				error("A record with other columns", IllegalArgumentException.class,
						() -> copyInsert.execute(record(list(tmpType, tmpDesc))));
				error("Values set by hand on an insert used with records", IllegalStateException.class,
						() -> copyInsert.values(new Value("X")));

				List<Record> own = selectAll(SQLSERVER, piecesTmp, true).readList();
				execute("DELETE FROM EXPLMARG_QLT.TPIECES_TMP");
				QInsert backInsert = new QInsert(SQLSERVER);
				int written = 0;
				for (Record record : own) {
					written += backInsert.execute(record);
				}
				List<Record> back = selectAll(SQLSERVER, piecesTmp, false).readList();
				StringBuilder backResult = new StringBuilder();
				backResult.append(written);
				backResult.append(" records written back, ");
				backResult.append(describe(source).equals(describe(back)) ? "same values" : "DIFFERENT values");
				check("Records read with the table columns, written back after a DELETE", backResult.toString(),
						"771 records written back, same values");

				execute("DELETE FROM EXPLMARG_QLT.TPIECES_TMP WHERE CPTYPE = 'ZZ_ONE'");
				Value[] one = values(own.get(0));
				one[0] = new Value("ZZ_ONE");
				int single = new QInsert(SQLSERVER).execute(new Record(own.get(0).columns(), one));
				check("A single record in one line", String.valueOf(single), "1");
			} catch (SQLException exc) {
				check("Run", exc.getMessage(), "no error");
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
