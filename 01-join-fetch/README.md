# Module 01: JOIN FETCH

## Kỹ thuật
JPQL `JOIN FETCH` — load entity và collection liên kết trong một query duy nhất.

## Key Code

```java
// AuthorRepository.java
@Query("SELECT DISTINCT a FROM Author a JOIN FETCH a.books ORDER BY a.id")
List<Author> findAllWithBooks();
```

## Kết quả

| Scenario | SQL queries |
|----------|------------|
| `findAll()` + lazy `getBooks()` | **6** (1 + 5) |
| `findAllWithBooks()` với JOIN FETCH | **1** |

## Chạy demo
```bash
./gradlew :01-join-fetch:bootRun
```

## Lưu ý
- Dùng `DISTINCT` để loại bỏ duplicate rows sinh ra bởi JOIN
- Không thể dùng pagination (`setFirstResult`/`setMaxResults`) với FETCH JOIN
- Nếu có nhiều `@OneToMany`, chỉ FETCH JOIN một collection để tránh Cartesian product
