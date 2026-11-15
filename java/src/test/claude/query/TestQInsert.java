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

import static msfx.lib.db.query.Comparison.EQ;

import java.sql.SQLException;
import java.util.List;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBEngine;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.adapters.DBEngineOracle;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.db.query.QInsert;
import msfx.lib.db.query.QSelect;
import msfx.lib.db.query.sa.SyntaxException;
import msfx.lib.md.Catalog;
import msfx.lib.md.Column;
import msfx.lib.md.Record;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import msfx.lib.md.Value;
import msfx.lib.util.json.JSONObject;
import test.db.CatalogCMA;

/**
 * Tests {@link QInsert}: the SQL written for rows of expressions, rows of
 * values and selects, and the validations of the statement. With "db", on the
 * local SQL Server, the test database: TPIECES_TMP is created as an empty copy
 * of TPIECES, filled row by row with the records read from TPIECES and
 * compared, emptied with a written DELETE, and filled again with an INSERT
 * from a select.
 *
 * @author Miquel Sas
 */
public class TestQInsert {

	/**
	 * SQL Server engine.
	 */
	private static final DBEngineSQLServer SQLSERVER = new DBEngineSQLServer();
	/**
	 * Oracle engine.
	 */
	private static final DBEngine ORACLE = new DBEngineOracle();
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
	 * The table TPIECES_TMP, a copy of TPIECES, defined with the domains of its
	 * columns.
	 */
	private static Table piecesTmp;

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
	 * Compares the SQL of an insert with the expected one.
	 *
	 * @param title    What is checked.
	 * @param insert   The insert.
	 * @param expected The expected SQL.
	 */
	private static void sql(String title, QInsert insert, String expected) {
		String result = insert.toString();
		report(expected.equals(result), title, result, expected);
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
	 * Returns a new insert into TPIECES_TMP.
	 *
	 * @param db The database engine.
	 * @return The insert.
	 */
	private static QInsert intoTmp(DBEngine db) {
		QInsert insert = new QInsert(db);
		insert.into(piecesTmp);
		return insert;
	}

	/**
	 * Returns a select of all the columns of a table, with their domains,
	 * ordered by the primary key CPTYPE.
	 *
	 * @param db    The database engine.
	 * @param table The table.
	 * @return The select.
	 */
	private static QSelect selectAll(DBEngine db, Table table) {
		QSelect select = new QSelect(db);
		select.from(table, "P");
		for (Column column : table.columns()) {
			StringBuilder expr = new StringBuilder();
			expr.append("P.");
			expr.append(column.getName());
			select.addColumn(expr.toString(), column.getName(), column.getDomain());
		}
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
	 * Returns the first column of the first row of a written query.
	 *
	 * @param sql The query.
	 * @return The value, as a string.
	 * @throws SQLException If a database access error occurs.
	 */
	private static String queryOne(String sql) throws SQLException {
		try (DBConnection cn = SQLSERVER.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

	/**
	 * Entry point.
	 *
	 * @param args Pass "db" to also run the inserts on the local SQL Server.
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

		System.out.println("== SQL");
		QInsert insert = intoTmp(SQLSERVER);
		insert.columns("CPTYPE", "dptype", "ISPAREPART");
		insert.values("'X1'", "UPPER('abc') || '-' || 'd'", "'Y'");
		sql("Row of expressions, names ignoring case, functions translated", insert, String.join("\n",
				"INSERT INTO [EXPLMARG_QLT].[TPIECES_TMP] ([CPTYPE], [DPTYPE], [ISPAREPART])",
				"VALUES (N'X1', CONCAT(UPPER(N'abc'), N'-', N'd'), N'Y')"));

		insert.values(new Value("X2"), new Value("Two"), new Value(true));
		sql("Row of values: placeholders, the values as comments, replacing the row", insert, String.join("\n",
				"INSERT INTO [EXPLMARG_QLT].[TPIECES_TMP] ([CPTYPE], [DPTYPE], [ISPAREPART])",
				"VALUES (?, ?, ?)",
				"-- 1: X2",
				"-- 2: Two",
				"-- 3: true"));

		QSelect source = new QSelect(SQLSERVER);
		source.from(pieces, "P");
		source.addColumn("P.CPTYPE", "CPTYPE");
		source.addColumn("P.DPTYPE", "DESCRIPTION");
		source.addColumn("P.ISPAREPART", "ISPAREPART");
		source.whereValue("P.CBUSINESS", EQ, "ACRI");
		insert.select(source);
		sql("Select, replacing the row, aliases not matched", insert, String.join("\n",
				"INSERT INTO [EXPLMARG_QLT].[TPIECES_TMP] ([CPTYPE], [DPTYPE], [ISPAREPART])",
				"SELECT [P].[CPTYPE] AS [CPTYPE], [P].[DPTYPE] AS [DESCRIPTION], [P].[ISPAREPART] AS [ISPAREPART]",
				"FROM [EXPLMARG_QLT].[TPIECES] [P]",
				"WHERE [P].[CBUSINESS] = N'ACRI'"));

		insert.values(new Value("X3"), new Value((String) null), new Value(false));
		sql("Values again, replacing the select, a null value", insert, String.join("\n",
				"INSERT INTO [EXPLMARG_QLT].[TPIECES_TMP] ([CPTYPE], [DPTYPE], [ISPAREPART])",
				"VALUES (?, ?, ?)",
				"-- 1: X3",
				"-- 2: null",
				"-- 3: false"));

		insert = intoTmp(SQLSERVER);
		insert.columns();
		String allColumns = insert.toString();
		report(allColumns.startsWith("INSERT INTO [EXPLMARG_QLT].[TPIECES_TMP] ([CPTYPE], [DPTYPE], [DPTYPE_ES], ")
				&& allColumns.endsWith("[CPTYPE_MDM], [IMDM])"), "All the columns", allColumns, "the 10 columns of TPIECES");

		insert = intoTmp(ORACLE);
		insert.columns("CPTYPE", "DPTYPE");
		insert.values("'X1'", "'A' || 'B'");
		sql("Oracle", insert, String.join("\n",
				"INSERT INTO \"EXPLMARG_QLT\".\"TPIECES_TMP\" (\"CPTYPE\", \"DPTYPE\")",
				"VALUES (N'X1', (N'A' || N'B'))"));

		System.out.println("== Errors");
		error("Table set twice", IllegalStateException.class, () -> intoTmp(SQLSERVER).into(pieces));
		error("Null table", NullPointerException.class, () -> new QInsert(SQLSERVER).into(null));
		error("Columns before the table", IllegalStateException.class, () -> new QInsert(SQLSERVER).columns());
		error("Unknown column name", IllegalArgumentException.class, () -> intoTmp(SQLSERVER).columns("CPTYPE", "XXX"));
		error("Repeated column, ignoring case", IllegalArgumentException.class,
				() -> intoTmp(SQLSERVER).columns("CPTYPE", "cptype"));
		error("Empty array of names", IllegalArgumentException.class, () -> intoTmp(SQLSERVER).columns(new String[0]));
		error("Column of another table", IllegalArgumentException.class,
				() -> intoTmp(SQLSERVER).columns(pieces.columns().get("CPTYPE")));
		Table piecesTmpOther = new Table("tpieces_tmp");
		piecesTmpOther.setSchema(CatalogCMA.SCHEMA);
		for (Column column : pieces.columns()) {
			Column copy = new Column(column.getName(), column.getDomain());
			piecesTmpOther.columns().add(copy);
		}
		String equalTable;
		try {
			QInsert i = intoTmp(SQLSERVER);
			i.columns(piecesTmpOther.columns().get("CPTYPE"), piecesTmpOther.columns().get("DPTYPE"));
			equalTable = i.toString();
		} catch (RuntimeException exc) {
			equalTable = exc.getMessage();
		}
		report(equalTable.equals("INSERT INTO [EXPLMARG_QLT].[TPIECES_TMP] ([CPTYPE], [DPTYPE])"),
				"Columns of an equal table, another instance", equalTable, "accepted");
		error("Columns changed once a row is set", IllegalStateException.class, () -> {
			QInsert i = intoTmp(SQLSERVER);
			i.columns("CPTYPE");
			i.values("'X'");
			i.columns("DPTYPE");
		});
		error("Row before the columns", IllegalStateException.class, () -> intoTmp(SQLSERVER).values("'X'"));
		error("Fewer expressions than columns", IllegalArgumentException.class, () -> {
			QInsert i = intoTmp(SQLSERVER);
			i.columns("CPTYPE", "DPTYPE");
			i.values("'X'");
		});
		error("More values than columns", IllegalArgumentException.class, () -> {
			QInsert i = intoTmp(SQLSERVER);
			i.columns("CPTYPE");
			i.values(new Value("X"), new Value("Y"));
		});
		error("A column in an expression", SyntaxException.class, () -> {
			QInsert i = intoTmp(SQLSERVER);
			i.columns("CPTYPE");
			i.values("CPTYPE");
		});
		error("An aggregate in an expression", SyntaxException.class, () -> {
			QInsert i = intoTmp(SQLSERVER);
			i.columns("CPTYPE");
			i.values("MAX('X')");
		});
		error("A placeholder in an expression", SyntaxException.class, () -> {
			QInsert i = intoTmp(SQLSERVER);
			i.columns("CPTYPE");
			i.values("?");
		});
		Table docs = new Table("DOCS");
		docs.columns().add(new Column("CDOC", Types.STRING, 10, -1));
		docs.columns().add(new Column("BDATA", Types.BINARY, -1, -1));
		docs.columns().add(new Column("OMETA", Types.OBJECT, -1, -1));
		error("Expressions with a BINARY column", IllegalArgumentException.class, () -> {
			QInsert i = new QInsert(SQLSERVER);
			i.into(docs);
			i.columns("CDOC", "BDATA");
			i.values("'D1'", "NULL");
		});
		String binaryRow;
		try {
			QInsert i = new QInsert(SQLSERVER);
			i.into(docs);
			i.columns();
			i.values(new Value("D1"), new Value(new byte[] { 1, 2, 3 }), new Value((JSONObject) null));
			binaryRow = i.toString();
		} catch (RuntimeException exc) {
			binaryRow = exc.getMessage();
		}
		report(binaryRow.endsWith("-- 2: BINARY, 3 bytes\n-- 3: null"), "Values with BINARY and OBJECT columns", binaryRow,
				"accepted");
		error("Select with another number of columns", IllegalArgumentException.class, () -> {
			QInsert i = intoTmp(SQLSERVER);
			i.columns("CPTYPE");
			i.select(selectAll(SQLSERVER, pieces));
		});
		error("Select with ORDER BY", IllegalArgumentException.class, () -> {
			QSelect s = selectAll(SQLSERVER, pieces);
			s.orderBy("CPTYPE");
			QInsert i = intoTmp(SQLSERVER);
			i.columns();
			i.select(s);
		});
		error("Select without FROM", IllegalStateException.class, () -> {
			QInsert i = intoTmp(SQLSERVER);
			i.columns();
			i.select(new QSelect(SQLSERVER));
		});
		error("Select of another engine", IllegalArgumentException.class, () -> {
			QInsert i = intoTmp(SQLSERVER);
			i.columns();
			i.select(selectAll(ORACLE, pieces));
		});
		error("Execute without row nor select", IllegalStateException.class, () -> {
			QInsert i = intoTmp(SQLSERVER);
			i.columns();
			try {
				i.execute();
			} catch (SQLException exc) {
				throw new RuntimeException(exc);
			}
		});

		if (args.length > 0 && args[0].equals("db")) {
			System.out.println("== Run on SQL Server");
			SQLSERVER.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");
			try {
				execute("DROP TABLE IF EXISTS EXPLMARG_QLT.TPIECES_TMP");
				execute("SELECT * INTO EXPLMARG_QLT.TPIECES_TMP FROM EXPLMARG_QLT.TPIECES WHERE 1 = 0");

				QSelect readPieces = selectAll(SQLSERVER, pieces);
				readPieces.orderBy("CPTYPE");
				List<Record> records = readPieces.readList();
				QInsert rowInsert = intoTmp(SQLSERVER);
				rowInsert.columns();
				int inserted = 0;
				long start = System.currentTimeMillis();
				for (Record record : records) {
					Value[] values = new Value[record.size()];
					for (int i = 0; i < values.length; i++) {
						values[i] = record.getValue(i);
					}
					rowInsert.values(values);
					inserted += rowInsert.execute();
				}
				long elapsed = System.currentTimeMillis() - start;
				QSelect readTmp = selectAll(SQLSERVER, piecesTmp);
				readTmp.orderBy("CPTYPE");
				List<Record> copies = readTmp.readList();
				String original = describe(records);
				String copied = describe(copies);
				StringBuilder rowResult = new StringBuilder();
				rowResult.append(inserted);
				rowResult.append(" rows inserted one by one in ");
				rowResult.append(elapsed);
				rowResult.append(" ms, ");
				rowResult.append(copies.size());
				rowResult.append(" read back, ");
				rowResult.append(original.equals(copied) ? "same values" : "DIFFERENT values");
				report(inserted == records.size() && original.equals(copied), "Row by row from the records of TPIECES",
						rowResult.toString(), "same values");

				QInsert exprInsert = intoTmp(SQLSERVER);
				exprInsert.columns("CPTYPE", "DPTYPE", "ISPAREPART");
				exprInsert.values("'ZZ_TEST'", "UPPER('test') || '-' || 'row'", "'Y'");
				int exprRows = exprInsert.execute();
				String exprRead = queryOne("SELECT DPTYPE FROM EXPLMARG_QLT.TPIECES_TMP WHERE CPTYPE = 'ZZ_TEST'");
				StringBuilder exprResult = new StringBuilder();
				exprResult.append(exprRows);
				exprResult.append(" row, DPTYPE ");
				exprResult.append(exprRead);
				report(exprRows == 1 && "TEST-row".equals(exprRead), "Row of expressions", exprResult.toString(),
						"1 row, DPTYPE TEST-row");

				execute("DELETE FROM EXPLMARG_QLT.TPIECES_TMP");
				QInsert selectInsert = intoTmp(SQLSERVER);
				selectInsert.columns();
				selectInsert.select(selectAll(SQLSERVER, pieces));
				int selected = selectInsert.execute();
				String missing = queryOne(String.join(" ",
						"SELECT COUNT(*) FROM (SELECT * FROM EXPLMARG_QLT.TPIECES",
						"EXCEPT SELECT * FROM EXPLMARG_QLT.TPIECES_TMP) X"));
				String extra = queryOne(String.join(" ",
						"SELECT COUNT(*) FROM (SELECT * FROM EXPLMARG_QLT.TPIECES_TMP",
						"EXCEPT SELECT * FROM EXPLMARG_QLT.TPIECES) X"));
				StringBuilder selectResult = new StringBuilder();
				selectResult.append(selected);
				selectResult.append(" rows inserted from the select, ");
				selectResult.append(missing);
				selectResult.append(" missing, ");
				selectResult.append(extra);
				selectResult.append(" extra");
				report(selected == records.size() && "0".equals(missing) && "0".equals(extra),
						"After a written DELETE, from a select", selectResult.toString(), "all rows, 0 missing, 0 extra");
			} catch (SQLException exc) {
				report(false, "Run", exc.getMessage(), "no error");
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
