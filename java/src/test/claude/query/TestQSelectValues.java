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
import java.util.ArrayList;
import java.util.List;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.db.query.QSelect;
import msfx.lib.md.Catalog;
import msfx.lib.md.Column;
import msfx.lib.md.ColumnList;
import msfx.lib.md.Domain;
import msfx.lib.md.Record;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import msfx.lib.md.Value;
import test.db.CatalogCMA;

/**
 * Tests {@link QSelect#getValues(DBResultSet)} on the local SQL Server: one
 * value per type, with the decimals of the domains, booleans as Y/N and 1/0,
 * an object as JSON text, and the conversion of nulls. The rows come from a
 * derived table of literals written in place of the table of the select, and
 * from a LEFT JOIN of CMA tables with a narrow WHERE. Only SELECT statements
 * are executed.
 *
 * @author Miquel Sas
 */
public class TestQSelectValues {

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
	 * Describes the values of a row, one per line: alias, type and value, and
	 * whether it is modified.
	 *
	 * @param columns The columns.
	 * @param values  The values.
	 * @return The description.
	 */
	private static String describe(ColumnList columns, Value[] values) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				b.append("\n");
			}
			b.append(columns.getAlias(i));
			b.append(" ");
			b.append(values[i].getType());
			b.append(" ");
			b.append(values[i]);
			if (values[i].isModified()) {
				b.append(" MODIFIED");
			}
		}
		return b.toString();
	}

	/**
	 * Reads the rows of a hand-written query as text, one row per line,
	 * columns separated by spaces.
	 *
	 * @param sql The query.
	 * @return The rows, or the error message.
	 */
	private static String rowsOf(String sql) {
		StringBuilder result = new StringBuilder();
		try (DBConnection cn = SQLSERVER.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql)) {
			int count = rs.getMetaData().getColumnCount();
			while (rs.next()) {
				for (int i = 1; i <= count; i++) {
					if (i > 1) {
						result.append(" ");
					}
					result.append(rs.getString(i));
				}
				result.append("\n");
			}
		} catch (SQLException exc) {
			result.append(exc.getMessage());
		}
		return result.toString();
	}

	/**
	 * Reads the rows of a select with getValues, validates each one against
	 * the column list with {@link Record#validate(ColumnList, Value[])}, and
	 * describes them.
	 *
	 * @param select The select.
	 * @param sql    The SQL to execute, the one of the select or a variation.
	 * @return The rows described, separated by a line with "--", or the error.
	 */
	private static String read(QSelect select, String sql) {
		ColumnList columns = select.getColumnList();
		List<String> rows = new ArrayList<>();
		try (DBConnection cn = SQLSERVER.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) {
				Value[] values = select.getValues(rs);
				Record.validate(columns, values);
				rows.add(describe(columns, values));
			}
		} catch (SQLException | RuntimeException exc) {
			StringBuilder b = new StringBuilder();
			b.append(exc.getClass().getSimpleName());
			b.append(": ");
			b.append(exc.getMessage());
			rows.add(b.toString());
		}
		return String.join("\n--\n", rows);
	}

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {
		SQLSERVER.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");

		System.out.println("== One value per type, and nulls");
		Table vals = new Table("VALS");
		vals.setSchema("dbo");
		String[] names = {
				"IFLAG", "IBIT", "QPRICE", "QQUOTE", "NQTY", "NBIG", "QRATE",
				"DDATE", "TTIME", "TSTAMP", "CNAME", "BDATA", "OJSON" };
		Types[] dbTypes = {
				Types.STRING, Types.STRING, Types.DECIMAL, Types.DOUBLE, Types.INTEGER, Types.LONG, Types.DOUBLE,
				Types.DATE, Types.TIME, Types.DATETIME, Types.STRING, Types.BINARY, Types.OBJECT };
		for (int i = 0; i < names.length; i++) {
			vals.columns().add(new Column(names[i], dbTypes[i], -1, -1));
		}
		QSelect select = new QSelect(SQLSERVER);
		select.from(vals, "V");
		select.addColumn("V.IFLAG", "IFLAG", new Domain("IFLAG", Types.BOOLEAN, -1, -1));
		select.addColumn("V.IBIT", "IBIT", new Domain("IBIT", Types.BOOLEAN, -1, -1));
		select.addColumn("V.QPRICE", "QPRICE", new Domain("QPRICE", Types.DECIMAL, 16, 2));
		select.addColumn("V.QQUOTE", "QQUOTE", new Domain("QQUOTE", Types.DECIMAL, 16, 5));
		select.addColumn("V.NQTY", "NQTY", new Domain("NQTY", Types.INTEGER, -1, -1));
		select.addColumn("V.NBIG", "NBIG", new Domain("NBIG", Types.LONG, -1, -1));
		select.addColumn("V.QRATE", "QRATE", new Domain("QRATE", Types.DOUBLE, -1, 2));
		select.addColumn("V.DDATE", "DDATE", new Domain("DDATE", Types.DATE, -1, -1));
		select.addColumn("V.TTIME", "TTIME", new Domain("TTIME", Types.TIME, -1, -1));
		select.addColumn("V.TSTAMP", "TSTAMP", new Domain("TSTAMP", Types.DATETIME, -1, -1));
		select.addColumn("V.CNAME", "CNAME", new Domain("CNAME", Types.STRING, 10, -1));
		select.addColumn("V.BDATA", "BDATA", new Domain("BDATA", Types.BINARY, -1, -1));
		select.addColumn("V.OJSON", "OJSON", new Domain("OJSON", Types.OBJECT, -1, -1));
		select.orderBy("NQTY DESC");

		StringBuilder rows = new StringBuilder();
		rows.append("FROM (");
		rows.append("SELECT 'Y' AS IFLAG, '1' AS IBIT, CAST(12.34567891 AS DECIMAL(18,8)) AS QPRICE, ");
		rows.append("CAST(1.0842349999 AS FLOAT) AS QQUOTE, 42 AS NQTY, CAST(9000000000 AS BIGINT) AS NBIG, ");
		rows.append("CAST(0.25 AS FLOAT) AS QRATE, CAST('2026-11-14' AS DATE) AS DDATE, CAST('10:30:00' AS TIME) AS TTIME, ");
		rows.append("CAST('2026-11-14 10:30:00' AS DATETIME2) AS TSTAMP, N'LCZ' AS CNAME, CAST(0x4142 AS VARBINARY(10)) AS BDATA, ");
		rows.append("CAST(N'{\"CCOMPANY\":\"LCZ\"}' AS NVARCHAR(MAX)) AS OJSON ");
		rows.append("UNION ALL SELECT NULL, NULL, CAST(NULL AS DECIMAL(18,8)), CAST(NULL AS FLOAT), NULL, CAST(NULL AS BIGINT), ");
		rows.append("CAST(NULL AS FLOAT), CAST(NULL AS DATE), CAST(NULL AS TIME), CAST(NULL AS DATETIME2), NULL, ");
		rows.append("CAST(NULL AS VARBINARY(10)), CAST(NULL AS NVARCHAR(MAX))");
		rows.append(") [V]");
		String sql = select.toString().replace("FROM [dbo].[VALS] [V]", rows.toString());
		String expected = String.join("\n",
				"IFLAG BOOLEAN true",
				"IBIT BOOLEAN true",
				"QPRICE DECIMAL 12.35",
				"QQUOTE DECIMAL 1.08423",
				"NQTY INTEGER 42",
				"NBIG LONG 9000000000",
				"QRATE DOUBLE 0.25",
				"DDATE DATE 2026-11-14",
				"TTIME TIME 10:30",
				"TSTAMP DATETIME 2026-11-14T10:30",
				"CNAME STRING LCZ",
				"BDATA BINARY AB",
				"OJSON OBJECT {\"CCOMPANY\":\"LCZ\"}",
				"--",
				"IFLAG BOOLEAN false",
				"IBIT BOOLEAN false",
				"QPRICE DECIMAL 0.00",
				"QQUOTE DECIMAL 0.00000",
				"NQTY INTEGER 0",
				"NBIG LONG 0",
				"QRATE DOUBLE 0.0",
				"DDATE DATE null",
				"TTIME TIME null",
				"TSTAMP DATETIME null",
				"CNAME STRING ",
				"BDATA BINARY null",
				"OJSON OBJECT null");
		String result = read(select, sql);
		report(result.equals(expected), "Values typed by the domains, decimals of the domains, nulls converted, not modified",
				result, expected);

		System.out.println("== Records");
		List<Record> records = new ArrayList<>();
		try (DBConnection cn = SQLSERVER.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) {
				records.add(select.getRecord(rs));
			}
		} catch (SQLException exc) {
			result = exc.getMessage();
		}
		StringBuilder recordResult = new StringBuilder();
		for (Record record : records) {
			recordResult.append(record.columns() == select.getColumnList() ? "shared columns" : "own columns");
			recordResult.append(", QPRICE ");
			recordResult.append(record.getValue("QPRICE"));
			recordResult.append(", CNAME '");
			recordResult.append(record.getValue("CNAME"));
			recordResult.append("', ");
			recordResult.append(record.size());
			recordResult.append(" values\n");
		}
		expected = String.join("\n",
				"shared columns, QPRICE 12.35, CNAME 'LCZ', 13 values",
				"shared columns, QPRICE 0.00, CNAME '', 13 values",
				"");
		report(recordResult.toString().equals(expected), "getRecord: the column list of the select and the values of the row",
				recordResult.toString(), expected);

		System.out.println("== readList");
		Catalog cma = new Catalog();
		cma.addProvider(new CatalogCMA());
		QSelect divisionList = new QSelect(SQLSERVER);
		divisionList.from(cma.getTable(CatalogCMA.SCHEMA, "DIVISIONS"), "D");
		divisionList.addColumn("D.CDIVISION", "CDIVISION", new Domain("CDIVISION", Types.STRING, 10, -1));
		divisionList.addColumn("D.DDIVISION", "DDIVISION", new Domain("DDIVISION", Types.STRING, 60, -1));
		divisionList.orderBy("CDIVISION");
		String listResult;
		String listExpected = rowsOf("SELECT CDIVISION, DDIVISION FROM EXPLMARG_QLT.DIVISIONS ORDER BY CDIVISION");
		try {
			List<Record> list = divisionList.readList();
			StringBuilder b = new StringBuilder();
			for (Record record : list) {
				b.append(record.columns() == divisionList.getColumnList() ? "" : "OWN COLUMNS ");
				b.append(record.getValue("CDIVISION"));
				b.append(" ");
				b.append(record.getValue("DDIVISION"));
				b.append("\n");
			}
			listResult = b.toString();
		} catch (SQLException exc) {
			listResult = exc.getMessage();
		}
		report(!listResult.isEmpty() && listResult.equals(listExpected), "All the rows as records, sharing the column list",
				listResult, listExpected);

		QSelect noDomainList = new QSelect(SQLSERVER);
		noDomainList.from(cma.getTable(CatalogCMA.SCHEMA, "DIVISIONS"), "D");
		noDomainList.addColumn("D.CDIVISION", "CDIVISION");
		noDomainList.whereCond("1 = 0");
		String listError;
		try {
			noDomainList.readList();
			listError = "no error";
		} catch (IllegalStateException | SQLException exc) {
			listError = exc.getMessage();
		}
		report(listError.equals("The columns of the select have no domain"),
				"Columns without domain fail before executing, even with no rows", listError,
				"The columns of the select have no domain");

		System.out.println("== LEFT JOIN of CMA tables");
		Catalog catalog = new Catalog();
		catalog.addProvider(new CatalogCMA());
		Table businesses = catalog.getTable(CatalogCMA.SCHEMA, "BUSINESSES");
		Table divisions = catalog.getTable(CatalogCMA.SCHEMA, "DIVISIONS");
		select = new QSelect(SQLSERVER);
		select.from(businesses, "U");
		select.leftJoin(divisions, "D", "U.CDIVISION = D.CDIVISION AND D.CDIVISION = 'NONE'");
		select.addColumn("U.CBUSINESS", "CBUSINESS", new Domain("CBUSINESS", Types.STRING, 10, -1));
		select.addColumn("D.DDIVISION", "DDIVISION", new Domain("DDIVISION", Types.STRING, 60, -1));
		select.addColumn("U.NROUNDCALC", "NROUNDCALC", new Domain("NROUNDCALC", Types.DECIMAL, 6, 2));
		select.addColumn("D.CDIVISION", "CDIVISION", new Domain("CDIVISION", Types.STRING, 10, -1));
		select.orderBy("CBUSINESS");
		result = read(select, select.toString());
		String[] joined = result.split("\n--\n");
		boolean ok = !result.contains("Exception");
		for (String row : joined) {
			ok &= row.contains("DDIVISION STRING \n") && row.endsWith("CDIVISION STRING ");
		}
		StringBuilder shown = new StringBuilder();
		shown.append(joined.length);
		shown.append(" rows, unmatched join columns read as empty strings, first row:\n");
		shown.append(joined[0]);
		report(ok, "Nulls of a LEFT JOIN without match", ok ? shown.toString() : result, "empty strings for D columns");

		System.out.println("== Errors");
		QSelect noDomains = new QSelect(SQLSERVER);
		noDomains.from(vals, "V");
		noDomains.addColumn("V.NQTY", "NQTY");
		String error;
		try {
			noDomains.getValues(null);
			error = "no error";
		} catch (IllegalStateException exc) {
			error = exc.getMessage();
		} catch (SQLException exc) {
			error = exc.getMessage();
		}
		report(error.equals("The columns of the select have no domain"), "Columns without domain", error,
				"The columns of the select have no domain");

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
