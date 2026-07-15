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
| `GET /demos/authors/pagination/self-defined/book?page=0&size=10&queryType=SQL` | `demoSelfDefinedJoinFetchPagination` | `MANUAL_DEFINED_JOIN_FETCH_PAGINATION` | `AuthorBooksDto` | Manual SQL pagination over joined rows; can return an incorrect Author page. |
| `GET /demos/authors/pagination/self-defined/book?page=0&size=10&queryType=JPQL` | `demoSelfDefinedJoinFetchPagination` | `MANUAL_DEFINED_JOIN_FETCH_PAGINATION` | none | Manual JPQL limit/offset attempt; currently throws `UnknownParameterException`. |

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
- `sqlStatements`: preview of collected SQL statements, **limited to the first 20 statements to keep the JSON readable.**
- `estimatedDatabaseRows`: scenario-specific estimate, not a JDBC `ResultSet` row counter.
- `executionTimeMs`: wall-clock time measured by `BenchmarkAspect`.
- `cpuTimeMs`: current request thread CPU time when supported by the JVM.
- `threadAllocatedMb`: allocated bytes by the request thread converted to MB when supported; `-1` means unavailable.
- `gcCountDelta`: JVM GC collection-count delta during the request.
- `result`: scenario-specific DTO data preview. **(top 5 DTOs)**

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

## API Details From `AuthorController`

This section copies the `@Operation(summary = ...)` and `@Operation(description = ...)` intent from `AuthorController`. Response samples are intentionally shortened: `...` means repeated SQL statements, repeated DTO items, or runtime-dependent metric values were omitted to keep the README readable. The top-level response attributes are still shown.

### `GET /demos/authors/baseline`

**Operation summary:** Load Authors only

**Operation description:**

```text
Scenario: AUTHORS_ONLY

Purpose: Establish the baseline cost of loading Author rows without touching lazy associations.

Service flow: AuthorService.demoAuthorsOnly() calls authorRepository.findAll(), maps each Author to AuthorBasicDto, and returns a preview of the first 5 DTOs.

Result DTO: AuthorBasicDto with id and name only.

Associations accessed by mapper: none. The mapper does not call getCountry(), getBooks(), or getAwards().

Expected behavior: one ORM author query and no lazy association SQL during DTO mapping.

Metrics: estimatedDatabaseRows is the full number of Authors loaded, while result contains only a small preview.
```

**Case value:** baseline control sample. It proves that DTO serialization itself does not trigger lazy SQL.

**Shortened response:**

```jsonc
{
  "scenario": "AUTHORS_ONLY",
  "ormQueryExecutionCount": 1,
  "ormQueries": [
    "[CRITERIA] select a1_0.id,a1_0.biography,a1_0.country_id,a1_0.name from authors a1_0"
  ],
  "sqlStatementCount": 1,
  "preparedStatementCount": 1,
  "sqlStatements": [
    "select a1_0.id,a1_0.biography,a1_0.country_id,a1_0.name from authors a1_0"
  ],
  "estimatedDatabaseRows": 500,
  "executionTimeMs": ...,
  "cpuTimeMs": ...,
  "threadAllocatedMb": ...,
  "gcCountDelta": ...,
  "result": [
    { "id": 1, "name": "Author 1" },
    { "id": 2, "name": "Author 2" },
    "..."
  ]
}
```

### `GET /demos/authors/n-plus-one/book`

**Operation summary:** N+1 Books

**Operation description:**

```text
Scenario: N_PLUS_ONE_BOOK

Purpose: Demonstrate the classic N+1 problem for a lazy one-to-many association.

Service flow: AuthorService.demoNPlusOneBook() loads all Authors first, then maps to AuthorBooksDto inside the transaction. The mapper intentionally calls author.getBooks().

Result DTO: AuthorBooksDto with id, name, and books. It does not include country or awards.

Why this is bad: after the initial Author query, Hibernate must initialize the books collection for each Author. Without batching, this can become one Author query plus many Books queries.

Metrics: SQL statements, ORM query execution count, CPU time, allocation, GC delta, and estimatedDatabaseRows include the DTO mapping work that triggers the lazy loads.
```

**Case value:** the response needs `books`, but the query only loaded Authors. Mapping `books` intentionally triggers lazy collection SQL inside the benchmark.

**Shortened response:**

```jsonc
{
  "scenario": "N_PLUS_ONE_BOOK",
  "ormQueryExecutionCount": ...,
  "ormQueries": [
    "[CRITERIA] select a1_0.id,a1_0.biography,a1_0.country_id,a1_0.name from authors a1_0",
    "..."
  ],
  "sqlStatementCount": 501,
  "preparedStatementCount": 501,
  "sqlStatements": [
    "select a1_0.id,a1_0.biography,a1_0.country_id,a1_0.name from authors a1_0",
    "select b1_0.author_id,b1_0.id,b1_0.publish_year,b1_0.title from books b1_0 where b1_0.author_id=1",
    "select b1_0.author_id,b1_0.id,b1_0.publish_year,b1_0.title from books b1_0 where b1_0.author_id=2",
    "... repeated lazy book queries per Author ..."
  ],
  "estimatedDatabaseRows": 10500,
  "executionTimeMs": ...,
  "cpuTimeMs": ...,
  "threadAllocatedMb": ...,
  "gcCountDelta": ...,
  "result": [
    {
      "id": 1,
      "name": "Author 1",
      "books": [
        { "id": 1, "title": "Author 1 - Book 1", "publishYear": 1981 },
        "... more books for Author 1 ..."
      ]
    },
    "... more Authors ..."
  ]
}
```

### `GET /demos/authors/n-plus-one/book-and-award`

**Operation summary:** N+1 Books and Awards

**Operation description:**

```text
Scenario: N_PLUS_ONE_BOOK_AWARD

Purpose: Demonstrate N+1 behavior for two lazy collections on the same root entity.

Service flow: AuthorService.demoNPlusOneBookAward() loads all Authors, then maps to AuthorBooksAwardsDto inside the transaction. The mapper intentionally calls author.getBooks() and author.getAwards().

Result DTO: AuthorBooksAwardsDto with id, name, books, and awards. It does not include country.

Why this is worse than the Books-only case: each Author can require lazy initialization for both collections, so the request can produce many Books queries and many Awards queries after the initial Author query.

Metrics: estimatedDatabaseRows is based on Authors plus loaded Books plus loaded Awards. The JSON result is only a preview, but the benchmark work covers the full loaded set.
```

**Case value:** touching two lazy collections makes the N+1 pattern worse: one root Author query plus lazy Books queries plus lazy Awards queries.

**Shortened response:**

```jsonc
{
  "scenario": "N_PLUS_ONE_BOOK_AWARD",
  "ormQueryExecutionCount": ...,
  "ormQueries": [
    "[CRITERIA] select a1_0.id,a1_0.biography,a1_0.country_id,a1_0.name from authors a1_0",
    "..."
  ],
  "sqlStatementCount": 1001,
  "preparedStatementCount": 1001,
  "sqlStatements": [
    "select a1_0.id,a1_0.biography,a1_0.country_id,a1_0.name from authors a1_0",
    "select b1_0.author_id,b1_0.id,b1_0.publish_year,b1_0.title from books b1_0 where b1_0.author_id=1",
    "select a1_0.author_id,a1_0.id,a1_0.name from awards a1_0 where a1_0.author_id=1",
    "... repeated lazy book and award queries per Author ..."
  ],
  "estimatedDatabaseRows": 15500,
  "executionTimeMs": ...,
  "cpuTimeMs": ...,
  "threadAllocatedMb": ...,
  "gcCountDelta": ...,
  "result": [
    {
      "id": 1,
      "name": "Author 1",
      "books": [
        { "id": 1, "title": "Author 1 - Book 1", "publishYear": 1981 },
        "..."
      ],
      "awards": [
        { "id": 1, "name": "Author 1 - Award 1" },
        "..."
      ]
    },
    "... more Authors ..."
  ]
}
```

### `GET /demos/authors/join-fetch/country`

**Operation summary:** JOIN FETCH Country

**Operation description:**

```text
Scenario: JOIN_FETCH_TO_ONE

Purpose: Show the safe JOIN FETCH case for a to-one association.

Repository query: select a from Author a join fetch a.country order by a.id

Service flow: AuthorService.demoJoinFetchToOne() fetches Authors with Country in one query and maps to AuthorCountryDto inside the transaction.

Result DTO: AuthorCountryDto with id, name, and country. It does not include books or awards.

Why this is safe: a to-one join does not multiply Author rows, so SQL row volume stays close to the number of Authors.

Expected behavior: no lazy SQL for country during serialization because the response contains DTOs, not JPA entities.
```

**Case value:** safe `JOIN FETCH` for a to-one relation. It fetches `country` without row multiplication and without lazy SQL during serialization.

**Shortened response:**

```jsonc
{
  "scenario": "JOIN_FETCH_TO_ONE",
  "ormQueryExecutionCount": 1,
  "ormQueries": [
    "select a from Author a join fetch a.country order by a.id"
  ],
  "sqlStatementCount": 1,
  "preparedStatementCount": 1,
  "sqlStatements": [
    "select a1_0.id,a1_0.biography,c1_0.id,c1_0.location,c1_0.name,c1_0.region,a1_0.name from authors a1_0 join countries c1_0 on c1_0.id=a1_0.country_id order by a1_0.id"
  ],
  "estimatedDatabaseRows": 500,
  "executionTimeMs": ...,
  "cpuTimeMs": ...,
  "threadAllocatedMb": ...,
  "gcCountDelta": ...,
  "result": [
    {
      "id": 1,
      "name": "Author 1",
      "country": { "id": 1, "name": "Country 1", "location": "Region 1", "region": "Continent Group 1" }
    },
    "... more Authors ..."
  ]
}
```

### `GET /demos/authors/join-fetch/book`

**Operation summary:** JOIN FETCH Books

**Operation description:**

```text
Scenario: JOIN_FETCH_BOOKS

Purpose: Remove the lazy Books N+1 problem by fetching one collection with the root Authors.

Repository query: select distinct a from Author a join fetch a.books order by a.id

Service flow: AuthorService.demoJoinFetchBooks() fetches Authors and Books together, then maps to AuthorBooksDto inside the transaction.

Result DTO: AuthorBooksDto with id, name, and books. It must not contain country or awards.

Tradeoff: SQL statement count is low, but the database still returns approximately one joined row per Author-Book pair. This can be large even when only one SQL statement is executed.

Metrics: estimatedDatabaseRows is based on the number of Book DTOs across the full result, not on the 5-item response preview.
```

**Case value:** removes Books N+1, but the database result is still large: one row per Author-Book pair.

**Shortened response:**

```jsonc
{
  "scenario": "JOIN_FETCH_BOOKS",
  "ormQueryExecutionCount": 1,
  "ormQueries": [
    "select distinct a from Author a join fetch a.books order by a.id"
  ],
  "sqlStatementCount": 1,
  "preparedStatementCount": 1,
  "sqlStatements": [
    "select distinct a1_0.id,a1_0.biography,b1_0.author_id,b1_0.id,b1_0.publish_year,b1_0.title,a1_0.country_id,a1_0.name from authors a1_0 join books b1_0 on a1_0.id=b1_0.author_id order by a1_0.id"
  ],
  "estimatedDatabaseRows": 10000,
  "executionTimeMs": ...,
  "cpuTimeMs": ...,
  "threadAllocatedMb": ...,
  "gcCountDelta": ...,
  "result": [
    {
      "id": 1,
      "name": "Author 1",
      "books": [
        { "id": 1, "title": "Author 1 - Book 1", "publishYear": 1981 },
        "... more books for Author 1 ..."
      ]
    },
    "... more Authors ..."
  ]
}
```

### `GET /demos/authors/join-fetch/book-and-award`

**Operation summary:** JOIN FETCH Books and Awards, Cartesian failure demo

**Operation description:**

```text
Scenario: JOIN_FETCH_CARTESIAN

Purpose: Demonstrate the dangerous multiple-collection JOIN FETCH shape: Author.books plus Author.awards.

Repository query: select distinct a from Author a join fetch a.books join fetch a.awards order by a.id

Service flow: AuthorService.demoCartesianProduct() attempts to fetch Authors, Books, and Awards in one query, then map to AuthorBooksAwardsDto.

Result DTO if it completes: AuthorBooksAwardsDto with id, name, books, and awards. It does not include country.

Important current behavior: this endpoint is expected to overload memory under the configured demo resource limits. The practical result of the request can be OutOfMemoryError instead of a usable benchmark response.

Why it fails: for each Author, every Book row is combined with every Award row. For example, 20 Books x 10 Awards becomes 200 joined rows for one Author before Hibernate de-duplicates the root Author objects.

How to read the failure: an OutOfMemoryError is the demonstration. It means one SQL statement created a result set too large for the configured JVM heap to hydrate and assemble.
```

**Case value:** low query count can be misleading. One SQL statement can still create a Cartesian row explosion and exhaust heap.

**Expected result under demo limits:**

```text
OutOfMemoryError
```

If the request completes on a larger heap, the response shape would be:

```jsonc
{
  "scenario": "JOIN_FETCH_CARTESIAN",
  "ormQueryExecutionCount": 1,
  "ormQueries": [
    "select distinct a from Author a join fetch a.books join fetch a.awards order by a.id"
  ],
  "sqlStatementCount": 1,
  "preparedStatementCount": 1,
  "sqlStatements": [
    "select distinct ... from authors ... join books ... join awards ..."
  ],
  "estimatedDatabaseRows": 100000,
  "executionTimeMs": ...,
  "cpuTimeMs": ...,
  "threadAllocatedMb": ...,
  "gcCountDelta": ...,
  "result": [
    {
      "id": 1,
      "name": "Author 1",
      "books": [ { "id": 1, "title": "Author 1 - Book 1", "publishYear": 1981 }, "..." ],
      "awards": [ { "id": 1, "name": "Author 1 - Award 1" }, "..." ]
    },
    "..."
  ]
}
```

### `GET /demos/authors/pagination/n-plus-one/book?page=0&size=10`

**Operation summary:** Pagination N+1 Books

**Operation description:**

```text
Scenario: PAGINATION_N_PLUS_ONE_BOOK

Purpose: Show that pagination limits the number of root Authors, but lazy collection access can still create N+1 behavior inside the page.

Service flow: AuthorService.demoPaginationNPlusOne() loads a Page<Author>, then maps the page content to AuthorBooksDto inside the transaction. The mapper intentionally calls author.getBooks().

Result DTO: AuthorBooksDto with id, name, and books.

Expected behavior: Spring Data runs the page content query and count query, then Hibernate initializes Books for Authors in the returned page.

Metrics: estimatedDatabaseRows is page Authors plus Books loaded for that page.
```

**Case value:** pagination reduces the number of Authors, but lazy Books still create N+1 inside the page.

**Shortened response:**

```jsonc
{
  "scenario": "PAGINATION_N_PLUS_ONE_BOOK",
  "ormQueryExecutionCount": 2,
  "ormQueries": [
    "[CRITERIA] select a1_0.id,a1_0.biography,a1_0.country_id,a1_0.name from authors a1_0 limit ?,?",
    "[CRITERIA] select count(a1_0.id) from authors a1_0"
  ],
  "sqlStatementCount": 12,
  "preparedStatementCount": 12,
  "sqlStatements": [
    "select a1_0.id,a1_0.biography,a1_0.country_id,a1_0.name from authors a1_0 limit 0,10",
    "select count(a1_0.id) from authors a1_0",
    "select b1_0.author_id,b1_0.id,b1_0.publish_year,b1_0.title from books b1_0 where b1_0.author_id=1",
    "... lazy book queries for Authors in the page ..."
  ],
  "estimatedDatabaseRows": 210,
  "executionTimeMs": ...,
  "cpuTimeMs": ...,
  "threadAllocatedMb": ...,
  "gcCountDelta": ...,
  "result": [
    {
      "id": 1,
      "name": "Author 1",
      "books": [ { "id": 1, "title": "Author 1 - Book 1", "publishYear": 1981 }, "..." ]
    },
    "... more Authors in the page ..."
  ]
}
```

### `GET /demos/authors/pagination/join-fetch/country?page=0&size=10`

**Operation summary:** Pagination JOIN FETCH Country

**Operation description:**

```text
Scenario: PAGINATION_TO_ONE

Purpose: Show the safe pagination case for a to-one JOIN FETCH.

Repository query: select a from Author a join fetch a.country order by a.id

Service flow: AuthorService.demoPaginationToOne() loads a Page<Author> with Country fetched, then maps to AuthorCountryDto.

Result DTO: AuthorCountryDto with id, name, and country. It does not contain books or awards.

Why this is safe: joining a to-one association does not multiply Author rows, so SQL limit and offset remain meaningful.

Metrics: estimatedDatabaseRows is the number of Authors in the returned page preview source.
```

**Case value:** this is the safe pagination + fetch join case because `country` is to-one.

**Shortened response:**

```jsonc
{
  "scenario": "PAGINATION_TO_ONE",
  "ormQueryExecutionCount": 2,
  "ormQueries": [
    "select a from Author a join fetch a.country order by a.id",
    "select count(a) from Author a"
  ],
  "sqlStatementCount": 2,
  "preparedStatementCount": 2,
  "sqlStatements": [
    "select a1_0.id,a1_0.biography,c1_0.id,c1_0.location,c1_0.name,c1_0.region,a1_0.name from authors a1_0 join countries c1_0 on c1_0.id=a1_0.country_id order by a1_0.id limit 0,10",
    "select count(a1_0.id) from authors a1_0"
  ],
  "estimatedDatabaseRows": 10,
  "executionTimeMs": ...,
  "cpuTimeMs": ...,
  "threadAllocatedMb": ...,
  "gcCountDelta": ...,
  "result": [
    {
      "id": 1,
      "name": "Author 1",
      "country": { "id": 1, "name": "Country 1", "location": "Region 1", "region": "Continent Group 1" }
    },
    "... more Authors in the page ..."
  ]
}
```

### `GET /demos/authors/pagination/join-fetch/book?page=0&size=10`

**Operation summary:** Pagination JOIN FETCH Books, Hibernate in-memory pagination demo

**Operation description:**

```text
Scenario: PAGINATION_COLLECTION_JOIN_FETCH

Purpose: Demonstrate why Pageable should not be applied directly to a collection JOIN FETCH.

Repository query: select distinct a from Author a join fetch a.books order by a.id

Service flow: AuthorService.demoPaginationBooksBad() calls the pageable repository method, maps returned Authors to AuthorBooksDto, and estimates processed rows from Hibernate entity load statistics.

Result DTO: AuthorBooksDto with id, name, and books.

Why this is bad: Hibernate may fetch a large Author-Book joined result, de-duplicate Authors, and apply pagination in memory. The response page can look small while the database and Hibernate processed much more data.

Warning to look for: HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory.

Recommendation: use the two-step pagination endpoint instead.
```

**Case value:** the response page is small, but Hibernate can load and de-duplicate a much larger joined result before applying pagination in memory.

**Shortened response:**

```jsonc
{
  "scenario": "PAGINATION_COLLECTION_JOIN_FETCH",
  "ormQueryExecutionCount": ...,
  "ormQueries": [
    "select distinct a from Author a join fetch a.books order by a.id",
    "..."
  ],
  "sqlStatementCount": ...,
  "preparedStatementCount": ...,
  "sqlStatements": [
    "select distinct a1_0.id,a1_0.biography,b1_0.author_id,b1_0.id,b1_0.publish_year,b1_0.title,a1_0.country_id,a1_0.name from authors a1_0 join books b1_0 on a1_0.id=b1_0.author_id order by a1_0.id",
    "..."
  ],
  "estimatedDatabaseRows": 10000,
  "executionTimeMs": ...,
  "cpuTimeMs": ...,
  "threadAllocatedMb": ...,
  "gcCountDelta": ...,
  "result": [
    {
      "id": 1,
      "name": "Author 1",
      "books": [ { "id": 1, "title": "Author 1 - Book 1", "publishYear": 1981 }, "..." ]
    },
    "... page result after Hibernate de-duplication ..."
  ]
}
```

### `GET /demos/authors/pagination/two-step/book?page=0&size=10`

**Operation summary:** Two-step Pagination Books

**Operation description:**

```text
Scenario: SAFE_PAGINATION_TWO_STEP

Purpose: Demonstrate a safer pattern for paginating Authors with Books.

Step 1 query: select a.id from Author a order by a.id

Step 2 query: select distinct a from Author a join fetch a.books where a.id in :ids order by a.id

Service flow: AuthorService.demoSafePagination() pages stable Author IDs first, then fetches Books only for those IDs, then maps to AuthorBooksDto.

Result DTO: AuthorBooksDto with id, name, and books.

Why this is better: SQL row volume is bounded by the requested page of Author IDs, not by all joined Author-Book rows in the table.

Metrics: estimatedDatabaseRows is page IDs plus Books for those Authors.
```

**Case value:** recommended collection pagination pattern. It uses two steps to keep row volume bounded and keep the Author page correct.

**Shortened response:**

```jsonc
{
  "scenario": "SAFE_PAGINATION_TWO_STEP",
  "ormQueryExecutionCount": 2,
  "ormQueries": [
    "select a.id from Author a order by a.id",
    "select distinct a from Author a join fetch a.books where a.id in :ids order by a.id"
  ],
  "sqlStatementCount": 2,
  "preparedStatementCount": 2,
  "sqlStatements": [
    "select a1_0.id from authors a1_0 order by a1_0.id limit 0,10",
    "select distinct a1_0.id,a1_0.biography,b1_0.author_id,b1_0.id,b1_0.publish_year,b1_0.title,a1_0.country_id,a1_0.name from authors a1_0 join books b1_0 on a1_0.id=b1_0.author_id where a1_0.id in (?,?,?,?,?,?,?,?,?,?) order by a1_0.id"
  ],
  "estimatedDatabaseRows": 210,
  "executionTimeMs": ...,
  "cpuTimeMs": ...,
  "threadAllocatedMb": ...,
  "gcCountDelta": ...,
  "result": [
    {
      "id": 1,
      "name": "Author 1",
      "books": [ { "id": 1, "title": "Author 1 - Book 1", "publishYear": 1981 }, "..." ]
    },
    "... more Authors in requested page ..."
  ]
}
```

### `GET /demos/authors/pagination/self-defined/book?page=0&size=10&queryType=SQL`

**Operation summary:** Self-defined Pagination Books, SQL vs JPQL failure cases

**Operation description:**

```text
Scenario: MANUAL_DEFINED_JOIN_FETCH_PAGINATION

Purpose: Demonstrate why manually adding limit/offset to a fetch-style pagination query is not a safe replacement for two-step pagination.

Endpoint parameter: queryType controls the implementation. Use queryType=SQL for the native SQL version. Any other value uses the JPQL version.

SQL case: AuthorService.demoSelfDefinedJoinFetchPaginationSQL() calls a native query that joins authors and books and applies limit/offset to joined SQL rows. This can produce an incorrect Author response because pagination is applied to rows, not Authors. In the current demo data, the response can contain only 1 Author in the list even when size is larger, because the limited joined rows may all belong to the same Author.

JPQL case: AuthorService.demoSelfDefinedJoinFetchPaginationJPQL() attempts to use limit/offset syntax in a JPQL query. This currently fails with: org.hibernate.query.UnknownParameterException: Could not resolve jakarta.persistence.Parameter 'SqmNamedParameter(noItem)' to org.hibernate.query.QueryParameter

Result DTO if the SQL case completes: AuthorBooksDto with id, name, and books. The result is intentionally not trustworthy as a correct Author page.

Recommendation: use /demos/authors/pagination/two-step/book for a bounded and correct Author page with Books.
```

**Case value:** native SQL `limit/offset` is applied to joined rows, not distinct Authors. The response may contain repeated `Author 1` entries and only one unique Author.

**Shortened response:**

```jsonc
{
  "scenario": "MANUAL_DEFINED_JOIN_FETCH_PAGINATION",
  "ormQueryExecutionCount": 1,
  "ormQueries": [
    "select a.* from authors a join books b on b.author_id = a.id order by a.id limit ? offset ?"
  ],
  "sqlStatementCount": 2,
  "preparedStatementCount": 2,
  "sqlStatements": [
    "select a.* from authors a join books b on b.author_id = a.id order by a.id limit 10 offset 0",
    "select b1_0.author_id,b1_0.id,b1_0.publish_year,b1_0.title from books b1_0 where b1_0.author_id=1"
  ],
  "estimatedDatabaseRows": 10,
  "executionTimeMs": ...,
  "cpuTimeMs": ...,
  "threadAllocatedMb": ...,
  "gcCountDelta": ...,
  "result": [
    {
      "id": 1,
      "name": "Author 1",
      "books": [ { "id": 1, "title": "Author 1 - Book 1", "publishYear": 1981 }, "..." ]
    },
    "... repeated Author 1 rows caused by row-level SQL pagination ..."
  ]
}
```

### `GET /demos/authors/pagination/self-defined/book?page=0&size=10&queryType=JPQL`

**Operation summary:** Self-defined Pagination Books, SQL vs JPQL failure cases

**Operation description:**

```text
Scenario: MANUAL_DEFINED_JOIN_FETCH_PAGINATION

Purpose: Demonstrate why manually adding limit/offset to a fetch-style pagination query is not a safe replacement for two-step pagination.

Endpoint parameter: queryType controls the implementation. Use queryType=SQL for the native SQL version. Any other value uses the JPQL version.

SQL case: AuthorService.demoSelfDefinedJoinFetchPaginationSQL() calls a native query that joins authors and books and applies limit/offset to joined SQL rows. This can produce an incorrect Author response because pagination is applied to rows, not Authors. In the current demo data, the response can contain only 1 Author in the list even when size is larger, because the limited joined rows may all belong to the same Author.

JPQL case: AuthorService.demoSelfDefinedJoinFetchPaginationJPQL() attempts to use limit/offset syntax in a JPQL query. This currently fails with: org.hibernate.query.UnknownParameterException: Could not resolve jakarta.persistence.Parameter 'SqmNamedParameter(noItem)' to org.hibernate.query.QueryParameter

Result DTO if the SQL case completes: AuthorBooksDto with id, name, and books. The result is intentionally not trustworthy as a correct Author page.

Recommendation: use /demos/authors/pagination/two-step/book for a bounded and correct Author page with Books.
```

**Case value:** SQL-style `limit` and `offset` are not a safe JPQL fetch-join pagination strategy.

**Current result:**

```text
org.hibernate.query.UnknownParameterException: Could not resolve jakarta.persistence.Parameter 'SqmNamedParameter(noItem)' to org.hibernate.query.QueryParameter
```

No normal `Response<T>` body is returned for this branch.

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




## Appendix A: `MultipleBagFetchException`

> `MultipleBagFetchException` is Hibernate's protection against fetching multiple bag collections in one joined query. It is related to Cartesian products, but it is not the same as the OOM demo in this module.

### Tag 01 - The Short Version

| Concept | Explanation |
| --- | --- |
| Bag | A `List` collection without an index column such as `@OrderColumn`. |
| Problem | A joined SQL result can repeat the same child row many times. |
| Hibernate concern | Without an index, Hibernate cannot know whether repeated rows are real duplicates or duplicates created by the join. |
| Result | Hibernate can stop the query and throw `MultipleBagFetchException`. |

Typical exception:

```text
org.hibernate.loader.MultipleBagFetchException: cannot simultaneously fetch multiple bags
```

### Tag 02 - Why A Bag Is Ambiguous

In Hibernate, a `List` without `@OrderColumn` is normally treated as a bag. A bag allows duplicate elements, but the database does not store the position of each element.

Example domain data:

```text
Author 1
books  = [Book 1, Book 2]
awards = [Award 1, Award 2, Award 3]
```

If both collections are fetched with joins:

```jpql
select distinct a
from Author a
left join fetch a.books
left join fetch a.awards
```

The database result is multiplied:

| Author | Book | Award |
| --- | --- | --- |
| Author 1 | Book 1 | Award 1 |
| Author 1 | Book 1 | Award 2 |
| Author 1 | Book 1 | Award 3 |
| Author 1 | Book 2 | Award 1 |
| Author 1 | Book 2 | Award 2 |
| Author 1 | Book 2 | Award 3 |

Now each `Book` appears three times, and each `Award` appears two times.

Hibernate cannot safely decide whether a repeated child row is:

1. A legitimate duplicate stored in the bag.
2. A duplicate created by the Cartesian product.

That ambiguity is why Hibernate may reject the query with `MultipleBagFetchException`.

### Tag 03 - How `@OrderColumn` Changes `books`

In this project, `books` is not a plain bag because it has an index column:

```java
@OneToMany(
    mappedBy = "author",
    cascade = CascadeType.ALL,
    orphanRemoval = true,
    fetch = FetchType.LAZY
)
@OrderColumn(name = "book_order")
@Builder.Default
private List<Book> books = new ArrayList<>();
```

`@OrderColumn` adds a position column to the `books` table:

| author_id | book_id | book_order | title |
| --- | --- | --- | --- |
| 1 | 1 | 0 | Author 1 - Book 1 |
| 1 | 2 | 1 | Author 1 - Book 2 |
| 2 | 21 | 0 | Author 2 - Book 1 |
| 2 | 22 | 1 | Author 2 - Book 2 |

The index starts from `0` for each Author because `book_order` stores the position of a Book inside that Author's `books` list.
