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
import static msfx.lib.db.query.Logical.AND;
import static msfx.lib.db.query.Logical.AND_NOT;
import static test.claude.Check.*;
import static test.claude.Fixture.*;

import java.sql.SQLException;
import java.util.List;

import msfx.lib.db.DBEngine;
import msfx.lib.db.query.QDelete;
import msfx.lib.db.query.QInsert;
import msfx.lib.db.query.QSelect;
import msfx.lib.db.query.QWhere;
import msfx.lib.db.query.sa.SyntaxException;
import msfx.lib.md.Column;
import msfx.lib.md.ColumnList;
import msfx.lib.md.Index;
import msfx.lib.md.Record;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import msfx.lib.md.Value;
import msfx.lib.util.json.JSONObject;
import test.claude.Check;
import test.db.CatalogCMA;

/**
 * Tests the statements that change data, {@link QInsert} and {@link QDelete}:
 * the SQL written, inserting and deleting records, and the validations. With
 * "db", on the local SQL Server, the test database: TPIECES_TMP is created as
 * an empty copy of TPIECES and filled with the records of TPIECES, a row of
 * expressions, a row of values and a select; the records read back with its
 * own columns are written back after a written DELETE; then rows are deleted
 * by record, by a WHERE with a value, by an EXISTS on TPIECES, and all of
 * them.
 *
 * @author Miquel Sas
 */
public class TestQDML {

	/**
	 * The table TPIECES.
	 */
	private static Table pieces;
	/**
	 * The table TPIECES_TMP, a copy of TPIECES defined with the domains of its
	 * columns and the primary key CPTYPE.
	 */
	private static Table piecesTmp;

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
		return select;
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
	 * Reads all the rows of TPIECES or TPIECES_TMP as records, ordered by
	 * CPTYPE.
	 *
	 * @param table        The table.
	 * @param tableColumns True to read with the columns of the table.
	 * @return The records.
	 * @throws SQLException If a database access error occurs.
	 */
	private static List<Record> readAll(Table table, boolean tableColumns) throws SQLException {
		QSelect select = selectAll(SQLSERVER, table, tableColumns);
		select.orderBy("CPTYPE");
		List<Record> records = select.readList();
		return records;
	}

	/**
	 * Checks the SQL written by an insert built by hand.
	 */
	private static void statements() {
		section("SQL");
		QInsert insert = intoTmp(SQLSERVER);
		insert.columns("CPTYPE", "dptype", "ISPAREPART");
		insert.values("'X1'", "UPPER('abc') || '-' || 'd'", "'Y'");
		sql("Row of expressions, names ignoring case, functions translated", insert, lines(
				"INSERT INTO [EXPLMARG_QLT].[TPIECES_TMP] ([CPTYPE], [DPTYPE], [ISPAREPART])",
				"VALUES (N'X1', CONCAT(UPPER(N'abc'), N'-', N'd'), N'Y')"));

		insert.values(new Value("X2"), new Value("Two"), new Value(true));
		sql("Row of values: placeholders, the values as comments, replacing the row", insert, lines(
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
		source.getWhere().value("P.CBUSINESS", EQ, "ACRI");
		insert.select(source);
		sql("Select, replacing the row, aliases not matched", insert, lines(
				"INSERT INTO [EXPLMARG_QLT].[TPIECES_TMP] ([CPTYPE], [DPTYPE], [ISPAREPART])",
				"SELECT [P].[CPTYPE] AS [CPTYPE], [P].[DPTYPE] AS [DESCRIPTION], [P].[ISPAREPART] AS [ISPAREPART]",
				"FROM [EXPLMARG_QLT].[TPIECES] [P]",
				"WHERE [P].[CBUSINESS] = N'ACRI'"));

		insert.values(new Value("X3"), new Value((String) null), new Value(false));
		sql("Values again, replacing the select, a null value", insert, lines(
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
		sql("Oracle", insert, lines(
				"INSERT INTO \"EXPLMARG_QLT\".\"TPIECES_TMP\" (\"CPTYPE\", \"DPTYPE\")",
				"VALUES (N'X1', (N'A' || N'B'))"));
	}

	/**
	 * Checks the validations of an insert built by hand.
	 */
	private static void errors() {
		section("Errors");
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
		equal("Columns of an equal table, another instance", equalTable,
				"INSERT INTO [EXPLMARG_QLT].[TPIECES_TMP] ([CPTYPE], [DPTYPE])");
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
			i.select(selectAll(SQLSERVER, pieces, false));
		});
		error("Select with ORDER BY", IllegalArgumentException.class, () -> {
			QSelect s = selectAll(SQLSERVER, pieces, false);
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
			i.select(selectAll(ORACLE, pieces, false));
		});
		error("Execute without row nor select", IllegalStateException.class, () -> {
			QInsert i = intoTmp(SQLSERVER);
			i.columns();
			i.execute();
		});
	}

	/**
	 * Checks the validations of the records inserted.
	 */
	private static void recordErrors() {
		section("Checks of the records");
		Column tmpType = piecesTmp.columns().get("CPTYPE");
		Column tmpDesc = piecesTmp.columns().get("DPTYPE");
		error("Insert built by hand", IllegalStateException.class, () -> {
			QInsert insert = intoTmp(SQLSERVER);
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
	}

	/**
	 * Runs the inserts on the local SQL Server.
	 *
	 * @throws SQLException If a database access error occurs.
	 */
	private static void runs() throws SQLException {
		section("Runs on SQL Server");
		connect();
		execute("DROP TABLE IF EXISTS EXPLMARG_QLT.TPIECES_TMP");
		execute("SELECT * INTO EXPLMARG_QLT.TPIECES_TMP FROM EXPLMARG_QLT.TPIECES WHERE 1 = 0");

		List<Record> source = readAll(pieces, false);
		QInsert copyInsert = new QInsert(SQLSERVER);
		ColumnList tmpColumns = piecesTmp.columns();
		int inserted = 0;
		for (Record record : source) {
			Record tmpRecord = new Record(tmpColumns, values(record));
			inserted += copyInsert.execute(tmpRecord);
		}
		List<Record> copied = readAll(piecesTmp, false);
		StringBuilder copyResult = new StringBuilder();
		copyResult.append(inserted);
		copyResult.append(" records inserted with one insert, ");
		copyResult.append(describe(source).equals(describe(copied)) ? "same values" : "DIFFERENT values");
		equal("Copy of TPIECES into TPIECES_TMP, records sharing a column list", copyResult.toString(),
				"771 records inserted with one insert, same values");

		Value[] sameValues = values(source.get(0));
		sameValues[0] = new Value("ZZ_SAME");
		ColumnList sameList = list(piecesTmp.columns().columns().toArray(new Column[0]));
		int sameRows = copyInsert.execute(new Record(sameList, sameValues));
		equal("Same structure on another column list, accepted", String.valueOf(sameRows), "1");
		error("A record with other columns", IllegalArgumentException.class,
				() -> copyInsert.execute(record(list(piecesTmp.columns().get("CPTYPE")))));
		error("Values set by hand on an insert used with records", IllegalStateException.class,
				() -> copyInsert.values(new Value("X")));

		QInsert exprInsert = intoTmp(SQLSERVER);
		exprInsert.columns("CPTYPE", "DPTYPE", "ISPAREPART");
		exprInsert.values("'ZZ_EXPR'", "UPPER('test') || '-' || 'row'", "'Y'");
		int exprRows = exprInsert.execute();
		String exprRead = queryOne("SELECT DPTYPE FROM EXPLMARG_QLT.TPIECES_TMP WHERE CPTYPE = 'ZZ_EXPR'");
		StringBuilder exprResult = new StringBuilder();
		exprResult.append(exprRows);
		exprResult.append(" row, DPTYPE ");
		exprResult.append(exprRead);
		equal("Row of expressions", exprResult.toString(), "1 row, DPTYPE TEST-row");

		QInsert valueInsert = intoTmp(SQLSERVER);
		valueInsert.columns("CPTYPE", "DPTYPE", "ISPAREPART");
		valueInsert.values(new Value("ZZ_VALUE"), new Value("Value row"), new Value(true));
		int valueRows = valueInsert.execute();
		String valueRead = queryOne("SELECT DPTYPE + '|' + ISPAREPART FROM EXPLMARG_QLT.TPIECES_TMP WHERE CPTYPE = 'ZZ_VALUE'");
		StringBuilder valueResult = new StringBuilder();
		valueResult.append(valueRows);
		valueResult.append(" row, ");
		valueResult.append(valueRead);
		equal("Row of values, a boolean written as Y", valueResult.toString(), "1 row, Value row|Y");

		execute("DELETE FROM EXPLMARG_QLT.TPIECES_TMP");
		QInsert selectInsert = intoTmp(SQLSERVER);
		selectInsert.columns();
		selectInsert.select(selectAll(SQLSERVER, pieces, false));
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
		equal("After a written DELETE, from a select", selectResult.toString(),
				"771 rows inserted from the select, 0 missing, 0 extra");

		List<Record> own = readAll(piecesTmp, true);
		execute("DELETE FROM EXPLMARG_QLT.TPIECES_TMP");
		QInsert backInsert = new QInsert(SQLSERVER);
		int written = 0;
		for (Record record : own) {
			written += backInsert.execute(record);
		}
		List<Record> back = readAll(piecesTmp, false);
		StringBuilder backResult = new StringBuilder();
		backResult.append(written);
		backResult.append(" records written back, ");
		backResult.append(describe(source).equals(describe(back)) ? "same values" : "DIFFERENT values");
		equal("Records read with the table columns, written back after a DELETE", backResult.toString(),
				"771 records written back, same values");

		Value[] one = values(own.get(0));
		one[0] = new Value("ZZ_ONE");
		int single = new QInsert(SQLSERVER).execute(new Record(own.get(0).columns(), one));
		equal("A single record in one line", String.valueOf(single), "1");

		deleteRuns(own);
	}

	/**
	 * Returns a new delete from TPIECES_TMP P.
	 *
	 * @param db The database engine.
	 * @return The delete.
	 */
	private static QDelete fromTmp(DBEngine db) {
		QDelete delete = new QDelete(db);
		delete.from(piecesTmp, "P");
		return delete;
	}

	/**
	 * Checks the SQL written by a delete built by hand.
	 */
	private static void deleteStatements() {
		section("DELETE: SQL");
		QDelete delete = new QDelete(SQLSERVER);
		sql("Empty delete", delete, "");
		delete.from(piecesTmp, "P");
		sql("No conditions: all the rows", delete, lines(
				"DELETE [P]",
				"FROM [EXPLMARG_QLT].[TPIECES_TMP] [P]"));

		delete = fromTmp(SQLSERVER);
		QWhere where = delete.getWhere();
		where.value("P.CBUSINESS", EQ, "ACRI");
		where.in(AND_NOT, "P.CUNIT", new Value("UND"), new Value("M2P"));
		sql("Conditions with values, SQL Server", delete, lines(
				"DELETE [P]",
				"FROM [EXPLMARG_QLT].[TPIECES_TMP] [P]",
				"WHERE [P].[CBUSINESS] = N'ACRI' AND NOT [P].[CUNIT] IN (N'UND', N'M2P')"));

		delete = fromTmp(SQLSERVER);
		where = delete.getWhere();
		QSelect exists = where.createExists();
		exists.from(pieces, "X");
		exists.addColumn("1", "NREF");
		QWhere existsWhere = exists.getWhere();
		existsWhere.expr("X.CPTYPE", EQ, "P.CPTYPE");
		existsWhere.value(AND, "X.ISPAREPART", EQ, "Y");
		where.exists(exists);
		sql("EXISTS on another table, using the alias", delete, lines(
				"DELETE [P]",
				"FROM [EXPLMARG_QLT].[TPIECES_TMP] [P]",
				"WHERE EXISTS (",
				"\tSELECT 1 AS [NREF]",
				"\tFROM [EXPLMARG_QLT].[TPIECES] [X]",
				"\tWHERE [X].[CPTYPE] = [P].[CPTYPE] AND [X].[ISPAREPART] = N'Y'",
				")"));

		delete = fromTmp(ORACLE);
		delete.getWhere().value("P.CBUSINESS", EQ, "ACRI");
		sql("Oracle", delete, lines(
				"DELETE",
				"FROM \"EXPLMARG_QLT\".\"TPIECES_TMP\" \"P\"",
				"WHERE \"P\".\"CBUSINESS\" = N'ACRI'"));
	}

	/**
	 * Checks the validations of deletes that need no database.
	 */
	private static void deleteErrors() {
		section("DELETE: errors");
		error("Table set twice", IllegalStateException.class, () -> fromTmp(SQLSERVER).from(pieces, "X"));
		error("Null alias", NullPointerException.class, () -> new QDelete(SQLSERVER).from(piecesTmp, null));
		error("Blank alias", IllegalArgumentException.class, () -> new QDelete(SQLSERVER).from(piecesTmp, " "));
		error("Condition before the table", IllegalStateException.class,
				() -> new QDelete(SQLSERVER).getWhere().cond("P.CPTYPE = 'X'"));
		error("Execute without table", IllegalStateException.class, () -> new QDelete(SQLSERVER).execute());

		Column tmpType = piecesTmp.columns().get("CPTYPE");
		Column tmpDesc = piecesTmp.columns().get("DPTYPE");
		error("Record on a delete built by hand", IllegalStateException.class,
				() -> fromTmp(SQLSERVER).execute(record(list(tmpType, tmpDesc))));
		error("A record without columns", IllegalArgumentException.class,
				() -> new QDelete(SQLSERVER).execute(new Record(new ColumnList(), new Value[0])));
		QSelect free = selectAll(SQLSERVER, pieces, false);
		error("Columns of no table, as read with domains", IllegalArgumentException.class,
				() -> new QDelete(SQLSERVER).execute(record(free.getColumnList())));
		error("Columns of two tables", IllegalArgumentException.class,
				() -> new QDelete(SQLSERVER).execute(record(list(tmpType, pieces.columns().get("DPTYPE")))));
		error("Without the primary key column", IllegalArgumentException.class,
				() -> new QDelete(SQLSERVER).execute(record(list(tmpDesc))));
		Table noKey = new Table("NOKEY");
		noKey.columns().add(new Column("CCODE", Types.STRING, 10, -1));
		error("A table without primary key", IllegalArgumentException.class,
				() -> new QDelete(SQLSERVER).execute(record(list(noKey.columns().get("CCODE")))));
	}

	/**
	 * Runs the deletes on the local SQL Server, on TPIECES_TMP filled by the
	 * inserts.
	 *
	 * @param own The records of TPIECES_TMP, read with its columns.
	 * @throws SQLException If a database access error occurs.
	 */
	private static void deleteRuns(List<Record> own) throws SQLException {
		section("DELETE: runs on SQL Server");
		String countSQL = "SELECT COUNT(*) FROM EXPLMARG_QLT.TPIECES_TMP";
		int before = Integer.parseInt(queryOne(countSQL));
		QDelete recordDelete = new QDelete(SQLSERVER);
		int deleted = 0;
		for (int i = 0; i < 10; i++) {
			deleted += recordDelete.execute(own.get(i));
		}
		int again = recordDelete.execute(own.get(0));
		int after = Integer.parseInt(queryOne(countSQL));
		StringBuilder recordResult = new StringBuilder();
		recordResult.append(deleted);
		recordResult.append(" deleted, ");
		recordResult.append(again);
		recordResult.append(" deleted again, ");
		recordResult.append(before - after);
		recordResult.append(" fewer rows");
		equal("Ten records by key with one delete, one of them again", recordResult.toString(),
				"10 deleted, 0 deleted again, 10 fewer rows");
		StringBuilder lastKey = new StringBuilder();
		lastKey.append("-- 1: ");
		lastKey.append(own.get(0).getValue("CPTYPE"));
		equal("SQL of the records, with the key of the last one", recordDelete.toString(), lines(
				"DELETE FROM [EXPLMARG_QLT].[TPIECES_TMP]",
				"WHERE [CPTYPE] = ?",
				lastKey.toString()));
		ColumnList other = list(piecesTmp.columns().columns().toArray(new Column[0]));
		error("A record with another column list", IllegalArgumentException.class,
				() -> recordDelete.execute(new Record(other, values(own.get(20)))));
		error("Conditions on a delete used with records", IllegalStateException.class, () -> recordDelete.getWhere());
		error("Table on a delete used with records", IllegalStateException.class,
				() -> recordDelete.from(piecesTmp, "P"));
		error("Execute by hand on a delete used with records", IllegalStateException.class, () -> recordDelete.execute());

		String business = queryOne("SELECT MAX(CBUSINESS) FROM EXPLMARG_QLT.TPIECES_TMP");
		StringBuilder countBusiness = new StringBuilder();
		countBusiness.append("SELECT COUNT(*) FROM EXPLMARG_QLT.TPIECES_TMP WHERE CBUSINESS = '");
		countBusiness.append(business);
		countBusiness.append("'");
		String expected = queryOne(countBusiness.toString());
		QDelete byBusiness = fromTmp(SQLSERVER);
		byBusiness.getWhere().value("P.CBUSINESS", EQ, business);
		int rows = byBusiness.execute();
		String remaining = queryOne(countBusiness.toString());
		StringBuilder businessResult = new StringBuilder();
		businessResult.append(rows);
		businessResult.append(" deleted, ");
		businessResult.append(remaining);
		businessResult.append(" left");
		StringBuilder businessExpected = new StringBuilder();
		businessExpected.append(expected);
		businessExpected.append(" deleted, 0 left");
		equal("WHERE with a value", businessResult.toString(), businessExpected.toString());

		expected = queryOne(lines(
				"SELECT COUNT(*) FROM EXPLMARG_QLT.TPIECES_TMP P",
				"WHERE EXISTS (SELECT 1 FROM EXPLMARG_QLT.TPIECES X WHERE X.CPTYPE = P.CPTYPE AND X.ISPAREPART = 'N')"));
		QDelete byExists = fromTmp(SQLSERVER);
		QWhere where = byExists.getWhere();
		QSelect exists = where.createExists();
		exists.from(pieces, "X");
		exists.addColumn("1", "NREF");
		QWhere existsWhere = exists.getWhere();
		existsWhere.expr("X.CPTYPE", EQ, "P.CPTYPE");
		existsWhere.value(AND, "X.ISPAREPART", EQ, "N");
		where.exists(exists);
		rows = byExists.execute();
		equal("WHERE EXISTS on another table", String.valueOf(rows), expected);

		expected = queryOne(countSQL);
		rows = fromTmp(SQLSERVER).execute();
		StringBuilder allResult = new StringBuilder();
		allResult.append(rows);
		allResult.append(" deleted, ");
		allResult.append(queryOne(countSQL));
		allResult.append(" left");
		StringBuilder allExpected = new StringBuilder();
		allExpected.append(expected);
		allExpected.append(" deleted, 0 left");
		equal("No conditions: all the rows", allResult.toString(), allExpected.toString());
	}

	/**
	 * Runs the checks.
	 *
	 * @param db True to also run the inserts on the local SQL Server.
	 */
	public static void run(boolean db) {
		Check.start("TestQDML");
		pieces = table("TPIECES");
		piecesTmp = new Table("TPIECES_TMP");
		piecesTmp.setSchema(CatalogCMA.SCHEMA);
		for (Column column : pieces.columns()) {
			Column copy = new Column(column.getName(), column.getDomain());
			piecesTmp.columns().add(copy);
		}
		Index primaryKey = new Index(piecesTmp);
		primaryKey.addColumns("CPTYPE");
		piecesTmp.setPrimaryKey(primaryKey);

		statements();
		errors();
		recordErrors();
		deleteStatements();
		deleteErrors();
		if (db) {
			try {
				runs();
			} catch (SQLException exc) {
				report(false, "Runs on SQL Server", exc.getMessage(), "no error");
			}
		}
	}

	/**
	 * Entry point.
	 *
	 * @param args Pass "db" to also run the inserts on the local SQL Server.
	 */
	public static void main(String[] args) {
		boolean db = args.length > 0 && args[0].equals("db");
		run(db);
		summary();
		System.exit(0);
	}
}
