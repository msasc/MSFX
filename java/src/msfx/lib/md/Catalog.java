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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Catalog of the domains and tables available to an application, organized by
 * schema.
 * <p>
 * The catalog searches first in its internal maps and, if not found, in a list
 * of metadata providers, which may define the metadata in code, in database
 * tables or in text files. The definition found is kept, and the providers
 * are not asked again for it.
 * <p>
 * For a domain, the first definition returned by a provider is kept. A table
 * of a schema must be defined by only one provider: a table defined by more
 * than one is a configuration error.
 * <p>
 * Domains are global, they do not belong to a schema.
 * <p>
 * The catalog may be used concurrently. Each internal map has its own lock, and
 * the providers are asked outside the locks, so a slow provider does not block
 * the access to the definitions already kept.
 *
 * @author Miquel Sas
 */
public class Catalog {
    /**
     * Key of a table kept in the catalog.
     *
     * @param schema The schema.
     * @param name   The table name.
     */
    private record TableKey(String schema, String name) {
    }

    /**
     * Domains already returned by a provider, by name.
     */
    private final Map<String, Domain> domains = new HashMap<>();
    /**
     * Lock that guards the domains.
     */
    private final ReentrantLock lockDomains = new ReentrantLock();
    /**
     * Tables already returned by a provider, by schema and name.
     */
    private final Map<TableKey, Table> tables = new HashMap<>();
    /**
     * Lock that guards the tables.
     */
    private final ReentrantLock lockTables = new ReentrantLock();
    /**
     * Providers, in the order they are asked.
     */
    private final List<CatalogProvider> providers = new ArrayList<>();
    /**
     * Lock that guards the providers.
     */
    private final ReentrantLock lockProviders = new ReentrantLock();

    /**
     * Constructor.
     */
    public Catalog() {
    }

    /**
     * Adds a provider, asked after the ones already added.
     *
     * @param provider The provider.
     * @throws NullPointerException If the provider is null.
     */
    public void addProvider(CatalogProvider provider) {
        Objects.requireNonNull(provider, "Provider can not be null");
        lockProviders.lock();
        try {
            providers.add(provider);
        } finally {
            lockProviders.unlock();
        }
    }

    /**
     * Returns a domain. The first definition returned by a provider is kept,
     * and the providers are not asked again for it.
     * <p>
     * If two threads ask at the same time for a domain not yet kept, both may
     * ask the providers, but only the first definition is kept and both get it.
     *
     * @param name The domain name.
     * @return The domain, or null if no provider defines it.
     * @throws NullPointerException If the name is null.
     */
    public Domain getDomain(String name) {
        Objects.requireNonNull(name, "Domain name can not be null");
        lockDomains.lock();
        try {
            Domain domain = domains.get(name);
            if (domain != null) {
                return domain;
            }
        } finally {
            lockDomains.unlock();
        }
        /* Ask outside the lock, a provider may take a round trip to the database. */
        for (CatalogProvider provider : getProviders()) {
            Domain domain = provider.getDomain(name);
            if (domain != null) {
                lockDomains.lock();
                try {
                    /* Another thread may have kept it meanwhile: the first one kept wins. */
                    return domains.computeIfAbsent(name, k -> domain);
                } finally {
                    lockDomains.unlock();
                }
            }
        }
        return null;
    }

    /**
     * Returns a table of a schema. The definition returned by a provider is
     * copied, the copy gets the schema and is kept, and the providers are not
     * asked again for it.
     * <p>
     * The first time a table is requested, all the providers are asked, to check
     * that only one of them defines it.
     * <p>
     * If two threads ask at the same time for a table not yet kept, both may ask
     * the providers, but only the first copy is kept and both get it.
     * <p>
     * The foreign tables of the foreign keys are not resolved in the catalog:
     * they remain those of the provider's design, except a foreign key to the
     * table itself, which refers to the copy.
     *
     * @param schema The schema.
     * @param name   The table name.
     * @return The table, or null if no provider defines it.
     * @throws NullPointerException  If the schema or the name is null.
     * @throws IllegalStateException If more than one provider defines the table.
     */
    public Table getTable(String schema, String name) {
        Objects.requireNonNull(schema, "Schema can not be null");
        Objects.requireNonNull(name, "Table name can not be null");
        TableKey key = new TableKey(schema, name);
        lockTables.lock();
        try {
            Table table = tables.get(key);
            if (table != null) {
                return table;
            }
        } finally {
            lockTables.unlock();
        }
        /* Ask and copy outside the lock, a provider may take a round trip to the database. */
        Table design = null;
        CatalogProvider owner = null;
        for (CatalogProvider provider : getProviders()) {
            Table found = provider.getTable(schema, name);
            if (found != null) {
                if (design != null) {
                    StringBuilder msg = new StringBuilder();
                    msg.append("Table ");
                    msg.append(name);
                    msg.append(" of schema ");
                    msg.append(schema);
                    msg.append(" is defined by more than one provider: ");
                    msg.append(owner.getClass().getName());
                    msg.append(", ");
                    msg.append(provider.getClass().getName());
                    throw new IllegalStateException(msg.toString());
                }
                design = found;
                owner = provider;
            }
        }
        if (design == null) {
            return null;
        }
        /* Copy, so the provider's design is not changed and each schema has its own table. */
        Table table = design.copy();
        table.setSchema(schema);
        lockTables.lock();
        try {
            /* Another thread may have kept it meanwhile: the first one kept wins. */
            return tables.computeIfAbsent(key, k -> table);
        } finally {
            lockTables.unlock();
        }
    }

    /**
     * Returns the names of all the domains defined by the providers, in the order
     * of the providers and without duplicates. The providers are asked on every
     * call, since the names are not kept.
     *
     * @return The domain names, an unmodifiable list.
     */
    public List<String> getAllDomainNames() {
        Set<String> names = new LinkedHashSet<>();
        for (CatalogProvider provider : getProviders()) {
            names.addAll(provider.getAllDomainNames());
        }
        return List.copyOf(names);
    }

    /**
     * Returns the names of all the tables of a schema defined by the providers,
     * in the order of the providers. The providers are asked on every call,
     * since the names are not kept.
     *
     * @param schema The schema.
     * @return The table names, an unmodifiable list.
     * @throws NullPointerException  If the schema is null.
     * @throws IllegalStateException If more than one provider defines a table.
     */
    public List<String> getAllTableNames(String schema) {
        Objects.requireNonNull(schema, "Schema can not be null");
        Set<String> names = new LinkedHashSet<>();
        for (CatalogProvider provider : getProviders()) {
            /* A name repeated by the same provider is not a conflict. */
            for (String name : new LinkedHashSet<>(provider.getAllTableNames(schema))) {
                if (!names.add(name)) {
                    StringBuilder msg = new StringBuilder();
                    msg.append("Table ");
                    msg.append(name);
                    msg.append(" of schema ");
                    msg.append(schema);
                    msg.append(" is defined by more than one provider");
                    throw new IllegalStateException(msg.toString());
                }
            }
        }
        return List.copyOf(names);
    }

    /**
     * @return A copy of the providers, to ask them outside the lock.
     */
    private List<CatalogProvider> getProviders() {
        lockProviders.lock();
        try {
            return new ArrayList<>(providers);
        } finally {
            lockProviders.unlock();
        }
    }
}
