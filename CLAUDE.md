# MSFX

`msfx.lib` is a Java 21 library, the base of a large internal application. It
is not a public library: closed, compile-checked designs are preferred over
extension points for third parties. Root classes (db, db.query, and later the
desktop and task concurrency packages) must be as well defined as possible.

## Layout

- `java/src/msfx/lib/util`: Properties, JSON, Copier and other utilities.
- `java/src/msfx/lib/md`: metadata. Types, Value, Column, ColumnList, Domain,
  Table, Index, ForeignKey, Record, Catalog, CatalogProvider.
- `java/src/msfx/lib/db`: DBEngine, connections, statements, result sets, the
  connection pool (`db.pool`) and the adapters (`db.adapters`).
- `java/src/msfx/lib/db/query`: the query generator, one class per statement.
  `db.query.sa`: the syntax analyzer of hand-written expressions. Lexer
  (Token), Parser (Node), Validator (Clause, Analysis) and Writer, with the
  vocabulary in Keyword, Operator and Function.
- `java/src/test`: manual tests, run with `main`. Claude writes its tests in
  `test.claude`, in sub-packages as needed, never as scratch files.
- `java/lib`: JDK and JavaFX (not in Git, downloaded separately) and the JDBC
  drivers. `java/doc` is generated Javadoc.

## Databases

- SQL Server (Developer Edition, local) is the development and test target.
- Oracle is supported to keep the library portable: a past port from Oracle to
  SQL Server forced rewriting more than 1800 hand-written queries. Oracle is
  not expected to be used. Keep it correct, but don't invest in Oracle-only
  features. Oracle schemas are created by the administrator, never by the
  library.
- A free database (PostgreSQL, MariaDB) may come only if the application needs
  a much bigger machine and lower licensing costs. A new adapter must then be
  a new `DBEngine.Adapter` value plus its abstract methods, with the compiler
  pointing at every place to handle.

## Design rules

- **Engine differences.** SQL that depends on the database is an abstract
  method in `DBEngine`, implemented in every adapter, with no default. Small
  local differences use a `switch` expression on `db.getAdapter()`: always an
  exhaustive expression, never a statement and never `instanceof`.
- **No design ahead.** Build only the operations the stated usage needs. When a
  gap seems to need extra machinery, first ask whether the operation causing
  it is needed at all.
- **md state lives in Properties.** Metadata classes keep all their state in
  their `Properties`, non-standard values with `putObject`, never in fields.
  Metadata is complete at definition time; later misuse is not guarded.
- **Tables are md objects.** A `Table` is either defined by the application or
  read from the database through `DatabaseMetaData`. Query classes take md
  objects (`Table`, `Column`, `Index`), never names, and validate that they
  belong to the table.
- **Query classes.** Members are private and final, validated in the
  constructor, with no getters, except the parts a caller needs to build the
  query (like `QSelect.getWhere()`). `toSQL()` is private. Parts (`QFrom`,
  `QWhere`, `QJoin`, `QGroupBy`, `QOrderBy`) are created only by their
  statement and share its `QFrom`. Every query class and part has a public
  `toString()` (abstract in `QBase`) with the SQL built so far, for debugging
  and to paste into an SQL editor; it is the last method of the class.
- **DDL** writes only the logical definition: no tablespaces, `ONLINE`,
  `FILLFACTOR` or other physical options, and no ASC/DESC in indexes.
  SQL Server defaults are named constraints `DF_table_column`.
- **Transactions** group only INSERT, UPDATE and DELETE. DDL and SELECT always
  run on their own pooled connection. A future `QTransaction` will chain DML
  statements through a package-private `execute(DBConnection)`.
- **Nulls.** Numbers and booleans are never null. Oracle stores `''` as NULL,
  so Oracle STRING columns get no NOT NULL and no `DEFAULT ''`. Null checks in
  SQL go through `DBEngine.isNull` and `isNotNull`.
- **Exceptions.** Each layer throws the checked exception of its context
  (`SQLException` in db) and translates other checked exceptions into it,
  keeping them as the cause. Never catch `Throwable` or `Error`. Don't wrap a
  `RuntimeException` (it is a bug, let it pass) nor an exception already of
  the context type. When wrapping an `InterruptedException`, set the
  interrupted flag again with `Thread.currentThread().interrupt()`. Callbacks
  written by the caller may throw `Exception`; the method that calls them
  translates around the call only.

## Code style

- Apache license header, `@author Miquel Sas`, Javadoc on every member.
- Indentation: tabs in db, db.query, util, test, `ColumnList` and `Types`;
  4 spaces in the rest of md and in `QBase`.
- Text is always composed with a `StringBuilder`, one append per piece, even for
  short compositions and exception messages. Never `+` or `+=`.
- Initials keep their case in identifiers: `toSQL`, `DBEngine`, `getDBEngine`,
  `getURL`, `JSONObject`. Naming conventions give way to readability.
- Switches use the old colon labels (`case X:`), never arrows (`->`), for
  readability and step-by-step debugging. Over an enum, write a switch
  expression with `yield` and no `default`, so the compiler checks that every
  constant is handled; assign it to a local before returning it.
- Methods not yet implemented throw
  `new UnsupportedOperationException("Not implemented yet")`.

## Workflow

- The user asks for analysis first on design questions, then says to implement.
- Don't commit unless asked. The test password is for a local development
  database and may be committed; production credentials (client databases,
  through a VPN) must never be in the code or the repository.
- Don't run DDL against a database without permission.
