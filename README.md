# Concurrent Data Seeding Pipeline

A multithreaded producer/consumer application that generates a large retail
catalogue and bulk-loads it into PostgreSQL. Built to explore how throughput,
batching and indexing behave when the row count grows into the millions.

Default workload: **3,000,000 `ShopEntry` rows**.

## What it does

1. Reads reference data (shops, item types) from CSV.
2. Generates item and shop-entry records with [Datafaker](https://www.datafaker.net/),
   optionally injecting a configurable share of invalid rows.
3. Producers push generated DTOs onto a bounded `BlockingQueue`;
   consumers drain it and write to PostgreSQL in JDBC batches.
4. Reconciles the number of produced rows against the number actually stored
   and fails loudly on a mismatch.

## Design notes

**Concurrency.** Separate `ExecutorService` pools for producers and consumers,
sized independently in configuration. The queue is bounded, so a slow consumer
applies back-pressure to producers instead of letting memory grow without limit.

**Validation.** Generated DTOs pass through Hibernate Validator before they reach
the database, so malformed rows are rejected at the boundary rather than surfacing
as constraint violations mid-batch.

**Indexing.** Indexes are created *after* the bulk load rather than before, so
inserts are not slowed by index maintenance — see
[`post_load_indexes.sql`](src/main/resources/post_load_indexes.sql).
The set of indexes was chosen by running `EXPLAIN ANALYZE` against the loaded
3M-row dataset: candidates that did not change the query plan were dropped and
are left commented out in the script as a record of what was tried.

**Failure modes.** Dedicated exception types (`PipelineTaskException`,
`RowCountMismatchException`, `ResourceLoadException`) distinguish a task that
died inside the pipeline from a load that completed but produced the wrong
number of rows.

## Schema

Four tables — `ItemType`, `Shop`, `Item`, `ShopEntry` — normalised to 3NF, with
foreign keys cascading on delete and a uniqueness constraint on the
(item, shop) pair. Full DDL in [`schema.sql`](src/main/resources/schema.sql).

## Tests

12 test classes covering the pipeline, repository, generators, CSV reading,
configuration and validation. JUnit 5 with Mockito for the database boundary.

```bash
mvn test
```

## Running it

```bash
cp src/main/resources/config.properties.example src/main/resources/config.properties
# edit db.url / db.user / db.password
mvn clean package
java -jar target/pract4-0.0.1.jar
```

Requires PostgreSQL 12+ and JDK 17+.

## Stack

Java, PostgreSQL, JDBC, Hibernate Validator, Datafaker, Apache Commons CSV,
Logback, JUnit 5, Mockito, Maven
