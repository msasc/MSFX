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

public class DBEngineSQLServer extends DBEngine {

	public DBEngineSQLServer() {
		/*
		 * Default properties to enhance performance.
		 */
		setDriverProperty("encrypt", "false");
		setDriverProperty("trustServerCertificate", "false");
		setDriverProperty("sendStringParametersAsUnicode", "false");
		setDriverProperty("useBulkCopyForBatchInsert", "true");
		setDriverProperty("tcpKeepAlive", "true");
		setDriverProperty("KeepAliveTime", "1000");
		setDriverProperty("KeepAliveInterval", "1000");
		setDriverProperty("responseBuffering", "adaptive");
	}

	@Override
	protected String getUrl(String cnString) {
		return "jdbc:sqlserver://" + cnString;
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
				def += "NUMERIC(";
				def += col.getLength();
				def += ",";
				def += col.getDecimals();
				def += ")";
				break;
			case DOUBLE:
				def += "FLOAT(53)";
				break;
			case LONG:
				def += "BIGINT";
				break;
			case INTEGER:
				def += "INT";
				break;
			case DATE:
				def += "DATE";
				break;
			case TIME:
				def += "TIME(7)";
				break;
			case DATETIME:
				def += "DATETIME2(7)";
				break;
			case STRING:
				def += "NVARCHAR(";
				if (col.getLength() > 0 && col.getLength() <= 4000) {
					def += col.getLength();
				} else {
					def += "MAX";
				}
				def += ")";
				break;
			case BINARY:
				def += "VARBINARY(";
				if (col.getLength() > 0 && col.getLength() <= 8000) {
					def += col.getLength();
				} else {
					def += "MAX";
				}
				def += ")";
				break;
			case OBJECT:
				def += "NVARCHAR(MAX)";
				break;
		}
		return def;
	}
}
