/*
 * Copyright (c) 2026 Miquel Sas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package msfx.lib.db.adapters;

import msfx.lib.db.DBEngine;
import msfx.lib.md.Column;
import msfx.lib.md.Types;

public class DBEngineOracle extends DBEngine {
	public DBEngineOracle() {
		/*
		 * Default driver properties to enhance performance.
		 */
		setDriverProperty("remarksReporting", "false");
		setDriverProperty("useBulkCopyForBatchInsert", "true");
	}

	@Override
	protected String getUrl(String cnString) {
		return "jdbc:oracle:thin:@" + cnString;
	}

	@Override
	public String getColumnDef(Column col) {
		Types type = col.getType();
		String def = "";
		switch (type) {
			case BOOLEAN:
				def += "VARCHAR(1)";
				break;
			case DECIMAL:
				def += "NUMBER(";
				def += col.getLength();
				def += ",";
				def += col.getDecimals();
				def += ")";
				break;
			case DOUBLE:
				def += "BINARY_DOUBLE";
				break;
			case LONG:
				def += "NUMBER(19,0)";
				break;
			case INTEGER:
				def += "NUMBER(10,0)";
				break;
			case DATE:
				def += "DATE";
				break;
			case TIME:
				// Oracle has no TIME type, the mapping is pending a decision.
				throw new UnsupportedOperationException("TIME columns not yet supported in Oracle");
			case DATETIME:
				def += "TIMESTAMP(7)";
				break;
			case STRING:
				if (col.getLength() > 0 && col.getLength() <= 4000) {
					def += "NVARCHAR2(";
					def += col.getLength();
					def += ")";
				} else {
					def += "CLOB";
				}
				break;
			case BINARY:
				if (col.getLength() > 0 && col.getLength() <= 2000) {
					def += "RAW(";
					def += col.getLength();
					def += ")";
				} else {
					def += "BLOB";
				}
				break;
			case OBJECT:
				def += "CLOB";
				break;
		}
		return def;
	}
}
