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

import static test.claude.Check.*;
import static test.claude.Fixture.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.query.QSelect;
import msfx.lib.md.Column;
import msfx.lib.md.ColumnList;
import msfx.lib.md.DataType;
import msfx.lib.md.Domain;
import msfx.lib.md.Record;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import msfx.lib.md.Value;
import test.claude.Check;

/**
 * Tests reading rows with a {@link QSelect}: the columns of the records, given
 * as domains or columns, that refer to their table when they are table
 * columns; and, with "db", on the local SQL Server, the values read for each
 * type, records, readList and readCallback with its exceptions. Only SELECT
 * statements are executed.
 *
 * @author Miquel Sas
 */
public class TestQSelectRecords {

	/**
	 * The table TPIECES.
	 */
	private static Table pieces;
	/**
	 * The table UNITS.
	 */
	private static Table units;

	/**
	 * Describes a column of a list: alias, name, type, length, decimals, label
	 * and table.
	 *
	 * @param list  The column list.
	 * @param index The index.
	 * @return The description.
	 */
	private static String describe(ColumnList list, int index) {
		Column column = list.get(index);
		DataType dataType = column.getDataType();
		Table table = column.getTable();
		StringBuilder b = new StringBuilder();
		b.append(list.getAlias(index));
		b.append(" ");
		b.append(column.getName());
		b.append(" ");
		b.append(dataType.type());
		b.append(" ");
		b.append(dataType.length());
		b.append(" ");
		b.append(dataType.decimals());
		b.append(" ");
		b.append(column.getLabel());
		b.append(" ");
		b.append(table == null ? "no table" : table.getName());
		return b.toString();
	}

	/**
	 * Describes all the columns of a list, one per line.
	 *
	 * @param list The column list.
	 * @return The description.
	 */
	private static String describe(ColumnList list) {
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			String line = describe(list, i);
			lines.add(line);
		}
		return String.join("\n", lines);
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
	 * Reads the rows of a select with getValues, validates each one against the
	 * column list with {@link Record#validate(ColumnList, Value[])}, and
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
	 * Returns a select of the units, by code.
	 *
	 * @return The select.
	 */
	private static QSelect allUnits() {
		QSelect select = new QSelect(SQLSERVER);
		select.from(units, "U");
		select.addColumn("U.CUNIT", "CUNIT");
		select.addColumn("U.DUNIT", "DUNIT");
		select.orderBy("CUNIT");
		return select;
	}

	/**
	 * Checks the columns of the records: given as domains, as table columns or
	 * as free columns, and the errors.
	 */
	private static void recordColumns() {
		section("Columns of the records");
		Column cptype = pieces.columns().get("CPTYPE");
		Column dptype = pieces.columns().get("DPTYPE");
		Domain unit = pieces.columns().get("CUNIT").getDomain();

		Column copy = new Column(cptype);
		copy.setLabel("Changed");
		StringBuilder copyResult = new StringBuilder();
		copyResult.append(copy.getTable().getName());
		copyResult.append(", ");
		copyResult.append(copy.equals(cptype) ? "equal" : "different");
		copyResult.append(", ");
		copyResult.append(copy != cptype ? "another object" : "same object");
		copyResult.append(", original label ");
		copyResult.append(cptype.getLabel());
		equal("Copy of a column, keeping its table", copyResult.toString(),
				"TPIECES, equal, another object, original label Type of piece code");

		QSelect select = fromPieces();
		select.addColumn("P.CPTYPE", "CPTYPE", cptype);
		select.addColumn("(P.DPTYPE)", "DESCRIPTION", dptype);
		select.addColumn("UPPER(P.DPTYPE)", "UPPER_DESC", new Column("UPPER_DESC", dptype.getDomain()));
		select.addColumn("P.CUNIT", "Unit", unit);
		ColumnList list = select.getColumnList();
		equal("Table columns, a free column and a domain, in order, under their aliases", describe(list), lines(
				"CPTYPE CPTYPE STRING 20 -1 Type of piece code TPIECES",
				"DESCRIPTION DPTYPE STRING 60 -1 Type of piece description TPIECES",
				"UPPER_DESC UPPER_DESC STRING 60 -1 Type of piece description no table",
				"Unit Unit STRING 6 -1 Unit code no table"));
		equal("The list of the select, shared", list == select.getColumnList() ? "same list" : "another list", "same list");
		equal("A copy of the table column, equal to it",
				String.valueOf(list.get(0) != cptype && list.get(0).equals(cptype)), "true");
		list.get(0).setLabel("Piece type");
		list.get(3).setLabel("Unit changed");
		StringBuilder labels = new StringBuilder();
		labels.append(cptype.getLabel());
		labels.append(", ");
		labels.append(unit.getLabel());
		equal("Labels changed in the select, not in the table nor the domain", labels.toString(),
				"Type of piece code, Unit code");
		equal("SQL unchanged, parentheses kept", select.toString().split("\n")[0],
				"SELECT [P].[CPTYPE] AS [CPTYPE], ([P].[DPTYPE]) AS [DESCRIPTION], UPPER([P].[DPTYPE]) AS [UPPER_DESC], [P].[CUNIT] AS [Unit]");

		error("Table column for an aggregate", IllegalArgumentException.class, () -> {
			fromPieces().addColumn("MAX(P.DPTYPE)", "DPTYPE", dptype);
		});
		error("Table column for another column", IllegalArgumentException.class, () -> {
			fromPieces().addColumn("P.CPTYPE", "CPTYPE", dptype);
		});
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
		error("A column without domain after columns with one", IllegalArgumentException.class, () -> {
			QSelect s = fromPieces();
			s.addColumn("P.CPTYPE", "CPTYPE", cptype);
			s.addColumn("P.DPTYPE", "DPTYPE");
		});
		error("A column with domain after columns without", IllegalArgumentException.class, () -> {
			QSelect s = fromPieces();
			s.addColumn("P.CPTYPE", "CPTYPE");
			s.addColumn("P.CUNIT", "CUNIT", unit);
		});
		error("Columns without domain", IllegalStateException.class, () -> {
			QSelect s = fromPieces();
			s.addColumn("P.CPTYPE", "CPTYPE");
			s.getColumnList();
		});
		error("No columns", IllegalStateException.class, () -> {
			fromPieces().getColumnList();
		});
		error("Values of columns without domain", IllegalStateException.class, () -> {
			QSelect s = fromPieces();
			s.addColumn("P.CPTYPE", "CPTYPE");
			s.getValues(null);
		});

		select = fromPieces();
		select.addColumn("P.CPTYPE", "CPTYPE", cptype);
		try {
			select.addColumn("P.DPTYPE", "DPTYPE");
		} catch (IllegalArgumentException exc) {
			/* Expected. */
		}
		equal("A rejected column leaves the select as it was", describe(select.getColumnList()),
				"CPTYPE CPTYPE STRING 20 -1 Type of piece code TPIECES");
	}

	/**
	 * Checks the checks of readCallback before executing.
	 */
	private static void readChecks() {
		section("Checks before reading");
		error("No FROM clause", IllegalStateException.class, () -> {
			new QSelect(SQLSERVER).readCallback(rs -> true);
		});
		error("No columns", IllegalStateException.class, () -> {
			fromPieces().readCallback(rs -> true);
		});
		error("EXISTS select executed alone", IllegalStateException.class, () -> {
			QSelect e = fromPieces().getWhere().createExists();
			e.from(units, "U");
			e.addColumn("1", "NREF");
			e.readCallback(rs -> true);
		});
		error("Aggregates and other expressions without groupBy()", IllegalStateException.class, () -> {
			QSelect s = fromPieces();
			s.addColumn("P.CUNIT", "CUNIT");
			s.addColumn("COUNT(*)", "NPIECES");
			s.readCallback(rs -> true);
		});
		error("Null callback", NullPointerException.class, () -> {
			allUnits().readCallback(null);
		});
	}

	/**
	 * Checks the values read for each type, from a derived table of literals
	 * written in place of the table of the select, and the records built with
	 * them.
	 */
	private static void values() {
		section("Values of each type, and nulls");
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
		String result = read(select, sql);
		equal("Values typed by the domains, decimals of the domains, nulls converted, not modified", result, lines(
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
				"OJSON OBJECT null"));

		List<Record> records = new ArrayList<>();
		try (DBConnection cn = SQLSERVER.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) {
				Record record = select.getRecord(rs);
				records.add(record);
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
		equal("getRecord: the column list of the select and the values of the row", recordResult.toString(), lines(
				"shared columns, QPRICE 12.35, CNAME 'LCZ', 13 values",
				"shared columns, QPRICE 0.00, CNAME '', 13 values",
				""));

		Table businesses = table("BUSINESSES");
		Table divisions = table("DIVISIONS");
		select = new QSelect(SQLSERVER);
		select.from(businesses, "U");
		select.leftJoin(divisions, "D", "U.CDIVISION = D.CDIVISION AND D.CDIVISION = 'NONE'");
		select.addColumn("U.CBUSINESS", "CBUSINESS", businesses.columns().get("CBUSINESS"));
		select.addColumn("D.DDIVISION", "DDIVISION", divisions.columns().get("DDIVISION"));
		select.addColumn("U.NROUNDCALC", "NROUNDCALC", businesses.columns().get("NROUNDCALC"));
		select.addColumn("D.CDIVISION", "CDIVISION", divisions.columns().get("CDIVISION"));
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
	}

	/**
	 * Checks readList and readCallback: all the rows, stopping early, and the
	 * exceptions of the callback.
	 */
	private static void reads() {
		section("Reads");
		String expected = rows("SELECT CUNIT, DUNIT FROM EXPLMARG_QLT.UNITS ORDER BY CUNIT");
		String firstUnit = expected.substring(0, expected.indexOf(' '));

		QSelect listed = new QSelect(SQLSERVER);
		listed.from(units, "U");
		listed.addColumn("U.CUNIT", "CUNIT", units.columns().get("CUNIT"));
		listed.addColumn("U.DUNIT", "DUNIT", units.columns().get("DUNIT"));
		listed.orderBy("CUNIT");
		String result;
		try {
			List<Record> list = listed.readList();
			StringBuilder b = new StringBuilder();
			for (Record record : list) {
				b.append(record.columns() == listed.getColumnList() ? "" : "OWN COLUMNS ");
				b.append(record.getValue("CUNIT"));
				b.append(" ");
				b.append(record.getValue("DUNIT"));
				b.append("\n");
			}
			result = b.toString();
		} catch (SQLException exc) {
			result = exc.getMessage();
		}
		report(!result.isEmpty() && result.equals(expected), "readList: all the rows as records, sharing the column list",
				result, expected);

		StringBuilder all = new StringBuilder();
		QSelect.Callback allRows = rs -> {
			all.append(rs.getString("CUNIT"));
			all.append(" ");
			all.append(rs.getString("DUNIT"));
			all.append("\n");
			return true;
		};
		try {
			allUnits().readCallback(allRows);
			result = all.toString();
		} catch (SQLException exc) {
			result = exc.getMessage();
		}
		equal("readCallback: all the rows, by alias", result, expected);

		StringBuilder first = new StringBuilder();
		QSelect.Callback firstRow = rs -> {
			first.append(rs.getString("CUNIT"));
			return false;
		};
		try {
			allUnits().readCallback(firstRow);
			result = first.toString();
		} catch (SQLException exc) {
			result = exc.getMessage();
		}
		equal("Stopped after the first row", result, firstUnit);

		error("Checked exception of the callback, as the cause", SQLException.class, IOException.class, () -> {
			allUnits().readCallback(rs -> {
				throw new IOException("Disk full");
			});
		});
		error("SQLException of the callback, not wrapped", SQLException.class, () -> {
			allUnits().readCallback(rs -> {
				rs.getString("UNKNOWN_ALIAS");
				return true;
			});
		});
		error("Runtime exception of the callback, not wrapped", IllegalStateException.class, () -> {
			allUnits().readCallback(rs -> {
				throw new IllegalStateException("A bug");
			});
		});

		boolean interrupted;
		try {
			allUnits().readCallback(rs -> {
				throw new InterruptedException("Stop");
			});
			interrupted = false;
		} catch (SQLException exc) {
			interrupted = Thread.interrupted() && exc.getCause() instanceof InterruptedException;
		}
		equal("InterruptedException: wrapped and the interrupted flag set again", String.valueOf(interrupted), "true");

		StringBuilder again = new StringBuilder();
		QSelect.Callback againRow = rs -> {
			again.append(rs.getString("CUNIT"));
			return false;
		};
		try {
			for (int i = 0; i < 50; i++) {
				again.setLength(0);
				allUnits().readCallback(againRow);
			}
			result = again.toString();
		} catch (SQLException exc) {
			result = exc.getMessage();
		}
		equal("50 more reads after failed ones: connections returned to the pool", result, firstUnit);
	}

	/**
	 * Runs the checks.
	 *
	 * @param db True to also read on the local SQL Server.
	 */
	public static void run(boolean db) {
		Check.start("TestQSelectRecords");
		pieces = table("TPIECES");
		units = table("UNITS");
		recordColumns();
		readChecks();
		if (db) {
			connect();
			values();
			reads();
		}
	}

	/**
	 * Entry point.
	 *
	 * @param args Pass "db" to also read on the local SQL Server.
	 */
	public static void main(String[] args) {
		boolean db = args.length > 0 && args[0].equals("db");
		run(db);
		summary();
		System.exit(0);
	}
}
