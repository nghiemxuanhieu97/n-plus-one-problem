# Module 01: JOIN FETCH

This section demonstrates common JOIN FETCH scenarios: why we need it, when it is useful, when it helps avoid extra queries, and when it can become risky because of row explosion, Cartesian products, or incorrect assumptions about how it works.
## What This Module Demonstrates
- Simple and safe author loading without loading associations information.
- N+1 query behavior with lazy loading.
- `JOIN FETCH` for a to-one association.
- `JOIN FETCH` for one collection.
- `JOIN FETCH` with multiple-collection.
- Pagination with `JOIN FETCH`

## Domain Model

```text
Country (1) ------ (*) Author
Author  (1) ------ (*) Book
Author  (1) ------ (*) Award
```
## Seed Data

`DataInitializer` creates data at startup.

Default dataset:

```text
50 countries
500 authors
20 books per author
10 awards per author
```

## Run

From repository root:

```bash
./gradlew :01-join-fetch:bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat :01-join-fetch:bootRun
```


OpenAPI UI:

```text
http://localhost:8080/swagger-ui/index.html
```
## Response Metrics

All benchmark APIs return `com.example.joinfetch.dto.record.Response<T>`:

```java
public record Response<T>(
        long ormQueryExecutionCount,
        List<String> ormQueries,
        long sqlStatementCount,
        long preparedStatementCount,
        List<String> sqlStatements,
        long estimatedDatabaseRows,
        double executionTimeMs,
        T result
) {}
```

Metric notes:

- `ormQueryExecutionCount`:  How many ORM queries Hibernate thinks it executed.
- `ormQueries`:  A preview of the ORM queries tracked by Hibernate.
- `sqlStatementCount`: number of SQL statements collected by P6Spy for this benchmark request.
- `preparedStatementCount`: `Hibernate Statistics#getPrepareStatementCount()`.
- `sqlStatements`: preview of collected SQL statements, **limited to the first 20 statements to keep the JSON readable.**
- `estimatedDatabaseRows`: scenario-specific estimate, not a JDBC `ResultSet` row counter.
- `executionTimeMs`: wall-clock time measured by `BenchmarkAspect`.
- `result`: scenario-specific DTO data preview. **(top 5 DTOs)**



Current relationship shape:

| Entity field | Mapping |
| --- | --- |
| `Author.country` | `@ManyToOne(fetch = FetchType.LAZY)` |
| `Author.books` | `@OneToMany(fetch = FetchType.LAZY)` |
| `Author.awards` | `@OneToMany(fetch = FetchType.LAZY)`|
| `Book.author` | `@ManyToOne(fetch = FetchType.LAZY)` |
| `Award.author` | `@ManyToOne(fetch = FetchType.LAZY)` |

## Why Associations Are Usually LAZY & Why it causes N+1 Problems.
### Scenario 01: Load Authors Only

On a book search page, the UI needs an Author dropdown. The dropdown only needs two fields:

| Field | Purpose |
| --- | --- |
| `author.id` | Used as the filter value sent to the backend |
| `author.name` | Shown as the label in the dropdown |
![img.png](img.png)

#### Demo endpoint: `GET /demos/authors`

For this use case, the UI does not need the Author's books, awards, or country information.

Loading those associations would be unnecessary because the backend would need to load, map, and return data that the screen does not use.

That is why associations such as `Author.books`, `Author.awards`, and `Author.country` are often configured as `FetchType.LAZY`.

With LAZY loading, a simple repository method such as `findAll()` can load only the Author rows first. Related data is loaded later only when the application actually accesses it.


| Metric | Value | Why it matters for later scenarios                                                           |
| --- | ---: |----------------------------------------------------------------------------------------------|
| `sqlStatementCount` | `1` | This is the clean baseline. Later N+1 cases will grow from `1` to hundreds of SQL statements. |
| `preparedStatementCount` | `1` | Used as a second signal that only one JDBC statement was prepared/executed.                  |
| `estimatedDatabaseRows` | `500` | Expected. Correct amount of authors in the database                                          |


### Scenario 02 - Problem: Lazy Books Cause N+1


An administration page displays a list of authors together with all books written by each author.

![img_1.png](img_1.png)

The response needs data like this:

```text
[
  {
    "author": "Author 1",
    "books": ["Book 1", "Book 2", "Book 3"]
  },
  {
    "author": "Author 2",
    "books": ["Book 4", "Book 5", "Book 6"]
  }
]
  ```

A naive implementation may first load all authors:

  ```

List<Author> authors = authorRepository.findAll();

  ```

Then, it accesses the lazy books collection: **author.getBooks()**


Because Author.books is configured as FetchType.LAZY, Hibernate does not load books together with authors at first.

```select * from books b where b.author_id = ?```


So the SQL flow become:


+ 1 query to load all authors

+ N additional queries to load books for N authors

  This can produce approximately:

  ```1 + 500 = 501 SQL statements```


This is the N+1 query problem.

| Metric                   |         Value | Meaning                                                                               |
| --- | --- | --- |
| `ormQueryExecutionCount` |           `1` | The application only runs one main ORM query: load Authors.                           |
| `sqlStatementCount`      |         `501` | The real SQL work is much higher: 1 query for Authors + 500 lazy queries for Books.   |
| `preparedStatementCount` |         `501` | Confirms that many SQL statements were prepared/executed at JDBC level.               |
| `estimatedDatabaseRows`  |      `10,500` | 500 Author rows + 10,000 Book rows.                                                   |



### Scenario 03 - Problem: Lazy Country Causes N+1
Now the page needs to display authors together with their country information.

For example, an administration page may show an author list like this:

| Author | Country | Region |
| --- | --- | --- |
| Author 1 | Country 1 | Continent Group 1 |
| Author 2 | Country 2 | Continent Group 2 |
| Author 3 | Country 3 | Continent Group 3 |
The response needs data like this:
```text
[
  {
    "id": 1,
    "name": "Author 1",
    "country": {
      "id": 1,
      "name": "Country 1",
      "location": "Region 1",
      "region": "Continent Group 1"
    }
  },
  {
    "id": 2,
    "name": "Author 2",
    "country": {
      "id": 2,
      "name": "Country 2",
      "location": "Region 2",
      "region": "Continent Group 2"
    }
  }
]

  ```
A naive implementation may first load all authors:
  ```
List<Author> authors = authorRepository.findAll();
  ```
Then, it accesses the lazy country  collection: **author.getCountry()**

Then The SQL would be:
```
select c1_0.id,c1_0.location,c1_0.name,c1_0.region from countries c1_0 where c1_0.id=?
```
> The 500 Authors reference only 50 distinct Countries. Hibernate loads each
> Country once and reuses it from the current Persistence Context. Therefore,
> the request executes 1 Author query and 50 Country queries.
So the SQL flow becomes:

1 query to load all authors

N additional queries to load country for N authors

This can produce approximately:
```
1 + 50 = 51 SQL statements
```

#### Demo endpoint: GET /demos/authors/n-plus-one/country

| Metric | Value | Meaning |
| --- | --- | --- |
| ``ormQueryExecutionCount`` | 1 | The application only runs one main ORM query: load Authors. |
| ``sqlStatementCount`` | 51 | The real SQL work is much higher: 1 query for Authors + 50 for Country. |
| ``preparedStatementCount`` | 51 | Confirms that many SQL statements were prepared/executed at JDBC level. |
| ``estimatedDatabaseRows`` | 500 | Expected number of Author rows in the database. |


## Apply JOIN FETCH
### Scenario 04 - Safe Case: JOIN FETCH Country

Instead of loading Authors first and allowing Hibernate to load Countries later, the repository explicitly fetches both associations:

```java
select a
from Author a
join fetch a.country
order by a.id
```

Hibernate translates this into one SQL statement that joins the `authors` and `countries` tables:

```sql
select
    a.id,
    a.name,
    a.country_id,
    c.id,
    c.name,
    c.location,
    c.region
from authors a
join countries c on c.id = a.country_id
order by a.id
```
| author_id | author_name | country_id | country_name |
|---:|---|---:|---|
| 1 | Author 1 | 1 | Country 1 |
| 2 | Author 2 | 1 | Country 1 |
| 3 | Author 3 | 2 | Country 2 |

The SQL flow now becomes:

- 1 SQL statement loads Authors and Countries together.
- No additional lazy Country queries are required while mapping the response.

A to-one `JOIN FETCH` is generally safe because each Author references at most one Country. Joining Country does not multiply one Author into multiple result rows.

#### Demo endpoint: `GET /demos/authors/join-fetch/country`

| Metric | Value | Meaning |
| --- | ---: | --- |
| `ormQueryExecutionCount` | `1` | One ORM query explicitly fetches Authors and Countries. |
| `sqlStatementCount` | `1` | Hibernate executes one joined SQL statement. |
| `preparedStatementCount` | `1` | Only one SQL statement is prepared at the JDBC level. |
| `estimatedDatabaseRows` | `500` | The join returns approximately one row for each Author. |

### Scenario 05 - Trade-off: JOIN FETCH Books

```java
select distinct a
from Author a
join fetch a.books
order by a.id
```

Hibernate translates this into one SQL statement:

```sql
select distinct
    a.id,
    a.country_id,
    a.name,
    b.author_id,
    b.book_order,
    b.id,
    b.publish_year,
    b.title
from authors a
join books b on a.id = b.author_id
order by a.id
```

The SQL flow is reduced from:

```text
1 query to load Authors
500 queries to load their Books
```

to:

```text
1 query to load Authors and Books together
```

This solves the N+1 query problem:

```text
501 SQL statements → 1 SQL statement
```
However, fetching a collection is different from fetching a to-one association such as `Author.country`.
Each Author can have multiple Books, so the database must return one joined row for every Author–Book pair:


| author_id | author_name | book_id | book_title |
| --------: | ----------- | ------: | ---------- |
|         1 | Author 1    |       1 | Book 1     |
|         1 | Author 1    |       2 | Book 2     |
|         1 | Author 1    |       3 | Book 3     |
|         2 | Author 2    |       4 | Book 4     |
|         2 | Author 2    |       5 | Book 5     |
|         2 | Author 2    |       6 | Book 6     |
|         3 | Author 3    |       7 | Book 7     |
|         3 | Author 3    |       8 | Book 8     |
|         3 | Author 3    |       9 | Book 9     |

With 500 Authors and 20 Books per Author, the joined result contains:

```text
500 Authors × 20 Books = 10,000 joined rows
```

#### Demo endpoint: `GET /demos/authors/join-fetch/book`

| Metric | Value | Meaning |
| --- | ---: | --- |
| `ormQueryExecutionCount` | `1` | One ORM query requests Authors together with their Books. |
| `sqlStatementCount` | `1` | Hibernate executes one joined SQL statement instead of 501 separate statements. |
| `preparedStatementCount` | `1` | Only one SQL statement is prepared at the JDBC level. |
| `estimatedDatabaseRows` | `10,000` | The database returns one joined row for each Author–Book pair. |
| `executionTimeMs` | `381.6906` | Time measured for this benchmark request. It can change between runs and environments. |

`JOIN FETCH` removes the large number of database round trips, but it replaces them with one larger result set.

| Approach           | SQL statements | Estimated rows | Main cost                          | executionTimeMs |
|--------------------| ---: |---------------:|------------------------------------|-----------------|
| Lazy Books         | `501` |       `10,500` | Many database round trips          | `1274.3404`     |
| JOIN FETCH Books   | `1` |       `10,000` | One large joined result            | `381.6906`      |
| JOIN FETCH Country | `1` |          `500` | One small joined result |  `28.2585`      |


### Scenario 06 - Failure Case: Fetching Two List Bags

In this scenario, `Author.reviews` and `Author.awards` are both mapped as plain `List` collections:
```java
@OneToMany(
    mappedBy = "author",
    cascade = CascadeType.ALL,
    orphanRemoval = true,
    fetch = FetchType.LAZY
)
@Builder.Default
private List<Review> reviews = new ArrayList<>();

@OneToMany(
    mappedBy = "author",
    cascade = CascadeType.ALL,
    orphanRemoval = true,
    fetch = FetchType.LAZY
)
@Builder.Default
private List<Award> awards = new ArrayList<>();
```
Hibernate therefore treats both collections as bags. [Read Bag definition](MultipleBagFetchException.md)

| Author | Review | Award |
| --- | --- | --- |
| Author 1 | Review 1 | Award 1 |
| Author 1 | Review 1 | Award 2 |
| Author 1 | Review 1 | Award 3 |
| Author 1 | Review 2 | Award 1 |
| Author 1 | Review 2 | Award 2 |
| Author 1 | Review 2 | Award 3 |
The same Review appears once for every Award, and the same Award appears once for every Review.
Some of these repeated values were introduced by the SQL join. However, because a bag can also contain real duplicates and has no index, Hibernate cannot reliably distinguish:
```text
A real duplicate stored in the collection
```

from:

```text
A duplicate produced by the join
```
Hibernate therefore refuses to execute the query instead of building collections that may contain incorrect data.
The root cause is:

```text
org.hibernate.loader.MultipleBagFetchException:
cannot simultaneously fetch multiple bags
```
### Scenario 07 - Risky Case: Multiple Collection JOIN FETCH Causes Row Explosion

In this scenario, we fix the 2 bags fetch by configured `books` is an indexed `List` because it uses `@OrderColumn`. 

The repository now fetches both collections:

```java
select distinct a
from Author a
join fetch a.books
join fetch a.awards
order by a.id
```

Hibernate generates one SQL statement that joins all three tables:

```sql
select distinct
    a.id,
    a.name,
    b.id,
    b.title,
    b.publish_year,
    aw.id,
    aw.name
from authors a
join books b on a.id = b.author_id
join awards aw on a.id = aw.author_id
order by a.id
```

| author_id | author_name | book_id | book_title | award_id | award_name |
| --------: | ----------- | ------: | ---------- | -------: | ---------- |
|         1 | Author 1    |       1 | Book 1     |        1 | Award 1    |
|         1 | Author 1    |       1 | Book 1     |        2 | Award 2    |
|         1 | Author 1    |       1 | Book 1     |        3 | Award 3    |
|         1 | Author 1    |       2 | Book 2     |        1 | Award 1    |
|         1 | Author 1    |       2 | Book 2     |        2 | Award 2    |
|         1 | Author 1    |       2 | Book 2     |        3 | Award 3    |

The query executes successfully, but it creates a much larger SQL result than the final Java response suggests.
```text
500 Authors × 20 Books × 10 Awards
= 100,000 joined rows
```
Hibernate reads all 100,000 SQL rows and then rebuilds them into approximately:
```text
500 unique Author entities
10,000 unique Book entities
5,000 unique Award entities
```
#### Demo endpoint: `GET /demo/author/join-fetch/book-and-award`

| Metric | Value | Meaning |
| --- | ---: | --- |
| `ormQueryExecutionCount` | `1` | One ORM query requests Authors, Books, and Awards. |
| `sqlStatementCount` | `1` | Hibernate executes one joined SQL statement. |
| `preparedStatementCount` | `1` | Only one statement is prepared at the JDBC level. |
| `estimatedDatabaseRows` | `100,000` | Every Book is combined with every Award belonging to the same Author. |
| `executionTimeMs` | `1567.3461` | Wall-clock time measured for this benchmark request. |


| Approach | SQL statements | Estimated database rows | Measured execution time |
| --- | ---: | ---: | ---: |
| N+1 for Books and Awards | `1,001` | `15,500` | `842.7453 ms` |
| JOIN FETCH Books and Awards | `1` | `100,000` | `1567.3461 ms` |


## JOIN FETCH With Pagination

### Scenario 08 - Safe Pagination: JOIN FETCH Country
Spring Data applies pagination to this query and executes SQL similar to:

```sql
select
    a.id,
    a.name,
    c.id,
    c.name,
    c.location,
    c.region
from authors a
join countries c on c.id = a.country_id
order by a.id
offset 0 rows
fetch first 10 rows only
```
The database applies the pagination directly and returns only the first 10 Authors together with their Countries.
- Country is loaded in the same data query.
- The to-one join does not multiply Author rows.

| Metric | Value | Meaning |
| --- | ---: | --- |
| `ormQueryExecutionCount` | `2` | One ORM query loads the page and one query counts all Authors. |
| `sqlStatementCount` | `2` | Hibernate executes one paginated join query and one count query. |
| `preparedStatementCount` | `2` | Two SQL statements are prepared at the JDBC level. |
| `estimatedDatabaseRows` | `10` | The data query returns approximately one row for each Author in the page. |
| `executionTimeMs` | `27.943` | Wall-clock time measured for this benchmark request. |

Pagination with a to-one `JOIN FETCH` is a safe and recommended pattern.

### Scenario 09 - Risky Pagination: JOIN FETCH Books
For this demonstration, the property is disabled:
```properties
hibernate.query.fail_on_pagination_over_collection_fetch=false
```

Then the repository attempts to fetch Authors and their Books using:
```java
select distinct a
from Author a
join fetch a.books
order by a.id
```

Hibernate generates the following data query:

```sql
select distinct
    a.id,
    a.country_id,
    a.name,
    b.author_id,
    b.book_order,
    b.id,
    b.publish_year,
    b.title
from authors a
join books b on a.id = b.author_id
order by a.id
```

this SQL does not contain:

```sql
offset 0 rows
fetch first 10 rows only
```

| Metric | Value | Meaning                                                                                       |
| --- | ---: |-----------------------------------------------------------------------------------------------|
| `ormQueryExecutionCount` | `2` | One ORM query loads Authors with Books and one query counts the Authors.                      |
| `sqlStatementCount` | `2` | The query count is small, but the main data query loads the complete joined result.           |
| `preparedStatementCount` | `2` | Two SQL statements are prepared at the JDBC level.                                            |
| `estimatedDatabaseRows` | `10,000` | The database returns all 500 × 20 Author–Book combinations. instead of 200 rows of 10 Authors |
| `executionTimeMs` | `444.59920` | Wall-clock time measured for this benchmark request.                                          |

The database therefore does not return only the 10 Authors requested by the page. Instead, it returns the complete Author–Book joined result.
With 500 Authors and 20 Books per Author:

```text
500 Authors × 20 Books
= 10,000 joined rows
```
Hibernate reports this behavior with the following warning:

```text
HHH90003004:
firstResult/maxResults specified with collection fetch;
applying in memory
``` 
This warning means that Hibernate applies pagination to the root Author result in JVM memory after loading the complete joined result.

#### Why SQL pagination cannot be applied safely
A collection join creates multiple rows for each Author:

| author_id | author_name | book_id | book_title |
| --------: | ----------- | ------: | ---------- |
|         1 | Author 1    |       1 | Book 1     |
|         1 | Author 1    |       2 | Book 2     |
|         1 | Author 1    |       3 | Book 3     |
|         2 | Author 2    |       4 | Book 4     |
|         2 | Author 2    |       5 | Book 5     |
|         2 | Author 2    |       6 | Book 6     |
|         3 | Author 3    |       7 | Book 7     |
|         3 | Author 3    |       8 | Book 8     |
|         3 | Author 3    |       9 | Book 9     |
If the database limited the result to 10 joined rows, it might return only one Author with 10 of their 20 Books:
```text
10 joined rows ≠ 10 complete Authors
```

To preserve complete collections, Hibernate performs the following process:
```text
Database returns all joined rows
→ Hibernate rebuilds Authors and Books
→ Hibernate removes duplicate Authors
→ Hibernate selects 10 Authors in JVM memory
```

The final page contains only 10 Authors, but Hibernate loads and processes all 500 Authors and 10,000 Books before applying pagination in JVM memory.


### Scenario 10 - Failure: Manual Pagination Over Joined Rows
```sql
select a.*
from authors a
join books b on b.author_id = a.id
order by a.id
limit :limit offset :offset
```

#### Demo endpoint

```text
GET /demo/author/self-defined/pagination/book?page=0&size=10&queryType=SQL
```
The response preview already demonstrates the incorrect result:

```json
[
  {
    "id": 1,
    "name": "Author 1",
    "books": ["20 Books"]
  },
  {
    "id": 1,
    "name": "Author 1",
    "books": ["20 Books"]
  },
  {
    "id": 1,
    "name": "Author 1",
    "books": ["20 Books"]
  },
  {
    "id": 1,
    "name": "Author 1",
    "books": ["20 Books"]
  },
  {
    "id": 1,
    "name": "Author 1",
    "books": ["20 Books"]
  }
]
```
The query technically returns 10 rows, but those rows do not represent 10 Authors.
The response itself proves that manual `LIMIT` and `OFFSET` do not solve collection pagination:

```text
Expected IDs: [1, 2, 3, ..., 10]
Actual IDs:   [1, 1, 1, ..., 1]
```

The query limits SQL rows, not root Authors.

```text
LIMIT 10 joined rows ≠ 10 Authors
Complete Books collection ≠ Correct Author page
Database pagination ≠ Correct root pagination
```

# Conclusion:
| Fetch shape | Conclusion |
| --- | --- |
| To-one | **Usually safe and recommended** |
| One to-many collection | **Size-sensitive — benchmark before using** |
| Multiple to-many collections | **Cartesian product risk — prefer separate queries** |
| To-one with pagination | **Usually safe** |
| To-many with pagination | **Use two-step pagination** |