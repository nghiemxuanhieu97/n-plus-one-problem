# Module 01: JOIN FETCH Benchmark Playground

This module is a Spring Boot + Spring Data JPA demo for observing Hibernate fetch behavior. It focuses on `LAZY` associations, N+1 queries, `JOIN FETCH`, pagination pitfalls, row explosion, and JVM/backend metrics collected during each benchmark request.

The module intentionally keeps JPA entities out of API responses. Service methods map entities to scenario-specific DTOs inside the transaction, then return `Response<T>` with metrics and a small preview of the result data.

## What This Module Demonstrates

- Baseline author loading without touching associations.
- N+1 query behavior with lazy `books` and lazy `awards`.
- Safe `JOIN FETCH` for a to-one association: `Author.country`.
- Size-sensitive `JOIN FETCH` for one collection: `Author.books`.
- Dangerous multiple-collection `JOIN FETCH`: `Author.books` + `Author.awards`.
- Pagination with lazy loading, to-one fetch join, collection fetch join, and two-step pagination.
- Runtime benchmark metrics: ORM query identifiers, SQL statements, prepared statement count, execution time, CPU time, thread allocation, GC count, and estimated database rows.

OpenAPI UI:

```text
http://localhost:8080/swagger-ui.html
```

## Domain Model

```text
Country (1) ------ (*) Author
Author  (1) ------ (*) Book
Author  (1) ------ (*) Award
```

Current relationship shape:

| Entity field | Mapping |
| --- | --- |
| `Author.country` | `@ManyToOne(fetch = FetchType.LAZY)` |
| `Author.books` | `@OneToMany(fetch = FetchType.LAZY)` as `List<Book>` |
| `Author.awards` | `@OneToMany(fetch = FetchType.LAZY)` as `Set<Award>` |
| `Book.author` | `@ManyToOne(fetch = FetchType.LAZY)` |
| `Award.author` | `@ManyToOne(fetch = FetchType.LAZY)` |

The demo does not solve N+1 by switching relationships to `EAGER`. Lazy loading remains part of the lesson.

## Seed Data

`DataInitializer` creates data at startup.

Default dataset:

```text
50 countries
500 authors
20 books per author
10 awards per author
10,000-character biography per author
```

Large dataset flag:

```yaml
demo:
  large-dataset: true
```

Large dataset:

```text
50 countries
5,000 authors
50 books per author
20 awards per author
10,000-character biography per author
```

The large dataset is intended for stressing memory, CPU, GC, row multiplication, and failure modes.

## Runtime Configuration

Important settings:

```yaml
spring:
  datasource:
    url: jdbc:p6spy:mysql://127.0.0.1:3306/assessment_service?...
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
  jpa:
    open-in-view: false
    database-platform: org.hibernate.dialect.MySQLDialect
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        format_sql: true
        generate_statistics: true
        query:
          fail_on_pagination_over_collection_fetch: false
```

Meaning:

- `open-in-view: false`: lazy loading must happen inside service transactions, not during Jackson serialization.
- `generate_statistics: true`: Hibernate statistics are available to `BenchmarkAspect`.
- `fail_on_pagination_over_collection_fetch: false`: Hibernate demonstrates in-memory pagination instead of failing fast.
- P6Spy is used to collect real SQL statements with bind values through `CollectingP6SpyLogger`.

`build.gradle` also constrains the demo runtime:

```groovy
tasks.named("bootRun") {
    jvmArgs = [
            "-Xms256m",
            "-Xmx512m",
            "-XX:MaxMetaspaceSize=256m",
            "-XX:ActiveProcessorCount=2"
    ]
}
```

These limits are intentional. They make the Cartesian-product endpoint easier to understand because the problematic query can exhaust memory instead of merely returning a statistic.

## Run

From repository root:

```bash
./gradlew :01-join-fetch:bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat :01-join-fetch:bootRun
```

The app expects MySQL by default. Override `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` if needed.

## Active Controller

```text
com.example.joinfetch.controller.AuthorController
Base path: /demos/authors
```

Controller tags:

- `Baseline`
- `JOIN FETCH`
- `Pagination`

## Active APIs

| Endpoint | Method | Scenario | Result DTO | Notes |
| --- | --- | --- | --- | --- |
| `GET /demos/authors/baseline` | `getBaselineAuthors` | `AUTHORS_ONLY` | `AuthorBasicDto` | Loads authors only; does not touch lazy associations. |
| `GET /demos/authors/n-plus-one/book` | `getAuthorsWithNPlusOneBooks` | `N_PLUS_ONE_BOOK` | `AuthorBooksDto` | Intentionally maps `books` inside the transaction to trigger lazy collection queries. |
| `GET /demos/authors/n-plus-one/book-and-award` | `getAuthorsWithNPlusOneBooksAndAwards` | `N_PLUS_ONE_BOOK_AWARD` | `AuthorBooksAwardsDto` | Intentionally maps both `books` and `awards`. |
| `GET /demos/authors/join-fetch/country` | `getAuthorsWithJoinFetchedCountry` | `JOIN_FETCH_TO_ONE` | `AuthorCountryDto` | Fetches `country`; response has no `books` or `awards`. |
| `GET /demos/authors/join-fetch/book` | `getAuthorsWithJoinFetchedBooks` | `JOIN_FETCH_BOOKS` | `AuthorBooksDto` | Fetches `books`; response has no `country` or `awards`. |
| `GET /demos/authors/join-fetch/book-and-award` | `getAuthorsWithJoinFetchedBooksAndAwards` | `JOIN_FETCH_CARTESIAN` | `AuthorBooksAwardsDto` | Demonstrates row explosion and may fail with OOM under resource limits. |
| `GET /demos/authors/pagination/n-plus-one/book?page=0&size=10` | `demoPaginationNPlusOne` | `PAGINATION_N_PLUS_ONE_BOOK` | `AuthorBooksDto` | Paged authors, then lazy books. |
| `GET /demos/authors/pagination/join-fetch/country?page=0&size=10` | `demoPaginationToOne` | `PAGINATION_TO_ONE` | `AuthorCountryDto` | Safe to-one fetch join pagination. |
| `GET /demos/authors/pagination/join-fetch/book?page=0&size=10` | `demoPaginationBooksBad` | `PAGINATION_COLLECTION_JOIN_FETCH` | `AuthorBooksDto` | Collection fetch join with pageable; may paginate in memory. |
| `GET /demos/authors/pagination/two-step/book?page=0&size=10` | `demoSafePagination` | `SAFE_PAGINATION_TWO_STEP` | `AuthorBooksDto` | Recommended two-step pagination for books. |

## Response Contract

All benchmark APIs return `com.example.joinfetch.dto.record.Response<T>`:

```java
public record Response<T>(
        String scenario,
        long ormQueryExecutionCount,
        List<String> ormQueries,
        long sqlStatementCount,
        long preparedStatementCount,
        List<String> sqlStatements,
        long estimatedDatabaseRows,
        double executionTimeMs,
        double cpuTimeMs,
        double threadAllocatedMb,
        long gcCountDelta,
        T result
) {}
```

Metric notes:

- `scenario`: value from `@BenchmarkScenario` on the service method.
- `ormQueryExecutionCount`: `Hibernate Statistics#getQueryExecutionCount()`.
- `ormQueries`: query identifiers from `Hibernate Statistics#getQueries()`; these are not guaranteed to be JPQL strings in every Hibernate path.
- `sqlStatementCount`: number of SQL statements collected by P6Spy for this benchmark request.
- `preparedStatementCount`: `Hibernate Statistics#getPrepareStatementCount()`.
- `sqlStatements`: preview of collected SQL statements, limited to the first 20 statements to keep the JSON readable.
- `estimatedDatabaseRows`: scenario-specific estimate, not a JDBC `ResultSet` row counter.
- `executionTimeMs`: wall-clock time measured by `BenchmarkAspect`.
- `cpuTimeMs`: current request thread CPU time when supported by the JVM.
- `threadAllocatedMb`: allocated bytes by the request thread converted to MB when supported; `-1` means unavailable.
- `gcCountDelta`: JVM GC collection-count delta during the request.
- `result`: scenario-specific DTO data preview.

The service currently limits `result` to the first 5 DTOs for full-list scenarios. Metrics such as `estimatedDatabaseRows`, query counts, CPU, allocation, and GC still represent the full benchmark work done before the preview is returned.

Example shape for `GET /demos/authors/join-fetch/book`:

```json
{
  "scenario": "JOIN_FETCH_BOOKS",
  "ormQueryExecutionCount": 1,
  "ormQueries": [
    "select distinct a from Author a join fetch a.books order by a.id"
  ],
  "sqlStatementCount": 1,
  "preparedStatementCount": 1,
  "sqlStatements": [
    "select ... from authors ... join books ..."
  ],
  "estimatedDatabaseRows": 10000,
  "executionTimeMs": 211.4,
  "cpuTimeMs": 156.2,
  "threadAllocatedMb": 18.7,
  "gcCountDelta": 0,
  "result": [
    {
      "id": 1,
      "name": "Author 1",
      "books": [
        {
          "id": 1,
          "title": "Author 1 - Book 1",
          "publishYear": 1981
        }
      ]
    }
  ]
}
```

This response intentionally has no `country`, no `awards`, no Hibernate proxy object, no persistence collection type, and no `Book -> Author` back-reference.

## Result DTOs

| DTO | Fields | Used by |
| --- | --- | --- |
| `AuthorBasicDto` | `id`, `name` | baseline authors only |
| `CountryDto` | `id`, `name`, `location`, `region` | nested inside `AuthorCountryDto` |
| `BookDto` | `id`, `title`, `publishYear` | nested inside `AuthorBooksDto`, `AuthorBooksAwardsDto` |
| `AwardDto` | `id`, `name` | nested inside `AuthorBooksAwardsDto` |
| `AuthorCountryDto` | `id`, `name`, `country` | country fetch scenarios |
| `AuthorBooksDto` | `id`, `name`, `books` | books fetch scenarios |
| `AuthorBooksAwardsDto` | `id`, `name`, `books`, `awards` | books + awards scenarios |

The DTO mappers deliberately access only the association needed by the scenario. For Cartesian joins, duplicate child DTOs are de-duplicated by child ID for readable JSON, but `estimatedDatabaseRows` still uses the raw row explosion formula.

## Scenario Details

### Baseline Authors Only

```http
GET /demos/authors/baseline
```

Loads authors through `findAll()` and maps only `id` and `name`. No lazy association is accessed by the mapper.

### N+1: Books

```http
GET /demos/authors/n-plus-one/book
```

Flow:

1. Load all authors.
2. Map `AuthorBooksDto` inside the transaction.
3. Mapping calls `author.getBooks()` intentionally.
4. Hibernate initializes lazy books collections inside the benchmark.

This endpoint is intentionally bad so the benchmark can show many SQL statements.

### N+1: Books And Awards

```http
GET /demos/authors/n-plus-one/book-and-award
```

Same baseline as books, but mapping touches both `author.getBooks()` and `author.getAwards()`. This can produce many more lazy collection SQL statements.

### JOIN FETCH Country

```http
GET /demos/authors/join-fetch/country
```

Repository query:

```jpql
select a
from Author a
join fetch a.country
order by a.id
```

This is safe because `country` is to-one. It avoids lazy country queries without multiplying author rows.

### JOIN FETCH Books

```http
GET /demos/authors/join-fetch/book
```

Repository query:

```jpql
select distinct a
from Author a
join fetch a.books
order by a.id
```

This removes the books N+1 problem, but the SQL result still contains approximately one row per author-book pair.

Default dataset estimate:

```text
500 authors x 20 books = 10,000 joined rows
```

### Cartesian Product: Books And Awards

```http
GET /demos/authors/join-fetch/book-and-award
```

Repository query:

```jpql
select distinct a
from Author a
join fetch a.books
join fetch a.awards
order by a.id
```

This is the dangerous endpoint.

Formula:

```text
estimated rows = sum(bookCount x awardCount) per author
```

Default dataset estimate:

```text
500 authors x 20 books x 10 awards = 100,000 joined rows
```

Large dataset estimate:

```text
5,000 authors x 50 books x 20 awards = 5,000,000 joined rows
```

Because `bootRun` limits heap to `-Xmx512m` and each author contains a large biography, this endpoint may fail with `OutOfMemoryError` before a normal benchmark response can be returned. That failure is the point of the demo: one SQL statement can still be catastrophic when it returns a multiplied result set that Hibernate must hydrate and assemble.

If this endpoint fails, do not read it as missing statistics. Read it as evidence that the Cartesian fetch shape exceeded the configured JVM resource budget.

### Pagination N+1 Books

```http
GET /demos/authors/pagination/n-plus-one/book?page=0&size=10
```

Pages authors first, then maps `AuthorBooksDto`, intentionally touching lazy books inside the transaction. The N+1 effect is bounded by page size but still visible.

### Pagination JOIN FETCH Country

```http
GET /demos/authors/pagination/join-fetch/country?page=0&size=10
```

Safe because the fetch join is to-one. SQL pagination remains stable.

### Pagination JOIN FETCH Books

```http
GET /demos/authors/pagination/join-fetch/book?page=0&size=10
```

Collection fetch join with `Pageable` is intentionally bad. Hibernate can fetch a much larger joined result, de-duplicate authors, and then apply pagination in memory.

Warning to look for:

```text
HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
```

### Safe Two-Step Pagination For Books

```http
GET /demos/authors/pagination/two-step/book?page=0&size=10
```

Step 1:

```jpql
select a.id
from Author a
order by a.id
```

Step 2:

```jpql
select distinct a
from Author a
join fetch a.books
where a.id in :ids
order by a.id
```

This keeps SQL row volume bounded by the requested page of author IDs.

## How To Read Results

A lower SQL statement count is not automatically better. Compare it with:

- `estimatedDatabaseRows`
- `threadAllocatedMb`
- `gcCountDelta`
- `cpuTimeMs`
- `executionTimeMs`
- `ormQueryExecutionCount`
- `preparedStatementCount`
- `sqlStatementCount`

Core lesson:

```text
One SQL query can still be slow or fail if it returns a huge joined result set.
```

For multiple to-many `JOIN FETCH` scenarios, query count can look excellent while memory, allocation, CPU, GC, and row volume are terrible.
