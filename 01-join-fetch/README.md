# Module 01: JOIN FETCH Playground

This module is a Spring Boot + Spring Data JPA educational demo for understanding Hibernate `JOIN FETCH` behavior.

It currently demonstrates:

- N+1 queries with lazy collections.
- Good `JOIN FETCH` usage for a to-one association.
- Good but size-sensitive `JOIN FETCH` usage for one collection.
- Bad `JOIN FETCH` usage with multiple collections, causing Cartesian product style row explosion.
- Pagination behavior with normal lazy loading, to-one fetch join, collection fetch join, and safe two-step pagination.
- Runtime observability data such as Hibernate statistics, heap usage, GC count, CPU time, and estimated joined rows.

OpenAPI UI, if Springdoc is enabled in the module build:

```text
http://localhost:8080/swagger-ui.html
```

## Current Domain Model

```text
Country (1) ------ (*) Author
Author  (1) ------ (*) Book
Author  (1) ------ (*) Award
```

Current entity relationships:

- `Author.country`: `@ManyToOne(fetch = FetchType.LAZY)`
- `Author.books`: `@OneToMany(fetch = FetchType.LAZY)` as `List<Book>`
- `Author.awards`: `@OneToMany(fetch = FetchType.LAZY)` as `Set<Award>`
- `Book.author`: `@ManyToOne(fetch = FetchType.LAZY)`
- `Award.author`: `@ManyToOne(fetch = FetchType.LAZY)`

The module keeps relationships `LAZY` by default. It does not solve N+1 by changing relationships to `EAGER`.

## Seed Data

`DataInitializer` creates data at startup.

Default dataset:

```text
50 countries
500 authors
20 books per author
10 awards per author
```

Large dataset flag:

```yaml
demo:
  large-dataset: true
```

When enabled:

```text
50 countries
5000 authors
50 books per author
20 awards per author
```

The large dataset is intended for stressing memory, CPU, GC, and row explosion behavior.

## Runtime Configuration

Important `application.yml` settings:

```yaml
spring:
  jpa:
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
        generate_statistics: true
        query:
          fail_on_pagination_over_collection_fetch: false
```

Meaning:

- `open-in-view: false`: lazy loading must happen inside service transactions, not accidentally in the web layer.
- `format_sql: true`: SQL logs are easier to read.
- `generate_statistics: true`: Hibernate statistics are available in responses.
- `fail_on_pagination_over_collection_fetch: false`: Hibernate is allowed to demonstrate the bad in-memory pagination behavior instead of failing fast.

## Run

From repository root:

```bash
./gradlew :01-join-fetch:bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat :01-join-fetch:bootRun
```

## Active Controller

The active controller is:

```text
com.example.joinfetch.controller.AuthorController
Base path: /demo
```

There is currently no active `BenchmarkController` file in this module. Benchmark-style information is returned inside the existing `/demo` responses through the nested `benchmark` field.

## Active APIs

| Endpoint | Response DTO | Scenario | Good/Bad |
| --- | --- | --- | --- |
| `GET /demo/n-plus-one/book` | `PerformanceResponse` | Load authors, then access lazy books | Bad |
| `GET /demo/n-plus-one/book&award` | `PerformanceResponse` | Load authors, then access lazy books and awards | Bad |
| `GET /demo/join-fetch/to-one` | `PerformanceResponse` | `JOIN FETCH` `Author.country` | Good |
| `GET /demo/join-fetch/books` | `PerformanceResponse` | `JOIN FETCH` one collection, `Author.books` | Good if size is controlled |
| `GET /demo/join-fetch/cartesian` | `CartesianProductDemoResponse` | `JOIN FETCH` books and awards together | Bad |
| `GET /demo/pagination/n-plus-one?page=0&size=10` | `PaginationDemoResponse` | Page authors normally, then touch books | Bad (N+1) |
| `GET /demo/pagination/to-one?page=0&size=10` | `PaginationDemoResponse` | Pageable with to-one fetch join | Good |
| `GET /demo/pagination/books?page=0&size=10` | `PaginationDemoResponse` | Pageable with collection fetch join | Bad |
| `GET /demo/pagination/safe?page=0&size=10` | `PaginationDemoResponse` | Page IDs first, then fetch graph | Recommended |

These endpoints are present in `AuthorController` but currently commented out, so they are not active:

- `GET /demo/batch-fetch`
- `GET /demo/performance/memory-explosion`
- `GET /demo/production/recommendations`

## API Details

### 1. N+1: Books

```http
GET /demo/n-plus-one/book
```

Service method:

```text
AuthorService.demoNPlusOneBook()
```

Main JPQL:

```jpql
select a
from Author a
```

Backend flow:

1. Load a page/list of authors through `authorRepository.findAll()`.
2. Access `author.getBooks()` inside the service.
3. Hibernate initializes the lazy `books` collection.

Why this is bad:

- The first query looks cheap because it only loads authors.
- The real cost appears later when lazy collections are accessed.
- Query count and collection fetch count can grow with the number of authors.

### 2. N+1: Books And Awards

```http
GET /demo/n-plus-one/book&award
```

Service method:

```text
AuthorService.demoNPlusOneBookAward()
```

Main JPQL:

```jpql
select a
from Author a
```

Backend flow:

1. Load authors.
2. Access `author.getBooks()`.
3. Access `author.getAwards()`.
4. Hibernate initializes two lazy collections.

Why this is worse than books only:

- Two lazy collections may need initialization.
- More entities and collections are hydrated.
- Heap, CPU, and GC pressure can increase.

### 3. JOIN FETCH To-One

```http
GET /demo/join-fetch/to-one
```

Repository method:

```text
AuthorRepository.findAllWithCountryJoinFetch()
```

JPQL:

```jpql
select a
from Author a
join fetch a.country
order by a.id
```

Expected SQL shape:

```sql
select a.*, c.*
from authors a
join countries c on c.id = a.country_id
order by a.id;
```

Why this is good:

- `Country` is to-one.
- One author still maps to one SQL row.
- It avoids lazy country queries without multiplying parent rows.

Backend artifact impact:

- Memory: usually stable.
- CPU: low extra cost.
- GC: usually low.
- SQL rows: approximately equal to author count.

### 4. JOIN FETCH One Collection

```http
GET /demo/join-fetch/books
```

Repository method:

```text
AuthorRepository.findAllWithBooksJoinFetch()
```

JPQL:

```jpql
select distinct a
from Author a
join fetch a.books
order by a.id
```

Expected SQL shape:

```sql
select distinct a.*, b.*
from authors a
join books b on b.author_id = a.id
order by a.id;
```

Why this is good, but size-sensitive:

- It removes N+1 for books.
- It uses one main query.
- But SQL rows become `authors x booksPerAuthor`.

Default dataset estimate:

```text
500 authors x 20 books = 10,000 joined rows
```

Backend artifact impact:

- Memory: grows with joined rows and hydrated books.
- CPU: grows with result-set processing and de-duplication.
- GC: can increase for larger datasets.
- SQL rows: much larger than author count.

### 5. Cartesian Product: Books And Awards

```http
GET /demo/join-fetch/cartesian
```

Repository method:

```text
AuthorRepository.findAllWithBooksAndAwardsJoinFetch()
```

JPQL:

```jpql
select distinct a
from Author a
join fetch a.books
join fetch a.awards
order by a.id
```

Expected SQL shape:

```sql
select distinct a.*, b.*, aw.*
from authors a
join books b on b.author_id = a.id
join awards aw on aw.author_id = a.id
order by a.id;
```

Why it is dangerous:

- `books` is to-many.
- `awards` is also to-many.
- SQL combines every book row with every award row for the same author.

Formula:

```text
estimated rows = authors x booksPerAuthor x awardsPerAuthor
```

Default dataset:

```text
500 authors x 20 books x 10 awards = 100,000 joined rows
```

Large dataset:

```text
5000 authors x 50 books x 20 awards = 5,000,000 joined rows
```

Why query count can be misleading:

- The main query count may be low.
- The amount of data returned by that one query can be huge.
- Hibernate still has to hydrate rows, resolve identity, assemble collections, and de-duplicate parent authors.

Backend artifact impact:

- Memory: can spike due to large result sets and hydrated object graphs.
- CPU: can spike due to row processing and de-duplication.
- GC: can increase because many temporary objects are created during hydration.
- Network/database transfer: can explode because SQL rows are multiplied.
- Response time: can become worse even with fewer SQL statements.

### 6. Pagination N+1 Baseline

```http
GET /demo/pagination/n-plus-one?page=0&size=10
```

Service method:

```text
AuthorService.demoPaginationNPlusOne(page, size)
```

Main query:

```jpql
select a
from Author a
```

Backend flow:

1. Page authors with `findAll(PageRequest.of(page, size))`.
2. Touch `author.getBooks()` for authors in the page.

Purpose:

This is a pagination baseline showing what happens when a paged result still triggers lazy collection access.

### 7. Pagination With To-One JOIN FETCH

```http
GET /demo/pagination/to-one?page=0&size=10
```

Repository method:

```text
AuthorRepository.findPageWithCountryJoinFetch(Pageable pageable)
```

JPQL:

```jpql
select a
from Author a
join fetch a.country
order by a.id
```

Why this is good:

- `Country` is to-one.
- Rows are not multiplied.
- SQL pagination remains correct.

### 8. Pagination With Collection JOIN FETCH

```http
GET /demo/pagination/books?page=0&size=10
```

Repository method:

```text
AuthorRepository.findPageWithBooksJoinFetch(Pageable pageable)
```

JPQL:

```jpql
select distinct a
from Author a
join fetch a.books
order by a.id
```

Why this is bad:

SQL pagination works on rows, but this query returns author-book rows. Hibernate may need to fetch all joined rows, de-duplicate authors, and paginate in memory.

Warning to look for:

```text
HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
```

Backend artifact impact:

- Memory: can grow with all joined rows, not page size.
- CPU: de-duplication and in-memory pagination cost.
- GC: can increase with larger result sets.
- Response time: can degrade sharply on large data.

### 9. Safe Pagination

```http
GET /demo/pagination/safe?page=0&size=10
```

Repository methods:

```text
AuthorRepository.findAuthorIds(Pageable pageable)
AuthorRepository.findAuthorsWithBooksByIds(List<Long> ids)
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
join fetch a.country
join fetch a.books
where a.id in :ids
order by a.id
```

Why this is safer:

- Pagination happens on author ids first.
- Collection fetch is limited to the selected page.
- The result set is bounded by `page size x books per author`.

## Response DTOs

### PerformanceResponse

Returned by:

- `/demo/n-plus-one/book`
- `/demo/n-plus-one/book&award`
- `/demo/join-fetch/to-one`
- `/demo/join-fetch/books`

Fields:

| Field | Meaning |
| --- | --- |
| `title` | Human-readable scenario name. |
| `scenarioType` | Category such as `GOOD`, `BAD`, or `GOOD WHEN RESULT SIZE IS CONTROLLED`. |
| `jpql` | Main JPQL used by the scenario. |
| `expectedSql` | Simplified SQL shape expected from Hibernate. |
| `executionTimeMs` | Wall-clock time measured by the service. Should match `benchmark.executionTimeMs`. |
| `totalAuthors` | Number of authors returned or involved in the scenario. |
| `totalBooksLoaded` | Number of books loaded/accessed by the service. |
| `totalAwardsLoaded` | Number of awards loaded/accessed by the service. |
| `generatedRowsEstimate` | Estimated or counted SQL row volume for the scenario. |
| `statistics` | Hibernate statistics snapshot. See `StatisticsResponse`. |
| `benchmark` | JVM/backend artifact metrics. See `BenchmarkResponse`. |
| `notes` | Educational explanation and recommendation. |
| `comparisonTimingsMs` | Optional timing map for scenarios that compare multiple approaches. Currently usually empty unless comparison code is active. |

### PaginationDemoResponse

Returned by:

- `/demo/pagination/n-plus-one`
- `/demo/pagination/to-one`
- `/demo/pagination/books`
- `/demo/pagination/safe`

Fields:

| Field | Meaning |
| --- | --- |
| `title` | Human-readable scenario name. |
| `jpql` | Main JPQL or multi-step JPQL description. |
| `expectedSql` | Simplified SQL behavior. |
| `page` | Requested page number. |
| `size` | Requested page size. |
| `recordsReturned` | Number of author records returned in the response scenario. |
| `executionTimeMs` | Wall-clock time measured by the service. |
| `idsQueryTimeMs` | Time spent fetching author ids in safe pagination. Null for single-step scenarios. |
| `fetchQueryTimeMs` | Time spent fetching associations in safe pagination. Null for single-step scenarios. |
| `totalAuthors` | Total authors according to the page/count result. |
| `totalBooksLoaded` | Books loaded/accessed in the scenario. |
| `totalAwardsLoaded` | Awards loaded/accessed in the scenario. |
| `generatedRowsEstimate` | Estimated or counted SQL row volume. |
| `statistics` | Hibernate statistics snapshot. |
| `benchmark` | Heap, GC, CPU, query, and hydration metrics. |
| `notes` | Educational explanation and recommendation. |

### CartesianProductDemoResponse

Returned by:

- `/demo/join-fetch/cartesian`

Fields:

| Field | Meaning |
| --- | --- |
| `title` | Scenario name. |
| `jpql` | JPQL with multiple collection fetch joins. |
| `expectedSql` | Simplified SQL join shape. |
| `executionTimeMs` | Wall-clock time measured by the service. |
| `totalAuthors` | Authors returned after Hibernate de-duplicates parent entities. |
| `totalBooksLoaded` | Books loaded/accessed. |
| `totalAwardsLoaded` | Awards loaded/accessed. |
| `generatedRowsEstimate` | Counted joined row volume: authors joined to books joined to awards. |
| `rowsPerAuthorEstimate` | `generatedRowsEstimate / totalAuthors`. |
| `statistics` | Hibernate statistics snapshot. |
| `benchmark` | Heap, GC, CPU, query, and hydration metrics. |
| `notes` | Explanation of why the scenario is dangerous. |

### StatisticsResponse

Nested inside main responses as `statistics`.

| Field | Meaning |
| --- | --- |
| `entityLoadCount` | Number of entities loaded by Hibernate in the measured session/statistics window. |
| `collectionFetchCount` | Number of collections fetched by Hibernate. This is especially useful for lazy collection access. |
| `queryExecutionCount` | Hibernate query execution count. This is not always the same as raw JDBC statement count. |
| `prepareStatementCount` | Number of prepared SQL statements. This is usually the closest value to SQL statement count. |

### BenchmarkResponse

Nested inside main responses as `benchmark`.

| Field | Meaning |
| --- | --- |
| `strategy` | Internal strategy name for the scenario, such as `JOIN_FETCH_BOOKS` or `JOIN_FETCH_CARTESIAN`. |
| `executionTimeMs` | Wall-clock time for the measured block. |
| `heapBeforeMB` | Used heap before the measured block. |
| `heapAfterMB` | Used heap after the measured block. |
| `heapDeltaMB` | `heapAfterMB - heapBeforeMB`. Positive means heap usage increased during the request. |
| `gcBefore` | Total JVM GC collection count before execution. |
| `gcAfter` | Total JVM GC collection count after execution. |
| `gcDelta` | `gcAfter - gcBefore`. If positive, GC happened during the request. |
| `cpuTimeMs` | Current thread CPU time consumed during the measured block, when supported by the JVM. |
| `queryCount` | Hibernate `prepareStatementCount`, used as a practical SQL statement count. |
| `entityLoadCount` | Hibernate entity load count. |
| `collectionLoadCount` | Hibernate collection load count. |
| `authorsReturned` | Number of author objects returned/involved in the scenario. |
| `estimatedCartesianRows` | Estimated row volume. For Cartesian scenarios this is `authors x books x awards`. |
| `jdbcRowCount` | Currently set to the same value as `estimatedCartesianRows` in `AuthorService` benchmark helpers. |

### Response Wrapper

`Response.java` exists as a generic wrapper:

```java
public record Response(
    StatisticsResponse statisticsResponse,
    PerformanceResponse performanceResponse,
    BenchmarkResponse benchmarkResponse
) {}
```

The active controller methods currently return specific DTOs directly, not this wrapper.

## Backend Artifact Comparison Tables

Use these tables after running the endpoints. Fill the measured values from the response JSON.
### Best vs Worst: General Comparison

| Scenario                                        | Endpoint                                          | Query Count | Estimated Rows | Heap Delta MB | GC Delta | CPU Time MS | Execution Time MS | Notes                                                                                                                   |
| ----------------------------------------------- | ------------------------------------------------- | ----------: | -------------: | ------------: | -------: | ----------: | ----------------: | ----------------------------------------------------------------------------------------------------------------------- |
| Pagination N+1: Authors then lazy Books         | `/demo/pagination/n-plus-one/book?page=0&size=10` |          12 |            210 |          6.01 |        0 |         140 |               202 | Loads 10 Authors, then executes 10 lazy Books queries. With the Page count query, the total is 12 statements.           |
| N+1: Books                                      | `/demo/n-plus-one/book`                           |         501 |         10,500 |         15.81 |        0 |         453 |               883 | Bad: one Author query followed by 500 lazy Books queries.                                                               |
| N+1: Books and Awards                           | `/demo/n-plus-one/book&award`                     |       1,001 |         15,500 |          6.59 |        1 |         812 |             1,430 | Worst query-count case: one Author query, 500 Books queries, and 500 Awards queries.                                    |
| To-one JOIN FETCH Country                       | `/demo/join-fetch/to-one`                         |           1 |            500 |          1.48 |        0 |          31 |                56 | Best full-list JOIN FETCH case: Country does not multiply the number of Author rows.                                    |
| One collection JOIN FETCH Books                 | `/demo/join-fetch/books`                          |           1 |         10,000 |         11.11 |        0 |         156 |               211 | Eliminates Books N+1 with one query, but the database still returns one joined row per Book.                            |
| Multiple collection JOIN FETCH Books and Awards | `/demo/join-fetch/cartesian`                      |           1 |        100,000 |         -6.30 |        3 |         500 |               764 | Worst row-volume case: 20 Books × 10 Awards creates 200 joined rows per Author.                                         |
| Pagination with to-one JOIN FETCH Country       | `/demo/pagination/to-one?page=0&size=10`          |           2 |             10 |          0.25 |        0 |          15 |                25 | Safe pagination: the content query applies limit/offset at the database, plus one Page count query.                     |
| Pagination with collection JOIN FETCH Books     | `/demo/pagination/books?page=0&size=10`           |           2 |         10,000 |         15.98 |        0 |         484 |               645 | Bad: only 10 Authors are returned, but Hibernate hydrates all 500 Authors and 10,000 Books before paginating in memory. |
| Safe two-step pagination                        | `/demo/pagination/safe?page=0&size=10`            |           3 |            210 |          0.84 |        0 |          31 |                31 | Recommended: paginate 10 Author IDs, then fetch only their 200 Books in a bounded association query.                    |

### Memory / GC Focus

| Endpoint                                          | Heap Before MB | Heap After MB | Heap Delta MB | GC Before | GC After | GC Delta | Interpretation                                                                                                                                                |
| ------------------------------------------------- | -------------: | ------------: | ------------: | --------: | -------: | -------: | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `/demo/pagination/n-plus-one/book?page=0&size=10` |          60.65 |         66.66 |          6.01 |        28 |       28 |        0 | Initializes 10 lazy Books collections and loads 200 Books. The request retains a moderate amount of heap.                                                     |
| `/demo/n-plus-one/book`                           |          67.16 |         82.97 |         15.81 |        28 |       28 |        0 | Loads 500 Authors and 10,000 Books through 500 secondary collection queries, producing substantial retained heap growth.                                      |
| `/demo/n-plus-one/book&award`                     |          42.50 |         49.10 |          6.59 |        37 |       38 |        1 | Loads 15,500 entities and initializes 1,000 collections. GC occurred during execution, so heap delta alone understates total allocation.                      |
| `/demo/join-fetch/to-one`                         |          62.47 |         63.95 |          1.48 |        29 |       29 |        0 | Stable memory usage because the query returns one joined row per Author and loads only 50 unique Country entities.                                            |
| `/demo/join-fetch/books`                          |          64.18 |         75.29 |         11.11 |        29 |       29 |        0 | Moderate heap growth while hydrating 500 Authors, 10,000 Books, and 500 initialized Books collections.                                                        |
| `/demo/join-fetch/cartesian`                      |          75.39 |         69.09 |         -6.30 |        29 |       32 |        3 | High temporary allocation pressure caused by 100,000 joined rows triggered three GC events. The negative delta does not mean the request allocated no memory. |
| `/demo/pagination/to-one?page=0&size=10`          |          69.24 |         69.49 |          0.25 |        32 |       32 |        0 | Very stable memory usage because only 10 Authors and 10 Country entities are loaded.                                                                          |
| `/demo/pagination/books?page=0&size=10`           |         104.69 |        120.67 |         15.98 |        27 |       27 |        0 | Although the API returns only 10 Authors, Hibernate hydrates all 500 Authors and 10,000 Books before applying pagination in memory.                           |
| `/demo/pagination/safe?page=0&size=10`            |          69.57 |         70.41 |          0.84 |        32 |       32 |        0 | Bounded memory usage because only 10 Authors, their Countries, and 200 Books are hydrated.                                                                    |

### CPU / Time Focus

| Endpoint                                          | CPU Time MS | Execution Time MS | Entity Load Count | Collection Load Count | Query Count | Interpretation                                                                                                            |
| ------------------------------------------------- | ----------: | ----------------: | ----------------: | --------------------: | ----------: | ------------------------------------------------------------------------------------------------------------------------- |
| `/demo/pagination/n-plus-one/book?page=0&size=10` |         140 |               202 |               210 |                    10 |          12 | One page query, one count query, and 10 lazy Books queries load 10 Authors and 200 Books.                                 |
| `/demo/n-plus-one/book`                           |         453 |               883 |            10,500 |                   500 |         501 | High database round-trip cost: every Author initializes its Books collection with a separate SQL statement.               |
| `/demo/n-plus-one/book&award`                     |         812 |             1,430 |            15,500 |                 1,000 |       1,001 | Highest statement count and longest execution time: every Author initializes both Books and Awards independently.         |
| `/demo/join-fetch/to-one`                         |          31 |                56 |               550 |                     0 |           1 | Most efficient full-list scenario: one query loads 500 Authors and 50 unique Countries without collection multiplication. |
| `/demo/join-fetch/books`                          |         156 |               211 |            10,500 |                   500 |           1 | Replaces 500 lazy queries with one query, but Hibernate must still process 10,000 joined rows.                            |
| `/demo/join-fetch/cartesian`                      |         500 |               764 |            15,500 |                 1,000 |           1 | Only one query is executed, but Hibernate processes 100,000 SQL rows and initializes two collections per Author.          |
| `/demo/pagination/to-one?page=0&size=10`          |          15 |                25 |                20 |                     0 |           2 | Fast and predictable: 10 Authors and 10 Countries are loaded using a paginated content query and count query.             |
| `/demo/pagination/books?page=0&size=10`           |         484 |               645 |            10,500 |                   500 |           2 | Inefficient pagination: 10 Authors are returned, but 500 Authors and 10,000 Books are loaded before in-memory pagination. |
| `/demo/pagination/safe?page=0&size=10`            |          31 |                31 |               220 |                    10 |           3 | Executes one more statement than unsafe pagination, but loads only 10 Authors, 10 Countries, and 200 Books.               |

### Main Comparison

| Comparison                      | Bad Case                                                         | Better Case                                 | Main Evidence                                                                                             |
| ------------------------------- | ---------------------------------------------------------------- | ------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| Loading Authors with Books      | N+1 Books: 501 queries, 883 ms                                   | JOIN FETCH Books: 1 query, 211 ms           | JOIN FETCH avoids 500 secondary database round trips.                                                     |
| Loading Books and Awards        | N+1 Books and Awards: 1,001 queries, 1,430 ms                    | Cartesian JOIN FETCH: 1 query, 764 ms       | Cartesian avoids round trips but still suffers from 100,000 joined rows. Neither is ideal for large data. |
| Paginating Authors with Books   | Collection JOIN FETCH pagination: 10,500 entities loaded, 645 ms | Safe pagination: 220 entities loaded, 31 ms | Safe pagination loads only data belonging to the requested page.                                          |
| Paginating Authors with Country | To-one JOIN FETCH pagination: 2 queries, 25 ms                   | —                                           | Safe because a to-one join does not duplicate parent rows.                                                |
| Paginated lazy loading          | Pagination N+1: 12 queries, 202 ms                               | Safe pagination: 3 queries, 31 ms           | The page limits N+1 to 10 Authors, but still causes one lazy query per Author.                            |

### Row Explosion Focus

| Scenario                  | Authors | Books per Author | Awards per Author | Estimated Rows | Why It Matters                           |
| ------------------------- | ------: | ---------------: | ----------------: | -------------: | ---------------------------------------- |
| To-one fetch              |     500 |                — |                 — |            500 | Rows stay close to author count.         |
| One collection fetch      |     500 |               20 |                 — |         10,000 | Rows grow as `authors x books`.          |
| Multiple collection fetch |     500 |               20 |                10 |        100,000 | Rows grow as `authors x books x awards`. |


## How To Read The Results

A lower `queryCount` is not automatically better. Compare it together with:

- `estimatedCartesianRows`
- `heapDeltaMB`
- `gcDelta`
- `cpuTimeMs`
- `executionTimeMs`
- `entityLoadCount`
- `collectionLoadCount`

The most important lesson in this module:

```text
One SQL query can still be slow if that query returns a huge joined result set.
```

For `JOIN FETCH` with multiple collections, the backend may do more work even though query count looks better. That extra work appears as memory growth, GC activity, CPU time, entity hydration, collection loading, and slower response time.
