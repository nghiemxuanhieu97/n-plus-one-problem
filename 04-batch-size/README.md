# Module 04: @BatchSize

## Kỹ thuật
`@BatchSize(size = N)` trên collection — Hibernate gộp N lazy-load requests
thành một `IN` clause query thay vì N queries riêng lẻ.

## Key Code

```java
// Author.java — chỉ thêm annotation này:
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
@BatchSize(size = 5)
private List<Book> books = new ArrayList<>();
```

Không cần sửa Repository hay Service!

## Kết quả

| Scenario | SQL queries |
|----------|------------|
| Không có `@BatchSize` (N+1) | **6** (1 + 5) |
| `@BatchSize(size = 5)`, 5 authors | **2** (1 + 1 batch) |
| `@BatchSize(size = 25)`, 100 authors | **5** (1 + 4 batches) |

Công thức: `1 + ceil(N / batch_size)`

## Generated SQL

```sql
-- Authors query (1)
SELECT a.id, a.name FROM authors a

-- Books query (1 batch, not 5 individual)
SELECT b.id, b.title, b.publish_year, b.author_id
FROM books b
WHERE b.author_id IN (1, 2, 3, 4, 5)
```

## Chạy demo
```bash
./gradlew :04-batch-size:bootRun
```

## Lưu ý
- `@BatchSize` là Hibernate-specific, không phải JPA chuẩn
- Không có vấn đề Cartesian product (mỗi collection là 1 query riêng)
- Pagination trên parent entity vẫn hoạt động bình thường
