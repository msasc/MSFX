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

import static test.claude.Check.*;

import java.util.HashSet;
import java.util.Set;

import msfx.lib.db.DBEngine;
import msfx.lib.db.adapters.DBEngineOracle;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.md.Column;
import msfx.lib.md.DataType;
import msfx.lib.md.Domain;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import msfx.lib.md.Value;
import msfx.lib.util.Copier;
import test.claude.Check;
import test.db.CatalogCMA;

/**
 * Tests the metadata objects themselves, built by hand on purpose: the
 * equality by value of data types, domains, columns and tables, the database
 * keys read by the adapters, the domain of a column, and the lookup of a
 * column list. Nothing is executed: connectTo only keeps the settings.
 *
 * @author Miquel Sas
 */
public class TestMetadata {

	/**
	 * Checks a boolean result.
	 *
	 * @param title    What is checked.
	 * @param result   The result.
	 * @param expected The expected result.
	 */
	private static void check(String title, boolean result, boolean expected) {
		equal(title, String.valueOf(result), String.valueOf(expected));
	}

	/**
	 * Connects an engine and returns its database key, or the error.
	 *
	 * @param db       The engine, not connected.
	 * @param cnString The connection string.
	 * @return The key, or the class and message of the error.
	 */
	private static String key(DBEngine db, String cnString) {
		try {
			db.connectTo(cnString, "user", "password");
			String key = db.getDatabaseKey();
			return key;
		} catch (RuntimeException exc) {
			StringBuilder b = new StringBuilder();
			b.append(exc.getClass().getSimpleName());
			b.append(": ");
			b.append(exc.getMessage());
			return b.toString();
		}
	}

	/**
	 * Returns an engine connected to a database of the local SQL Server, with
	 * the CMA catalog.
	 *
	 * @param database The database name.
	 * @return The engine.
	 */
	private static DBEngine engine(String database) {
		DBEngine db = new DBEngineSQLServer();
		StringBuilder cnString = new StringBuilder();
		cnString.append("localhost:1433;databaseName=");
		cnString.append(database);
		db.connectTo(cnString.toString(), "sa", "SqlPassword2026!");
		db.getCatalog().addProvider(new CatalogCMA());
		return db;
	}

	/**
	 * Describes a domain or a column: name, data type, header, label,
	 * description, possible values and default value, as its getters return
	 * them, and for a column the nullable flag and the table.
	 *
	 * @param domain The domain.
	 * @param column The column, or null to describe the domain.
	 * @return The description.
	 */
	private static String describe(Domain domain, Column column) {
		StringBuilder b = new StringBuilder();
		if (column == null) {
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
	 * Checks the equality of data types, domains and columns.
	 */
	private static void equality() {
		section("Equality of data types, domains and columns");
		DataType dt1 = new DataType(Types.STRING, 10, -1);
		DataType dt2 = new DataType(Types.STRING, 10, -1);
		DataType dt3 = new DataType(Types.STRING, 20, -1);
		check("Data types: same type, length and decimals", dt1.equals(dt2) && dt1.hashCode() == dt2.hashCode(), true);
		check("Data types: another length", dt1.equals(dt3), false);

		Domain d1 = new Domain("CCOMPANY", Types.STRING, 10, -1);
		Domain d2 = new Domain("ccompany", Types.STRING, 10, -1);
		d2.setLabel("Company");
		d2.setHeader("Comp");
		Domain d3 = new Domain("CCOMPANY", Types.STRING, 12, -1);
		Domain d4 = new Domain("CBUSINESS", Types.STRING, 10, -1);
		check("Domains: name ignoring case, other properties not compared, same hash",
				d1.equals(d2) && d1.hashCode() == d2.hashCode(), true);
		check("Domains: another length", d1.equals(d3), false);
		check("Domains: another name", d1.equals(d4), false);
		Domain d1Copy = new Copier().copy(d1);
		check("Domains: a copy", d1.equals(d1Copy) && d1 != d1Copy, true);

		Column c1 = new Column("QSALES", Types.DECIMAL, 16, 2);
		Column c2 = new Column("qsales", Types.DECIMAL, 16, 2);
		c2.setLabel("Sales");
		Column c3 = new Column("QSALES", Types.DECIMAL, 16, 4);
		check("Columns without table: name ignoring case, label not compared, same hash",
				c1.equals(c2) && c1.hashCode() == c2.hashCode(), true);
		check("Columns: other decimals", c1.equals(c3), false);

		Table sales = new Table("SALES");
		Set<Column> set = new HashSet<>();
		set.add(c1);
		int hashBefore = c1.hashCode();
		sales.columns().add(c1);
		check("Hash code unchanged when added to a table, still found in a hash set",
				c1.hashCode() == hashBefore && set.contains(c1), true);
		check("A column in a table and one without", c1.equals(c2), false);
		Table other = new Table("SALES_OLD");
		other.columns().add(c2);
		check("Columns of two different tables", c1.equals(c2), false);

		Column c4 = new Column("QSALES", Types.DECIMAL, 16, 2);
		Column c5 = new Column("QSALES", Types.DECIMAL, 16, 2);
		Table t = new Table("T");
		t.columns().add(c4);
		Column c4Copy = new Copier().copy(c4);
		check("A copy through the Copier belongs to no table, so it differs", c4.equals(c4Copy), false);
		check("Two columns without table, same structure", c5.equals(c4Copy), true);
		Column fromDomain = new Column(c1.getDomain());
		check("Column from the domain of a column: no table", fromDomain.equals(c5) && !fromDomain.equals(c2), true);
	}

	/**
	 * Checks the database keys read by the adapters, and the equality of
	 * tables.
	 */
	private static void tables() {
		section("Database keys");
		equal("SQL Server: host, port and database", key(new DBEngineSQLServer(), "localhost:1433;databaseName=sql_cma_qlt"),
				"SQLSERVER:LOCALHOST:1433:SQL_CMA_QLT");
		equal("SQL Server: default port, property database, other options", key(new DBEngineSQLServer(),
				"dbserver;encrypt=true;database=Sales;trustServerCertificate=true"), "SQLSERVER:DBSERVER:1433:SALES");
		equal("SQL Server: named instance", key(new DBEngineSQLServer(), "host\\SQLEXPRESS:1435;databaseName=cma"),
				"SQLSERVER:HOST\\SQLEXPRESS:1435:CMA");
		String missing = key(new DBEngineSQLServer(), "localhost:1433;encrypt=false");
		check("SQL Server: no database", missing.startsWith("IllegalArgumentException"), true);
		equal("Oracle: service", key(new DBEngineOracle(), "orahost:1521/CMAPDB"), "ORACLE:ORAHOST:1521:CMAPDB");
		equal("Oracle: SID", key(new DBEngineOracle(), "orahost:1522:orcl"), "ORACLE:ORAHOST:1522:ORCL");
		equal("Oracle: // and default port", key(new DBEngineOracle(), "//orahost/cmapdb"), "ORACLE:ORAHOST:1521:CMAPDB");
		String descriptor = key(new DBEngineOracle(), "(DESCRIPTION=(ADDRESS=(HOST=orahost)))");
		check("Oracle: TNS descriptor not read", descriptor.startsWith("IllegalArgumentException"), true);
		DBEngine once = new DBEngineSQLServer();
		key(once, "localhost:1433;databaseName=a");
		String twice = key(once, "localhost:1433;databaseName=b");
		StringBuilder onceResult = new StringBuilder();
		onceResult.append(twice.startsWith("IllegalStateException") ? "second call rejected" : twice);
		onceResult.append(", ");
		onceResult.append(once.getDatabaseKey());
		onceResult.append(", catalog ");
		onceResult.append(once.getCatalog().getDatabaseKey());
		equal("connectTo once, the key kept and given to the catalog", onceResult.toString(),
				"second call rejected, SQLSERVER:LOCALHOST:1433:A, catalog SQLSERVER:LOCALHOST:1433:A");

		section("Equality of tables");
		DBEngine front = engine("sql_cma_qlt");
		DBEngine back = engine("SQL_CMA_QLT");
		DBEngine another = engine("sql_cma_prd");
		Table frontPieces = front.getCatalog().getTable(CatalogCMA.SCHEMA, "TPIECES");
		Table backPieces = back.getCatalog().getTable(CatalogCMA.SCHEMA, "TPIECES");
		Table otherPieces = another.getCatalog().getTable(CatalogCMA.SCHEMA, "TPIECES");
		Table frontUnits = front.getCatalog().getTable(CatalogCMA.SCHEMA, "UNITS");
		equal("Key set by the catalog", frontPieces.getDatabaseKey(), "SQLSERVER:LOCALHOST:1433:SQL_CMA_QLT");
		check("Same table from two engines of the same database",
				frontPieces != backPieces && frontPieces.equals(backPieces) && frontPieces.hashCode() == backPieces.hashCode(),
				true);
		check("Same table of another database", frontPieces.equals(otherPieces), false);
		check("Another table", frontPieces.equals(frontUnits), false);
		Column frontColumn = frontPieces.columns().get("CPTYPE");
		Column backColumn = backPieces.columns().get("CPTYPE");
		Column otherColumn = otherPieces.columns().get("CPTYPE");
		check("Same column through the two engines", frontColumn.equals(backColumn), true);
		check("Same column of another database", frontColumn.equals(otherColumn), false);

		Table lower = new Table("sales");
		Table upper = new Table("SALES");
		check("Tables on the fly, by name ignoring case", lower.equals(upper), true);
		Set<Table> set = new HashSet<>();
		set.add(lower);
		int hash = lower.hashCode();
		lower.setSchema("dbo");
		lower.setDatabaseKey("SQLSERVER:LOCALHOST:1433:X");
		check("Hash code unchanged after schema and key", lower.hashCode() == hash && set.contains(lower), true);
		check("With schema and key against one without", lower.equals(upper), false);
		upper.setSchema("DBO");
		upper.setDatabaseKey("SQLSERVER:LOCALHOST:1433:X");
		check("Same key, schema ignoring case", lower.equals(upper), true);
	}

	/**
	 * Checks the domain of a column and the columns created from it.
	 */
	private static void domains() {
		section("Domain of a column");
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

		Domain statusDomain = status.getDomain();
		equal("Explicit values only: no header fallback, no description fallback", describe(statusDomain, null), lines(
				"CSTATUS DataType[type=STRING, length=1, decimals=-1]",
				"header null, label Status, description null",
				"possible {A=Active, B=Blocked}, default B"));
		check("The data type is shared", statusDomain.getDataType() == status.getDataType(), true);
		equal("A plain column", describe(quantity.getDomain(), null), lines(
				"QSALES DataType[type=DECIMAL, length=16, decimals=2]",
				"header null, label null, description null",
				"possible {}, default null"));
		Column createdCopy = new Column("DSTART", created.getDomain());
		equal("No nullable flag: a column from the domain is nullable as its type", describe(null, createdCopy), lines(
				"DSTART DataType[type=DATE, length=-1, decimals=-1]",
				"header DSTART, label DSTART, description DSTART",
				"possible {}, default null, nullable true, table none"));
		Column alias = new Column("CSTATE", statusDomain);
		equal("Same definition under another name, fallbacks to the new name", describe(null, alias), lines(
				"CSTATE DataType[type=STRING, length=1, decimals=-1]",
				"header CSTATE, label Status, description Status",
				"possible {A=Active, B=Blocked}, default B, nullable false, table none"));
		Domain first = status.getDomain();
		status.setHeader("St");
		Domain second = status.getDomain();
		StringBuilder headers = new StringBuilder();
		headers.append(first.getHeader());
		headers.append(", ");
		headers.append(second.getHeader());
		headers.append(", ");
		headers.append(first == second ? "same domain" : "new domain");
		equal("A new domain on each call, with the current definition", headers.toString(), "null, St, new domain");
	}

	/**
	 * Checks the lookup ignoring case in a column list with many columns.
	 */
	private static void columnList() {
		section("Column list with many columns");
		Table wideTable = new Table("WIDE");
		long start = System.nanoTime();
		for (int i = 1; i <= 2000; i++) {
			StringBuilder name = new StringBuilder();
			name.append("COLUMN_");
			name.append(i);
			wideTable.columns().add(new Column(name.toString(), Types.STRING, 10, -1));
		}
		boolean found = true;
		for (int i = 1; i <= 2000; i++) {
			StringBuilder name = new StringBuilder();
			name.append("column_");
			name.append(i);
			found &= wideTable.columns().getIgnoreCase(name.toString()) != null;
		}
		long millis = (System.nanoTime() - start) / 1_000_000;
		StringBuilder result = new StringBuilder();
		result.append("2000 columns added and found ignoring case in ");
		result.append(millis);
		result.append(" ms");
		report(found, "Lookup ignoring case", result.toString(), "all found");
		error("Name that differs only in case", IllegalArgumentException.class, () -> {
			wideTable.columns().add(new Column("Column_7", Types.STRING, 10, -1));
		});
	}

	/**
	 * Runs the checks.
	 */
	public static void run() {
		Check.start("TestMetadata");
		equality();
		tables();
		domains();
		columnList();
	}

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {
		run();
		summary();
		System.exit(0);
	}
}
