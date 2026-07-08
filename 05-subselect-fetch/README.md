# Module 05: @Fetch(FetchMode.SUBSELECT)

## Kỹ thuật
`@Fetch(FetchMode.SUBSELECT)` — khi bất kỳ lazy collection nào cần được load,
Hibernate dùng subquery để load collections cho **tất cả entities trong session** cùng lúc.

## Key Code

```java
// Author.java — chỉ thêm annotation này:
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
@Fetch(FetchMode.SUBSELECT)
private List<Book> books = new ArrayList<>();
```

## Kết quả

| Scenario | SQL queries |
|----------|------------|
| Không có annotation (N+1) | **6** (1 + 5) |
| `@Fetch(SUBSELECT)`, bất kể N | **2** |

**Luôn đúng 2 queries**, dù N = 5 hay N = 10,000.

## Generated SQL

```sql
-- Query 1: Load all authors
SELECT a.id, a.name FROM authors a

-- Query 2: Load ALL books using subselect (triggered when first .getBooks() called)
SELECT b.id, b.title, b.publish_year, b.author_id
FROM books b
WHERE b.author_id IN (
    SELECT a2.id FROM authors a2
)
```

## Chạy demo
```bash
./gradlew :05-subselect-fetch:bootRun
```

## So sánh với @BatchSize

| | @BatchSize(5) | @Fetch(SUBSELECT) |
|-|--------------|-----------------|
| 5 authors | 2 queries | 2 queries |
| 100 authors | 5 queries | 2 queries |
| Chỉ cần books của 1 author | Batch 5 | Load tất cả |
| Hibernate-specific | Có | Có |

## Khi nào dùng
- Số lượng parent entities rất lớn (N >> batch_size)
- Luôn cần load collection cho tất cả parents
- Muốn guarantee chính xác 2 queries
