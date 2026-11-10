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
package msfx.lib.md;

import java.util.List;

/**
 * Provider of metadata to a {@link Catalog}, asked when the catalog does not
 * find a domain or a table in its internal maps.
 * <p>
 * Implementations may define the metadata in code, in database tables or in
 * text files. They may be called concurrently.
 *
 * @author Miquel Sas
 */
public interface CatalogProvider {

    /**
     * @param name The domain name.
     * @return The domain, or null if this provider does not define it.
     */
    Domain getDomain(String name);

    /**
     * Returns the design of a table of a schema, since different schemas may
     * have different tables with the same name. The provider may return the same
     * design for several schemas, and does not need to set the schema: the
     * catalog sets it on its own copy.
     * <p>
     * A table of a schema must be defined by only one of the providers of a
     * catalog.
     *
     * @param schema The schema.
     * @param name   The table name.
     * @return The table design, or null if this provider does not define it for
     *         the schema.
     */
    Table getTable(String schema, String name);

    /**
     * @return The names of all the domains this provider defines, or an empty
     *         list, never null.
     */
    List<String> getAllDomainNames();

    /**
     * @param schema The schema.
     * @return The names of all the tables this provider defines for the schema,
     *         none of them defined by another provider of the catalog,
     *         or an empty list, never null.
     */
    List<String> getAllTableNames(String schema);
}