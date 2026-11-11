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

package test.claude;

import java.util.ArrayList;
import java.util.List;

import msfx.lib.db.DBEngine;
import msfx.lib.db.adapters.DBEngineOracle;
import msfx.lib.db.adapters.DBEngineSQLServer;
import msfx.lib.db.query.sa.Function;

/**
 * Prints the translation of every function, with every accepted number of
 * arguments, in every adapter, to review them side by side. Needs no database.
 *
 * @author Miquel Sas
 */
public class TestFunctionSQL {

	/**
	 * Entry point.
	 *
	 * @param args Not used.
	 */
	public static void main(String[] args) {
		DBEngine[] dbs = new DBEngine[] { new DBEngineSQLServer(), new DBEngineOracle() };
		String[] samples = new String[] { "a", "b", "c" };
		for (Function function : Function.values()) {
			int max = Math.min(function.getMaxArguments(), samples.length);
			for (int count = function.getMinArguments(); count <= max; count++) {
				List<String> arguments = new ArrayList<>();
				for (int i = 0; i < count; i++) {
					arguments.add(samples[i]);
				}
				StringBuilder title = new StringBuilder();
				title.append(function);
				title.append(" ");
				title.append(arguments);
				System.out.println(title);
				for (DBEngine db : dbs) {
					StringBuilder line = new StringBuilder();
					line.append("   ");
					line.append(db.getAdapter());
					line.append(": ");
					try {
						line.append(db.getFunctionSQL(function, arguments));
					} catch (UnsupportedOperationException exc) {
						line.append("<");
						line.append(exc.getMessage());
						line.append(">");
					}
					System.out.println(line);
				}
			}
		}
	}
}
