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

import java.sql.SQLException;
import java.util.List;

import msfx.lib.db.DBConnection;
import msfx.lib.db.DBResultSet;
import msfx.lib.db.DBStatement;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.db.query.QSelect;
import msfx.lib.md.Catalog;
import msfx.lib.md.Column;
import msfx.lib.md.Table;
import test.db.CatalogCMA;

/**
 * Tests {@link CatalogCMA} against the local SQL Server copy: every table is
 * loaded through a {@link Catalog}, and a select of all its columns is built
 * with {@link QSelect} and executed with WHERE 1 = 0, so that the database
 * checks the table and every column without reading rows. Only SELECT
 * statements are executed.
 *
 * @author Miquel Sas
 */
public class TestCatalogCMA {

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {
		DBEngineSQLServer db = new DBEngineSQLServer();
		db.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");
		Catalog catalog = new Catalog();
		long start = System.currentTimeMillis();
		catalog.addProvider(new CatalogCMA());
		List<String> names = catalog.getAllTableNames(CatalogCMA.SCHEMA);
		long loaded = System.currentTimeMillis() - start;

		int passed = 0;
		int failed = 0;
		int columns = 0;
		for (String name : names) {
			String result;
			boolean ok;
			try {
				Table table = catalog.getTable(CatalogCMA.SCHEMA, name);
				QSelect select = new QSelect(db);
				select.from(table, "T");
				for (Column column : table.columns()) {
					StringBuilder expr = new StringBuilder();
					expr.append("T.");
					expr.append(column.getName());
					select.addColumn(expr.toString(), column.getName());
				}
				select.whereCond("1 = 0");
				try (DBConnection cn = db.getConnection();
						DBStatement st = cn.createStatement();
						DBResultSet rs = st.executeQuery(select.toString())) {
					int count = rs.getMetaData().getColumnCount();
					ok = count == table.columns().size();
					StringBuilder b = new StringBuilder();
					b.append(count);
					b.append(" columns");
					result = b.toString();
					columns += count;
				}
			} catch (SQLException | RuntimeException exc) {
				ok = false;
				StringBuilder b = new StringBuilder();
				b.append(exc.getClass().getSimpleName());
				b.append(": ");
				b.append(exc.getMessage());
				result = b.toString();
			}
			if (ok) {
				passed++;
			} else {
				failed++;
				StringBuilder line = new StringBuilder();
				line.append("   FAIL  ");
				line.append(name);
				line.append(": ");
				line.append(result);
				System.out.println(line);
			}
		}

		StringBuilder summary = new StringBuilder();
		summary.append("Catalog created in ");
		summary.append(loaded);
		summary.append(" ms, tables: ");
		summary.append(names.size());
		summary.append(", columns read: ");
		summary.append(columns);
		summary.append("\nPassed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
		System.exit(0);
	}
}
