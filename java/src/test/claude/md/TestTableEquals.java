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

import java.util.HashSet;
import java.util.Set;

import msfx.lib.db.DBEngine;
import msfx.lib.db.adapters.DBEngineOracle;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.md.Column;
import msfx.lib.md.Table;
import test.db.CatalogCMA;

/**
 * Tests the database key of the engines, read from the connection string by
 * each adapter, and {@link Table#equals(Object)}: the database key, the schema
 * and the name. Nothing is executed: connectTo only keeps the settings.
 *
 * @author Miquel Sas
 */
public class TestTableEquals {

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
		line.append(": ");
		line.append(result);
		if (!ok) {
			line.append(", expected ");
			line.append(expected);
		}
		System.out.println(line);
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
			return db.getDatabaseKey();
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
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {

		System.out.println("== Database keys, SQL Server");
		check("Host, port and database", key(new DBEngineSQLServer(), "localhost:1433;databaseName=sql_cma_qlt"),
				"SQLSERVER:LOCALHOST:1433:SQL_CMA_QLT");
		check("Default port, property database, other options", key(new DBEngineSQLServer(),
				"dbserver;encrypt=true;database=Sales;trustServerCertificate=true"), "SQLSERVER:DBSERVER:1433:SALES");
		check("Named instance", key(new DBEngineSQLServer(), "host\\SQLEXPRESS:1435;databaseName=cma"),
				"SQLSERVER:HOST\\SQLEXPRESS:1435:CMA");
		String missing = key(new DBEngineSQLServer(), "localhost:1433;encrypt=false");
		check("No database", String.valueOf(missing.startsWith("IllegalArgumentException")), "true");

		System.out.println("== Database keys, Oracle");
		check("Service", key(new DBEngineOracle(), "orahost:1521/CMAPDB"), "ORACLE:ORAHOST:1521:CMAPDB");
		check("SID", key(new DBEngineOracle(), "orahost:1522:orcl"), "ORACLE:ORAHOST:1522:ORCL");
		check("// and default port", key(new DBEngineOracle(), "//orahost/cmapdb"), "ORACLE:ORAHOST:1521:CMAPDB");
		String descriptor = key(new DBEngineOracle(), "(DESCRIPTION=(ADDRESS=(HOST=orahost)))");
		check("TNS descriptor not read", String.valueOf(descriptor.startsWith("IllegalArgumentException")), "true");

		System.out.println("== connectTo once");
		DBEngine once = new DBEngineSQLServer();
		key(once, "localhost:1433;databaseName=a");
		String twice = key(once, "localhost:1433;databaseName=b");
		check("Second connectTo", String.valueOf(twice.startsWith("IllegalStateException")), "true");
		check("Key kept", once.getDatabaseKey(), "SQLSERVER:LOCALHOST:1433:A");
		check("Key given to the catalog", once.getCatalog().getDatabaseKey(), "SQLSERVER:LOCALHOST:1433:A");

		System.out.println("== Table equals");
		DBEngine front = engine("sql_cma_qlt");
		DBEngine back = engine("SQL_CMA_QLT");
		DBEngine other = engine("sql_cma_prd");
		Table frontPieces = front.getCatalog().getTable(CatalogCMA.SCHEMA, "TPIECES");
		Table backPieces = back.getCatalog().getTable(CatalogCMA.SCHEMA, "TPIECES");
		Table otherPieces = other.getCatalog().getTable(CatalogCMA.SCHEMA, "TPIECES");
		Table frontUnits = front.getCatalog().getTable(CatalogCMA.SCHEMA, "UNITS");
		check("Key set by the catalog", frontPieces.getDatabaseKey(), "SQLSERVER:LOCALHOST:1433:SQL_CMA_QLT");
		check("Same table from two engines of the same database", String.valueOf(
				frontPieces != backPieces && frontPieces.equals(backPieces) && frontPieces.hashCode() == backPieces.hashCode()),
				"true");
		check("Same table of another database", String.valueOf(frontPieces.equals(otherPieces)), "false");
		check("Another table", String.valueOf(frontPieces.equals(frontUnits)), "false");

		Column frontColumn = frontPieces.columns().get("CPTYPE");
		Column backColumn = backPieces.columns().get("CPTYPE");
		Column otherColumn = otherPieces.columns().get("CPTYPE");
		check("Same column through the two engines", String.valueOf(frontColumn.equals(backColumn)), "true");
		check("Same column of another database", String.valueOf(frontColumn.equals(otherColumn)), "false");

		Table lower = new Table("sales");
		Table upper = new Table("SALES");
		check("Tables on the fly, by name ignoring case", String.valueOf(lower.equals(upper)), "true");
		Set<Table> set = new HashSet<>();
		set.add(lower);
		int hash = lower.hashCode();
		lower.setSchema("dbo");
		lower.setDatabaseKey("SQLSERVER:LOCALHOST:1433:X");
		check("Hash code unchanged after schema and key", String.valueOf(lower.hashCode() == hash && set.contains(lower)),
				"true");
		check("With schema and key against one without", String.valueOf(lower.equals(upper)), "false");
		upper.setSchema("DBO");
		upper.setDatabaseKey("SQLSERVER:LOCALHOST:1433:X");
		check("Same key, schema ignoring case", String.valueOf(lower.equals(upper)), "true");

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
	}
}
