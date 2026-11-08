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

package msfx.lib.db.pool;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Opens the physical connections of a {@link ConnectionPool}, so that the pool
 * does not need to know the URL, the credentials or the driver properties.
 *
 * @author Miquel Sas
 */
@FunctionalInterface
public interface ConnectionFactory {
	/**
	 * @return A new physical JDBC connection.
	 * @throws SQLException If an error occurs.
	 */
	Connection open() throws SQLException;
}
