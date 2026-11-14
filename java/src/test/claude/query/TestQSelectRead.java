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

import java.io.IOException;
import java.sql.SQLException;

import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.db.query.QSelect;
import msfx.lib.md.Column;
import msfx.lib.md.Table;
import msfx.lib.md.Types;
import msfx.lib.md.Value;

/**
 * Tests {@link QSelect#readCallback(QSelect.Callback)}: the checks before
 * executing, and, with the "db" argument, reading the system view sys.types of
 * the local SQL Server, stopping early, and the exceptions thrown by the
 * callback. Only SELECT statements are executed.
 *
 * @author Miquel Sas
 */
public class TestQSelectRead {

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
	 * The table SALES, never read.
	 */
	private static Table sales;
	/**
	 * The system view sys.types.
	 */
	private static Table types;

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
	 * A read that must fail with an exception.
	 */
	@FunctionalInterface
	private interface Read {
		/**
		 * Runs the read.
		 *
		 * @throws Exception If the read fails.
		 */
		void run() throws Exception;
	}

	/**
	 * Runs a read that must fail with an exception of the expected class, and
	 * optionally a cause of the expected class, and prints its message.
	 *
	 * @param title         What is checked.
	 * @param expected      The expected class of exception.
	 * @param expectedCause The expected class of the cause, or null.
	 * @param read          The read.
	 */
	private static void error(String title, Class<? extends Exception> expected, Class<? extends Exception> expectedCause, Read read) {
		String result;
		boolean ok;
		try {
			read.run();
			result = "no error";
			ok = false;
		} catch (Exception exc) {
			StringBuilder b = new StringBuilder();
			b.append(exc.getClass().getSimpleName());
			b.append(": ");
			b.append(exc.getMessage());
			ok = expected.isInstance(exc);
			if (exc.getCause() != null) {
				b.append("\ncause ");
				b.append(exc.getCause().getClass().getSimpleName());
				b.append(": ");
				b.append(exc.getCause().getMessage());
			}
			if (expectedCause != null) {
				ok &= expectedCause.isInstance(exc.getCause());
			}
			result = b.toString();
		}
		StringBuilder exp = new StringBuilder();
		exp.append(expected.getSimpleName());
		if (expectedCause != null) {
			exp.append(" caused by ");
			exp.append(expectedCause.getSimpleName());
		}
		report(ok, title, result, exp.toString());
	}

	/**
	 * Returns a select of some types of sys.types, by name.
	 *
	 * @return The select.
	 */
	private static QSelect someTypes() {
		QSelect select = new QSelect(SQLSERVER);
		select.from(types, "T");
		select.addColumn("T.name", "NAME");
		select.addColumn("T.max_length", "NLENGTH");
		select.whereIn("T.name", new Value("int"), new Value("bigint"), new Value("date"));
		select.orderBy("NAME");
		return select;
	}

	/**
	 * Entry point.
	 *
	 * @param args Pass "db" to also read from the local SQL Server.
	 */
	public static void main(String[] args) {

		sales = new Table("SALES");
		sales.setSchema("dbo");
		sales.columns().add(new Column("CCOMPANY", Types.STRING, 10, -1));
		sales.columns().add(new Column("QSALES", Types.DECIMAL, 16, 2));

		types = new Table("types");
		types.setSchema("sys");
		types.columns().add(new Column("name", Types.STRING, 128, -1));
		types.columns().add(new Column("max_length", Types.INTEGER, -1, -1));

		System.out.println("== Checks before executing, no database needed");
		error("No FROM clause", IllegalStateException.class, null, () -> {
			new QSelect(SQLSERVER).readCallback(rs -> true);
		});
		error("No columns", IllegalStateException.class, null, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(sales, "S");
			s.readCallback(rs -> true);
		});
		error("EXISTS select executed alone", IllegalStateException.class, null, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(sales, "S");
			QSelect e = new QSelect(s);
			e.from(types, "T");
			e.addColumn("1", "NREF");
			e.readCallback(rs -> true);
		});
		error("Aggregates and other expressions without groupBy()", IllegalStateException.class, null, () -> {
			QSelect s = new QSelect(SQLSERVER);
			s.from(sales, "S");
			s.addColumn("S.CCOMPANY", "CCOMPANY");
			s.addColumn("SUM(S.QSALES)", "QSALES");
			s.readCallback(rs -> true);
		});
		error("Null callback", NullPointerException.class, null, () -> {
			someTypes().readCallback(null);
		});

		if (args.length > 0 && args[0].equals("db")) {
			System.out.println("== Reads on SQL Server, from the system view sys.types");
			SQLSERVER.connectTo("localhost:1433;databaseName=sql_cma_qlt", "sa", "SqlPassword2026!");

			StringBuilder rows = new StringBuilder();
			String result;
			try {
				someTypes().readCallback(rs -> {
					if (!rows.isEmpty()) {
						rows.append(", ");
					}
					rows.append(rs.getString("NAME"));
					rows.append(" ");
					rows.append(rs.getString("NLENGTH"));
					return true;
				});
				result = rows.toString();
			} catch (SQLException exc) {
				result = exc.getMessage();
			}
			report(result.equals("bigint 8, date 3, int 4"), "All rows read, by alias", result, "bigint 8, date 3, int 4");

			StringBuilder first = new StringBuilder();
			try {
				someTypes().readCallback(rs -> {
					first.append(rs.getString("NAME"));
					return false;
				});
				result = first.toString();
			} catch (SQLException exc) {
				result = exc.getMessage();
			}
			report(result.equals("bigint"), "Stopped after the first row", result, "bigint");

			error("Checked exception of the callback, as the cause", SQLException.class, IOException.class, () -> {
				someTypes().readCallback(rs -> {
					throw new IOException("Disk full");
				});
			});
			error("SQLException of the callback, not wrapped", SQLException.class, null, () -> {
				someTypes().readCallback(rs -> {
					rs.getString("UNKNOWN_ALIAS");
					return true;
				});
			});
			error("Runtime exception of the callback, not wrapped", IllegalStateException.class, null, () -> {
				someTypes().readCallback(rs -> {
					throw new IllegalStateException("A bug");
				});
			});

			boolean interrupted;
			try {
				someTypes().readCallback(rs -> {
					throw new InterruptedException("Stop");
				});
				interrupted = false;
			} catch (SQLException exc) {
				interrupted = Thread.interrupted() && exc.getCause() instanceof InterruptedException;
			}
			report(interrupted, "InterruptedException: wrapped and the interrupted flag set again",
					String.valueOf(interrupted), "true");

			try {
				someTypes().readCallback(rs -> {
					throw new IOException("Disk full");
				});
			} catch (SQLException exc) {
				// expected
			}
			StringBuilder again = new StringBuilder();
			try {
				for (int i = 0; i < 50; i++) {
					again.setLength(0);
					someTypes().readCallback(rs -> {
						again.append(rs.getString("NAME"));
						return false;
					});
				}
				result = again.toString();
			} catch (SQLException exc) {
				result = exc.getMessage();
			}
			report(result.equals("bigint"), "50 more reads after failed ones: connections returned to the pool", result, "bigint");
		}

		StringBuilder summary = new StringBuilder();
		summary.append("Passed: ");
		summary.append(passed);
		summary.append(", failed: ");
		summary.append(failed);
		System.out.println(summary);
		System.exit(0);
	}
}
