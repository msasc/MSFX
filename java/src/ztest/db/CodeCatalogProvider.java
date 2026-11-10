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

package ztest.db;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import msfx.lib.md.CatalogProvider;
import msfx.lib.md.Column;
import msfx.lib.md.Domain;
import msfx.lib.md.Index;
import msfx.lib.md.Table;
import msfx.lib.md.Types;

/**
 * Catalog provider of domains and tables defined in code.
 * <p>
 * All the definitions are made once, in the constructor, and only read
 * afterwards, so the provider can be called concurrently. Define the domains
 * first, so the tables can create their columns from them with
 * {@link #getDomain(String)}.
 *
 * @author Miquel Sas
 */
public class CodeCatalogProvider implements CatalogProvider {

	/**
	 * Domains, by name, in the order they were defined.
	 */
	private final Map<String, Domain> domains = new LinkedHashMap<>();
	/**
	 * Tables, by schema and name, in the order they were defined.
	 */
	private final Map<String, Map<String, Table>> tables = new LinkedHashMap<>();

	/**
	 * Constructor. Defines the domains and the tables.
	 */
	public CodeCatalogProvider() {
		defineDomains();
		defineTables();
	}

	/**
	 * Defines the domains, with {@link #addDomain(Domain)}.
	 */
	private void defineDomains() {
		addDomain(new Domain("CARTICLE", Types.STRING, 40, -1));
		addDomain(new Domain("CBUSINESS", Types.STRING, 12, -1));
		addDomain(new Domain("CBRAND", Types.STRING, 40, -1));
		addDomain(new Domain("CSET", Types.STRING, 40, -1));
		addDomain(new Domain("CPTYPE", Types.STRING, 40, -1));
		addDomain(new Domain("DARTICLE", Types.STRING, 200, -1));
	}

	/**
	 * Defines the tables of each schema, with {@link #addTable(String, Table)}.
	 */
	private void defineTables() {
		Table articles = new Table("ARTICLES");
		articles.columns().add(new Column(getDomain("CARTICLE")));
		articles.columns().add(new Column(getDomain("CBUSINESS")));
		articles.columns().add(new Column(getDomain("CBRAND")));
		articles.columns().add(new Column(getDomain("CSET")));
		articles.columns().add(new Column(getDomain("CPTYPE")));
		articles.columns().add(new Column(getDomain("DARTICLE")));

		Index articles_pk = new Index(articles);
		articles_pk.addColumns("CARTICLE");
		articles.setPrimaryKey(articles_pk);

		addTable("EXPLMARG_QLT", articles);
	}

	/**
	 * @param domain The domain to define.
	 * @throws IllegalArgumentException If a domain with the same name is already
	 *                                  defined.
	 */
	private void addDomain(Domain domain) {
		Objects.requireNonNull(domain, "Domain can not be null");
		if (domains.putIfAbsent(domain.getName(), domain) != null) {
			throw new IllegalArgumentException("Duplicate domain " + domain.getName());
		}
	}

	/**
	 * @param schema The schema of the table.
	 * @param table  The table design.
	 * @throws IllegalArgumentException If a table with the same name is already
	 *                                  defined in the schema.
	 */
	private void addTable(String schema, Table table) {
		Objects.requireNonNull(schema, "Schema can not be null");
		Objects.requireNonNull(table, "Table can not be null");
		Map<String, Table> schemaTables = tables.computeIfAbsent(schema, k -> new LinkedHashMap<>());
		if (schemaTables.putIfAbsent(table.getName(), table) != null) {
			throw new IllegalArgumentException("Duplicate table " + table.getName() + " in schema " + schema);
		}
	}

	@Override
	public Domain getDomain(String name) {
		return domains.get(name);
	}

	@Override
	public Table getTable(String schema, String name) {
		return tables.getOrDefault(schema, Map.of()).get(name);
	}

	@Override
	public List<String> getAllDomainNames() {
		return List.copyOf(domains.keySet());
	}

	@Override
	public List<String> getAllTableNames(String schema) {
		return List.copyOf(tables.getOrDefault(schema, Map.of()).keySet());
	}
}
