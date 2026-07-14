# Module 03: DTO Projection

This module researches Spring Data JPA DTO projection as an alternative to loading full entities. It now implements the same read scenarios in three styles:

- interface projection: Spring creates proxy objects from entity properties or query aliases
- class projection: JPQL constructor expressions create regular DTO classes
- record projection: JPQL constructor expressions create immutable Java records

The goal is to compare how each style behaves for scalar fields, to-one relationships, aggregate values, latest-child queries, computed display values, pagination, and to-many relationships.

## Endpoint Groups

| Projection style | Non-paged endpoints | Paged endpoints | DTO package |
| --- | --- | --- | --- |
| Interface | `/interface-projections/authors/...` | `/interface-projections/authors/page/...` | `dto.interfaceprojection` |
| Class | `/class-projections/authors/...` | `/class-projections/authors/page/...` | `dto.classprojection` |
| Record | `/record-projections/authors/...` | `/record-projections/authors/page/...` | `dto.recordprojection` |

Each style has its own controller, service, and repository so the query strategy stays visible instead of being hidden behind one mixed abstraction.

## Scenario Matrix

| Scenario | Interface endpoint | Class endpoint | Record endpoint | Notes |
| --- | --- | --- | --- | --- |
| Names only | `/names-only` | `/names-only` | `/names-only` | Simple read model with `id` and `name`. Interface can use derived projection; class/record use constructor queries. |
| Author with country | `/with-country` | `/with-country` | `/with-country` | Tests a to-one relationship. All three work cleanly because each author has at most one country summary. |
| Book and award counts | `/book-award-counts` | `/book-award-counts` | `/book-award-counts` | Tests aggregate projection with `count(distinct ...)`. Interface uses aliases; class/record use constructor expressions. |
| Most recent book | `/recent-books` | `/recent-books` | `/recent-books` | Uses a subquery with `max(book.id)` to pick one book title per author. Authors without books are not returned. |
| Author books | `/with-books` | `/book-rows` | `/book-rows` | Interface keeps the risky nested collection case for research. Class/record use flat rows, which is the safer DTO projection shape. |
| Display name | `/open-display-name?type=valid` | `/display-name` | `/display-name` | Interface uses open projection with SpEL. Class/record compute the value in JPQL and pass it into the DTO constructor. |
| Broken display name | `/open-display-name?type=broken` | Not implemented | Not implemented | The broken case is interface-specific. Reproducing it in JPQL would make query validation fail and can prevent startup. |

## Interface Projection Cases

Interface projection is concise because Spring Data maps getters to entity properties or query aliases.

| Case | Projection | How it works | Blocked or risky behavior |
| --- | --- | --- | --- |
| Names only | `AuthorNameOnlyProjection` | Derived query returns a proxy with `getId()` and `getName()`. | None. This is the cleanest interface projection shape. |
| With country | `AuthorWithCountryProjection` + `CountrySummaryProjection` | Spring maps the to-one association into another projection interface. | Safe for to-one data. |
| Counts | `AuthorBookAwardCountProjection` | JPQL aliases `bookCount` and `awardCount` match the projection getter names. | Alias mismatch would return null or fail mapping. |
| Recent book | `AuthorRecentBookProjection` | JPQL returns one scalar `recentBookName` per author. | Authors without books are filtered out by the subquery condition. |
| With books | `AuthorWithBooksProjection` + `BookSummaryProjection` | Attempts a nested to-many projection. | Risky. With `open-in-view: false`, JSON serialization can touch a lazy collection after the transaction closes and fail. It can also reintroduce N+1 behavior. |
| Open display name | `AuthorDisplayNameOpenProjection` | SpEL evaluates `target.name + ' ' + target.lastname`. | Works, but `lastname` is null in seeded data, so the display value can include an empty or null-looking suffix depending on expression. |
| Broken open display name | `AuthorBrokenDisplayNameOpenProjection` | SpEL tries `target.firstname`. | Intentionally fails at runtime because `Author` has no `firstname` property. |

## Class Projection Cases

Class projection uses DTO constructors and explicit `select new ...(...)` JPQL. This is more verbose than interface projection, but the selected columns are obvious and the DTOs are normal Java objects.

| Case | DTO | How it works |
| --- | --- | --- |
| Names only | `AuthorNameOnlyDto` | Constructor query selects `a.id` and `a.name`. |
| With country | `AuthorWithCountryDto` | Query selects country scalar fields; the DTO constructor builds `CountrySummaryDto`. |
| Counts | `AuthorBookAwardCountDto` | Query groups by author/country fields and passes count values into the constructor. |
| Recent book | `AuthorRecentBookDto` | Query selects the latest book title as a scalar constructor argument. |
| Book rows | `AuthorBookRowDto` | Query returns one row per author-book pair: `(authorId, authorName, bookId, bookTitle, publishYear)`. This avoids lazy nested collections. |
| Display name | `AuthorDisplayNameDto` | Query computes `concat(a.name, ' ', coalesce(a.lastname, ''))` and passes it as `displayName`. |

Paged class endpoints exist for `/with-country`, `/book-award-counts`, `/recent-books`, and `/book-rows`. Aggregate and row queries include explicit `countQuery` definitions for reliable pagination.

## Record Projection Cases

Record projection uses the same JPQL constructor pattern as class projection, but DTOs are immutable records with less boilerplate.

| Case | Record | How it works |
| --- | --- | --- |
| Names only | `AuthorNameOnlyRecord` | Constructor query selects `a.id` and `a.name`. |
| With country | `AuthorWithCountryRecord` | Additional constructor accepts flat country fields and creates `CountrySummaryRecord`. |
| Counts | `AuthorBookAwardCountRecord` | Additional constructor accepts flat country fields and aggregate counts. |
| Recent book | `AuthorRecentBookRecord` | Additional constructor accepts flat country fields and latest book title. |
| Book rows | `AuthorBookRowRecord` | One immutable record per author-book row. |
| Display name | `AuthorDisplayNameRecord` | Query-computed display name passed to the record constructor. |

Paged record endpoints mirror the class projection endpoints: `/with-country`, `/book-award-counts`, `/recent-books`, and `/book-rows`.

## Why The To-Many Shape Changes

Interface projection includes `/with-books` to demonstrate the tempting nested collection shape. It is kept as a research case because it can fail or reintroduce N+1 queries.

Class and record projections use `/book-rows` instead. The flat row shape is usually the better DTO projection for one-to-many data because every SQL row maps to exactly one DTO. If an API needs nested JSON later, group the rows in service code after the query has already returned all required columns.

## Why The Broken Open Projection Is Interface-Only

Open projection is a Spring Data interface feature. The broken interface case is useful because it shows a runtime SpEL failure: `target.firstname` does not exist on `Author`.

Class and record projections should not copy that broken case in JPQL. A query like `a.firstname` is validated as a JPQL path and can break application startup instead of returning a controlled endpoint error.

## Run Demo

```bash
./gradlew :03-dto-projection:bootRun
```

This project requires Java 17 or newer because the root Gradle build uses Spring Boot 3.x.

## When To Use Each Style

| Style | Best use | Tradeoff |
| --- | --- | --- |
| Interface | Fast read models, aliases, and simple nested to-one summaries | Runtime proxy behavior can hide costs, especially with to-many relationships and open projections |
| Class | Clear constructor-based DTOs with custom constructor logic | More boilerplate than records |
| Record | Immutable read DTOs with minimal boilerplate | Constructor signatures must stay aligned with JPQL constructor expressions |
