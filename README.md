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
and both are handled the same way.

### Database access (`msfx.lib.db`)

- A database engine per database, with its own connection pool.
- Adapters for **SQL Server** and **Oracle**. Every difference between
  databases lives in the adapter, and adding a database makes the compiler
  point at every place it must handle.

### Query generator (`msfx.lib.db.query`)

Generates SQL from the metadata, one class per statement, so that the same code
works on every supported database:

- **DDL**, complete: create and drop tables, columns, primary keys, indexes
  and schemas.
- **SELECT**, in progress: the FROM clause is done; joins, conditions and the
  select list will rely on a small SQL analyzer (`db.query.sa`) that validates
  hand-written expressions against the tables involved and translates them to
  each database. Done so far: the vocabulary of keywords, operators and
  functions, the translation of every function by each adapter, and the lexer.
  The parser, the validator and the writer come next.
- **INSERT, UPDATE and DELETE**: to come, with transactions that group them.

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
java/src/msfx/lib/db/query   query generator
java/src/test                manual tests against a local SQL Server
java/lib                     JDBC drivers (the JDK and JavaFX are downloaded separately)
java/doc                     generated Javadoc
```

## License and credits

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

Developed by Miquel Sas with the assistance of Claude (Anthropic). See
[NOTICE](NOTICE).
