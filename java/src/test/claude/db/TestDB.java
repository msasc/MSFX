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

package test.claude.db;

import static test.claude.Check.*;
import static test.claude.Fixture.*;

import java.io.IOException;
import java.io.StringReader;
import java.sql.SQLException;
import java.util.List;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBPreparedStatement;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.query.QSelect;
import msfx.lib.md.Catalog;
import msfx.lib.md.Column;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import msfx.lib.util.json.JSONObject;
import msfx.lib.util.json.JSONParser;
import test.claude.Check;
import test.db.CatalogCMA;

/**
 * Tests the database layer on the local SQL Server: the booleans and objects
 * read by {@link DBResultSet}, the booleans and objects sent by
 * {@link DBPreparedStatement}, over selects of literals that read no table;
 * and the CMA catalog against the local copy, each table selected with all its
 * columns and WHERE 1 = 0, so that the database checks every column without
 * reading rows. Only SELECT statements are executed. Everything needs the
 * database.
 *
 * @author Miquel Sas
 */
public class TestDB {

	/**
	 * Checks getBoolean, by index and by alias.
	 */
	private static void booleans() {
		section("Booleans read");
		String[] exprs = {
				"'Y'", "'y'", "'N'", "'n'",
				"'1'", "'0'",
				"'T'", "'t'", "'F'", "'f'",
				"CAST(1 AS BIT)", "CAST(0 AS BIT)",
				"'S'", "'2'", "''", "' '", "NULL" };
		boolean[] expected = {
				true, true, false, false,
				true, false,
				true, true, false, false,
				true, false,
				false, false, false, false, false };
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT ");
		for (int i = 0; i < exprs.length; i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append(exprs[i]);
			sql.append(" AS C");
			sql.append(i + 1);
		}
		StringBuilder result = new StringBuilder();
		StringBuilder exp = new StringBuilder();
		try (DBConnection cn = SQLSERVER.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql.toString())) {
			rs.next();
			for (int i = 0; i < exprs.length; i++) {
				StringBuilder alias = new StringBuilder();
				alias.append("C");
				alias.append(i + 1);
				boolean byIndex = rs.getBoolean(i + 1);
				boolean byAlias = rs.getBoolean(alias.toString());
				result.append(exprs[i]);
				result.append(" ");
				result.append(byIndex);
				result.append("/");
				result.append(byAlias);
				result.append("\n");
				exp.append(exprs[i]);
				exp.append(" ");
				exp.append(expected[i]);
				exp.append("/");
				exp.append(expected[i]);
				exp.append("\n");
			}
		} catch (SQLException exc) {
			result.append(exc.getMessage());
		}
		equal("Y/N, 1/0 and T/F in any case and bits read, the rest false; by index and by alias", result.toString(),
				exp.toString());

		String trueSQL = SQLSERVER.getLiteralSQL(true);
		String falseSQL = SQLSERVER.getLiteralSQL(false);
		StringBuilder roundTrip = new StringBuilder();
		roundTrip.append("SELECT ");
		roundTrip.append(trueSQL);
		roundTrip.append(" AS T, ");
		roundTrip.append(falseSQL);
		roundTrip.append(" AS F");
		StringBuilder literals = new StringBuilder();
		literals.append(trueSQL);
		literals.append("/");
		literals.append(falseSQL);
		literals.append(" read ");
		try (DBConnection cn = SQLSERVER.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(roundTrip.toString())) {
			rs.next();
			literals.append(rs.getBoolean(1));
			literals.append("/");
			literals.append(rs.getBoolean(2));
		} catch (SQLException exc) {
			literals.append(exc.getMessage());
		}
		equal("Literals written and read back", literals.toString(), "'Y'/'N' read true/false");
	}

	/**
	 * Reads a value by index and its copy by alias, since a streamed column is
	 * read only once, and describes the result: the object with its keys, null,
	 * or the error and its cause.
	 *
	 * @param rs    The result set.
	 * @param index The index of the value, read from column C[index], its copy
	 *              read from column A[index].
	 * @return The description of the reads by index and by alias.
	 */
	private static String readObject(DBResultSet rs, int index) {
		StringBuilder alias = new StringBuilder();
		alias.append("A");
		alias.append(index);
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 2; i++) {
			if (i > 0) {
				b.append("\n");
			}
			try {
				JSONObject obj = i == 0 ? rs.getObject(index) : rs.getObject(alias.toString());
				if (obj == null) {
					b.append("null");
				} else {
					b.append(obj.size());
					b.append(" keys ");
					b.append(obj.keys());
				}
			} catch (SQLException exc) {
				b.append("SQLException: ");
				b.append(exc.getMessage());
				b.append(" / cause ");
				b.append(exc.getCause() == null ? "none" : exc.getCause().getClass().getSimpleName());
			}
		}
		return b.toString();
	}

	/**
	 * Checks getObject, by index and by alias.
	 */
	private static void objects() {
		section("Objects read");
		String[] texts = {
				"N'{\"CCOMPANY\":\"LCZ\",\"QSALES\":10.5,\"ITEMS\":[1,2]}'",
				"N''",
				"NULL",
				"N'{\"CCOMPANY\":'",
				"N'[1,2]'" };
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT ");
		for (int i = 0; i < texts.length; i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append("CAST(");
			sql.append(texts[i]);
			sql.append(" AS NVARCHAR(MAX)) AS C");
			sql.append(i + 1);
		}
		for (int i = 0; i < texts.length; i++) {
			sql.append(", CAST(");
			sql.append(texts[i]);
			sql.append(" AS NVARCHAR(MAX)) AS A");
			sql.append(i + 1);
		}
		try (DBConnection cn = SQLSERVER.getConnection();
				DBStatement st = cn.createStatement();
				DBResultSet rs = st.executeQuery(sql.toString())) {
			rs.next();
			String result = readObject(rs, 1);
			equal("JSON object", result, "3 keys [CCOMPANY, QSALES, ITEMS]\n3 keys [CCOMPANY, QSALES, ITEMS]");
			result = readObject(rs, 2);
			equal("Empty text: empty object", result, "0 keys []\n0 keys []");
			result = readObject(rs, 3);
			equal("Null: null", result, "null\nnull");
			result = readObject(rs, 4);
			report(result.startsWith("SQLException: The value of column 4 is not a JSON object")
					&& result.contains("SQLException: The value of column A4 is not a JSON object")
					&& result.endsWith("cause IOException"), "Malformed text: SQLException with the parse error as cause",
					result, "SQLException caused by IOException");
			result = readObject(rs, 5);
			report(result.startsWith("SQLException: The value of column 5 is not a JSON object")
					&& result.endsWith("cause IOException"), "An array: not an object", result,
					"SQLException caused by IOException");
		} catch (SQLException exc) {
			report(false, "Select", exc.getMessage(), "no error");
		}
	}

	/**
	 * Checks setBoolean, setObject and setNull, read back.
	 */
	private static void setters() {
		section("Parameters sent");
		JSONObject obj;
		try {
			StringReader source = new StringReader("{\"CCOMPANY\":\"LCZ\",\"QSALES\":10.5}");
			obj = new JSONParser().parse(source);
		} catch (IOException exc) {
			report(false, "Test object", exc.getMessage(), "parsed");
			return;
		}
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT CAST(? AS VARCHAR(1)) AS BTRUE, CAST(? AS VARCHAR(1)) AS BFALSE, ");
		sql.append("CAST(? AS NVARCHAR(MAX)) AS OTEXT, CAST(? AS NVARCHAR(MAX)) AS OVALUE, ");
		sql.append("CAST(? AS NVARCHAR(MAX)) AS ONULL, CAST(? AS VARCHAR(1)) AS BNULL");
		try (DBConnection cn = SQLSERVER.getConnection();
				DBPreparedStatement ps = cn.prepareStatement(sql.toString())) {
			ps.setBoolean(1, true);
			ps.setBoolean(2, false);
			ps.setObject(3, obj);
			ps.setObject(4, obj);
			ps.setObject(5, null);
			ps.setNull(6, Types.BOOLEAN);
			try (DBResultSet rs = ps.executeQuery()) {
				rs.next();
				StringBuilder booleans = new StringBuilder();
				booleans.append(rs.getString(1));
				booleans.append("/");
				booleans.append(rs.getString(2));
				booleans.append(", read back ");
				booleans.append(rs.getBoolean("BTRUE"));
				equal("setBoolean sends Y/N", booleans.toString(), "Y/N, read back true");
				String text = rs.getString(3);
				equal("setObject sends the JSON text", text, "{\"CCOMPANY\":\"LCZ\",\"QSALES\":10.5}");
				JSONObject read = rs.getObject(4);
				equal("read back with getObject", String.valueOf(read.keys()), "[CCOMPANY, QSALES]");
				JSONObject none = rs.getObject(5);
				String bNull = rs.getString(6);
				StringBuilder nulls = new StringBuilder();
				nulls.append(none);
				nulls.append("/");
				nulls.append(bNull);
				equal("setObject(null) and setNull(BOOLEAN) send NULL", nulls.toString(), "null/null");
			}
		} catch (SQLException exc) {
			report(false, "Select", exc.getMessage(), "no error");
		}
	}

	/**
	 * Checks every table of the CMA catalog against the local copy.
	 */
	private static void catalog() {
		section("CMA catalog against the local copy");
		Catalog catalog = new Catalog();
		catalog.addProvider(new CatalogCMA());
		List<String> names = catalog.getAllTableNames(CatalogCMA.SCHEMA);
		int columns = 0;
		StringBuilder failures = new StringBuilder();
		for (String name : names) {
			try {
				Table table = catalog.getTable(CatalogCMA.SCHEMA, name);
				QSelect select = new QSelect(SQLSERVER);
				select.from(table, "T");
				for (Column column : table.columns()) {
					StringBuilder expr = new StringBuilder();
					expr.append("T.");
					expr.append(column.getName());
					select.addColumn(expr.toString(), column.getName());
				}
				select.getWhere().cond("1 = 0");
				try (DBConnection cn = SQLSERVER.getConnection();
						DBStatement st = cn.createStatement();
						DBResultSet rs = st.executeQuery(select.toString())) {
					int count = rs.getMetaData().getColumnCount();
					columns += count;
					if (count != table.columns().size()) {
						failures.append(name);
						failures.append(": ");
						failures.append(count);
						failures.append(" columns read\n");
					}
				}
			} catch (SQLException | RuntimeException exc) {
				failures.append(name);
				failures.append(": ");
				failures.append(exc.getClass().getSimpleName());
				failures.append(": ");
				failures.append(exc.getMessage());
				failures.append("\n");
			}
		}
		StringBuilder result = new StringBuilder();
		result.append(names.size());
		result.append(" tables, ");
		result.append(columns);
		result.append(" columns");
		if (!failures.isEmpty()) {
			result.append("\n");
			result.append(failures);
		}
		report(failures.isEmpty(), "Every table selected with all its columns", result.toString(), "no failures");
	}

	/**
	 * Runs the checks.
	 *
	 * @param db True to run them: they all need the local SQL Server.
	 */
	public static void run(boolean db) {
		Check.start("TestDB");
		if (!db) {
			section("Skipped: needs the database");
			return;
		}
		connect();
		booleans();
		objects();
		setters();
		catalog();
	}

	/**
	 * Entry point.
	 *
	 * @param args Not used: the checks always run on the local SQL Server.
	 */
	public static void main(String[] args) {
		run(true);
		summary();
		System.exit(0);
	}
}
