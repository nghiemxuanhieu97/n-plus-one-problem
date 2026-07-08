# Module 03: DTO Projection

## Kỹ thuật
Thay vì load full entity, project data thẳng vào interface hoặc record DTO.
Không có entity lifecycle → không bao giờ có N+1.

## Key Code

```java
// Interface Projection:
public interface AuthorBookCountProjection {
    String getName();
    Long getBookCount();
}

@Query("SELECT a.name as name, COUNT(b) as bookCount " +
       "FROM Author a LEFT JOIN a.books b GROUP BY a.id, a.name ORDER BY a.id")
List<AuthorBookCountProjection> findAuthorBookCounts();

// Class Projection (Record):
public record AuthorBooksDto(String authorName, String bookTitle, int publishYear) {}

@Query("SELECT new com.example.dtoprojection.dto.AuthorBooksDto(a.name, b.title, b.publishYear) " +
       "FROM Author a JOIN a.books b ORDER BY a.name, b.title")
List<AuthorBooksDto> findAllAuthorBooks();
```

## Kết quả

| Scenario | SQL queries |
|----------|------------|
| `findAll()` + lazy `getBooks()` | **6** (1 + 5) |
| Interface projection (aggregation) | **1** |
| Class projection (flat list) | **1** |

## Chạy demo
```bash
./gradlew :03-dto-projection:bootRun
```

## Khi nào dùng
- Read-only views, API responses
- Chỉ cần subset of data (ít columns)
- Aggregation (COUNT, SUM, AVG)
- Không cần thao tác entity lifecycle (update/delete)
