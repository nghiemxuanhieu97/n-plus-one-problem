# Module 02: @EntityGraph

## Kỹ thuật
`@EntityGraph` — JPA chuẩn để chỉ định eager-loading attributes mà không cần viết JPQL.

## Key Code

```java
// Dynamic EntityGraph (ad-hoc):
@EntityGraph(attributePaths = {"books"})
List<Author> findAllWithDynamicGraph();

// Named EntityGraph (defined on entity):
// Author.java:
@NamedEntityGraph(name = "Author.withBooks",
                  attributeNodes = @NamedAttributeNode("books"))
// Repository:
@EntityGraph("Author.withBooks")
List<Author> findAllWithNamedGraph();
```

## Kết quả

| Scenario | SQL queries |
|----------|------------|
| `findAll()` + lazy `getBooks()` | **6** (1 + 5) |
| `findAllWithDynamicGraph()` | **1** |
| `findAllWithNamedGraph()` | **1** |

## Chạy demo
```bash
./gradlew :02-entity-graph:bootRun
```

## So sánh với JOIN FETCH
- EntityGraph sinh ra SQL tương tự JOIN FETCH
- Ưu điểm: có thể áp dụng cho derived query methods (`findByName(...)`)
- NamedEntityGraph có thể tái sử dụng nhiều nơi
