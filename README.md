# MSFX

MSFX is the base library of a large business application, written in Java 21.
It is built step by step, with care for its root classes, so that the
application can be developed quickly and safely on top of them.

The library is under development. This README will grow with it.

## What it does so far

### Metadata (`msfx.lib.md`)

Describes the data of the application independently of any database:

- **Types and values**: a small closed set of types (strings, numbers,
  booleans, dates, times, binaries and JSON objects) and a `Value` that holds
  any of them.
- **Columns, tables and indexes**: columns with type, length, nullability,
  default and possible values; tables with a primary key, secondary indexes and
  foreign keys.
- **Records**: rows of values that share the description of their columns.
- **Catalog**: the tables of the application by schema, provided by code or by
  other sources.

A table can be defined by the application or read from an existing database,
and both are handled the same way. Metadata is compared by value, by its
database structure only, so that the same table read by two parts of the
application, or sent between them, is recognized as the same.

### Database access (`msfx.lib.db`)

- A database engine per database, with its own connection pool.
- Adapters for **SQL Server** and **Oracle**. Every difference between
  databases lives in the adapter, and adding a database makes the compiler
  point at every place it must handle.
- Values written to and read from the database by their type, also for SQL
  written by hand: booleans as Y/N, JSON objects as text, decimals with the
  decimals of their column.

### Query generator (`msfx.lib.db.query`)

Generates SQL from the metadata, one class per statement, so that the same code
works on every supported database. Hand-written expressions are validated by a
small SQL analyzer (`db.query.sa`) against the tables involved, and translated
to each database: names, functions, concatenations and literals.

- **DDL**: create and drop tables, columns, primary keys, indexes and schemas.
- **SELECT**: joins, the select list, WHERE conditions in groups with values,
  IN and EXISTS, GROUP BY generated from the select list, HAVING, ORDER BY,
  and the UNION of selects wrapped as a table. Rows are read as records, all
  at once or one by one.
- **INSERT, UPDATE and DELETE**: built by hand, with expressions, values or a
  select, on blocks of rows; or with records, one row by its primary key.
  Transactions that group them come next.

## Why portability matters

A previous application of the author, built on Oracle, had to be ported to
SQL Server: more than 1800 hand-written queries were rewritten. This library
keeps every database difference in one place, checked by the compiler, so that
moving to another database means writing an adapter, not rewriting the
application.

SQL Server is the development and test database. PostgreSQL is expected to be
added.

## Project layout

```
java/src/msfx/lib/util       utilities: properties, JSON, copying
java/src/msfx/lib/md         metadata
java/src/msfx/lib/db         engines, connections, pool, adapters
java/src/msfx/lib/db/query   query generator, and the SQL analyzer in db/query/sa
java/src/test                tests against a local SQL Server; test.claude.RunAll runs them all
java/lib                     JDBC drivers (the JDK and JavaFX are downloaded separately)
java/doc                     generated Javadoc
docs                         notes on the development
```

## License and credits

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

Developed by Miquel Sas with the assistance of Claude (Anthropic). See
[NOTICE](NOTICE).

## Development time

About **40 hours** of coding effort up to 2026-11-17, for all of the above:
about 20,700 lines of library and 5,100 lines of tests, Javadoc included.
Time is tracked from then on, by the active time of the sessions with Claude
Code. See [docs/development-time.md](docs/development-time.md).
