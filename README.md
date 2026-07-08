# N+1 Problem: 5 Solutions với Spring Data JPA

## N+1 Problem là gì?

Khi dùng ORM (Hibernate/JPA), N+1 problem xảy ra khi:
1. **1 query** để load danh sách entity cha (e.g., `SELECT * FROM authors`)
2. **N queries** để load collection liên kết cho từng entity (e.g., `SELECT * FROM books WHERE author_id = ?` × 5)

**Ví dụ:** 5 tác giả → 1 + 5 = **6 queries** thay vì 1.

```
Hibernate: select a1_0.id, a1_0.name from authors a1_0
Hibernate: select b1_0.author_id, ... from books b1_0 where b1_0.author_id=1
Hibernate: select b1_0.author_id, ... from books b1_0 where b1_0.author_id=2
Hibernate: select b1_0.author_id, ... from books b1_0 where b1_0.author_id=3
Hibernate: select b1_0.author_id, ... from books b1_0 where b1_0.author_id=4
Hibernate: select b1_0.author_id, ... from books b1_0 where b1_0.author_id=5
```

## Domain Model

```
Author (1) ──────< Book (N)
  - id              - id
  - name            - title
                    - publishYear
                    - author_id (FK)
```

Demo data: **5 tác giả**, mỗi người **3 cuốn sách** = 15 books tổng cộng.

## 5 Solutions — Tổng quan

| Module | Kỹ thuật | SQL queries | Ghi chú |
|--------|----------|-------------|---------|
| 01 | `JOIN FETCH` (JPQL) | **1** | Đơn giản, hiệu quả nhất |
| 02 | `@EntityGraph` | **1** | Linh hoạt hơn, config tại call-site |
| 03 | DTO Projection | **1** | Tốt nhất khi không cần full entity |
| 04 | `@BatchSize` | **ceil(N/batch)+1** | Trong trường hợp này: 2 |
| 05 | `@Fetch(SUBSELECT)` | **2** | Luôn đúng 2 queries |

## Cách chạy

### Yêu cầu
- Java 17+
- Gradle Wrapper đã có sẵn (`gradlew.bat` / `gradlew`)

### Build toàn bộ
```bash
./gradlew build
```

### Chạy từng module và quan sát SQL log

```bash
# Module 01: JOIN FETCH
./gradlew :01-join-fetch:bootRun

# Module 02: @EntityGraph
./gradlew :02-entity-graph:bootRun

# Module 03: DTO Projection
./gradlew :03-dto-projection:bootRun

# Module 04: @BatchSize
./gradlew :04-batch-size:bootRun

# Module 05: @Fetch(SUBSELECT)
./gradlew :05-subselect-fetch:bootRun
```

### Đọc output

Khi chạy mỗi module, chú ý các dòng:
- `Hibernate:` → SQL query thực sự được gửi xuống DB
- `[INFO]  ...` → Narrative của demo giải thích điều gì đang xảy ra

Đếm số dòng `Hibernate:` để thấy sự khác biệt.

## Cấu trúc code

Mỗi module có cùng package structure:

```
src/main/java/com/example/{module}/
├── {Module}Application.java       ← Spring Boot entry point
├── entity/
│   ├── Author.java                ← @Entity với @OneToMany(fetch = LAZY)
│   └── Book.java                  ← @Entity với @ManyToOne
├── repository/
│   └── AuthorRepository.java      ← JpaRepository + solution-specific methods
├── service/
│   └── AuthorService.java         ← @Transactional demo methods
└── runner/
    ├── DataInitializer.java       ← Seed data (@Order(1))
    └── DemoRunner.java            ← Chạy demo (@Order(2))
```

---

## So sánh chi tiết

### Bảng so sánh đa tiêu chí

| Tiêu chí | JOIN FETCH | @EntityGraph | DTO Projection | @BatchSize | @Fetch(SUBSELECT) |
|----------|-----------|-------------|---------------|-----------|------------------|
| **Số queries (5 authors)** | 1 | 1 | 1 | 2 | 2 |
| **Nơi cấu hình** | Repository | Repository / Entity | Repository + DTO | Entity | Entity |
| **Cần sửa entity?** | Không | Tùy | Không | Có | Có |
| **Cartesian product?** | Có (1 collection) | Có (1 collection) | Không | Không | Không |
| **Nhiều collections?** | Vấn đề | Vấn đề | OK | OK | OK |
| **Trả về full entity?** | Có | Có | Không | Có | Có |
| **Hibernate-specific?** | Không (JPQL) | Không (JPA) | Không (JPQL) | Có | Có |
| **Độ phức tạp** | Thấp | Thấp-Vừa | Vừa | Rất thấp | Rất thấp |

---

### Module 01: JOIN FETCH

#### Cơ chế
JPQL `JOIN FETCH` yêu cầu Hibernate tạo một `LEFT JOIN` trong câu SQL, nạp entity cha và
collection con trong **một query duy nhất**.

```sql
-- Generated SQL
SELECT DISTINCT a.id, a.name, b.id, b.title, b.publish_year, b.author_id
FROM authors a
LEFT OUTER JOIN books b ON b.author_id = a.id
ORDER BY a.id
```

#### Code
```java
@Query("SELECT DISTINCT a FROM Author a JOIN FETCH a.books ORDER BY a.id")
List<Author> findAllWithBooks();
```

#### Ưu điểm
- Đơn giản, JPQL chuẩn (không phụ thuộc Hibernate)
- 1 query duy nhất, hiệu suất tốt
- Dễ hiểu và debug

#### Nhược điểm
- **Cartesian product** với nhiều `@OneToMany`: `JOIN FETCH a.books JOIN FETCH a.tags`
  tạo ra rows = authors × books × tags → kết quả trùng lặp, cần `DISTINCT`
- Không thể dùng pagination (`setFirstResult`/`setMaxResults`) khi có `FETCH JOIN`
  (Hibernate phải load tất cả vào memory rồi mới slice)
- Phải viết query riêng cho từng use case

#### Khi nào dùng
- Fetch **1 collection** cho parent entity
- Không cần pagination trên kết quả sau JOIN
- Muốn kiểm soát query một cách tường minh

---

### Module 02: @EntityGraph

#### Cơ chế
`@EntityGraph` là cách JPA chuẩn để chỉ định attribute nào sẽ được eager-load cho một
query cụ thể — mà không cần viết JPQL thủ công.

```sql
-- Generated SQL (tương tự JOIN FETCH)
SELECT DISTINCT a.id, a.name, b.id, b.title, b.publish_year, b.author_id
FROM authors a
LEFT OUTER JOIN books b ON b.author_id = a.id
```

#### Code
```java
// Dynamic (ad-hoc):
@EntityGraph(attributePaths = {"books"})
List<Author> findAllWithBooks();

// Named (defined on entity):
@NamedEntityGraph(name = "Author.withBooks",
                  attributeNodes = @NamedAttributeNode("books"))
// Dùng trong repository:
@EntityGraph("Author.withBooks")
List<Author> findAll();
```

#### Ưu điểm
- Có thể áp dụng cho **derived query methods** (`findByName(...)`) mà không cần viết JPQL
- `@NamedEntityGraph` trên entity cho phép tái sử dụng across nhiều methods
- Tách biệt "what to load" khỏi "how to query"

#### Nhược điểm
- Cùng vấn đề Cartesian product với nhiều collections như JOIN FETCH
- `@NamedEntityGraph` trên entity làm entity class nặng hơn (mix concerns)
- Cú pháp verbose hơn JOIN FETCH một chút

#### Khi nào dùng
- Muốn tái sử dụng fetch strategy (named graph)
- Cần eager-load cho derived query methods
- Teamproject lớn: tách fetch strategy ra khỏi query string giúp maintain dễ hơn

---

### Module 03: DTO Projection

#### Cơ chế
Thay vì load full entity, query trả về chỉ đúng data cần thiết vào DTO/interface.
Không có entity lifecycle, không có lazy loading → không có N+1 bao giờ.

```sql
-- Interface projection: SELECT a.name, COUNT(b) FROM authors a LEFT JOIN books b ...
SELECT a.name AS name, COUNT(b.id) AS bookCount
FROM authors a
LEFT JOIN books b ON b.author_id = a.id
GROUP BY a.id, a.name

-- Class projection: SELECT new Dto(a.name, b.title, b.publish_year) ...
SELECT a.name, b.title, b.publish_year
FROM authors a
JOIN books b ON b.author_id = a.id
ORDER BY a.name, b.title
```

#### Code
```java
// Interface projection
public interface AuthorBookCountProjection {
    String getName();
    Long getBookCount();
}

@Query("SELECT a.name as name, COUNT(b) as bookCount " +
       "FROM Author a LEFT JOIN a.books b GROUP BY a.id, a.name ORDER BY a.id")
List<AuthorBookCountProjection> findAuthorBookCounts();

// Class projection (record)
public record AuthorBooksDto(String authorName, String bookTitle, int publishYear) {}

@Query("SELECT new com.example.dtoprojection.dto.AuthorBooksDto(a.name, b.title, b.publishYear) " +
       "FROM Author a JOIN a.books b ORDER BY a.name, b.title")
List<AuthorBooksDto> findAllAuthorBooks();
```

#### Ưu điểm
- **Tốt nhất về hiệu suất** — chỉ select đúng columns cần thiết
- Không bao giờ có N+1 vì không có entity lifecycle
- Phù hợp cho read-only APIs, reports, aggregations
- Records (Java 16+) làm DTO rất clean

#### Nhược điểm
- Không có entity lifecycle: không thể update/delete qua projection
- Tái cấu trúc query phức tạp hơn
- Cần viết DTO class/interface riêng

#### Khi nào dùng
- Read-only views, REST API responses
- Aggregation queries (COUNT, SUM, AVG)
- Chỉ cần subset of fields
- Performance-critical endpoints

---

### Module 04: @BatchSize

#### Cơ chế
`@BatchSize(size = N)` trên collection annotation yêu cầu Hibernate, khi cần khởi tạo
lazy collection, load nhiều collections cùng lúc bằng `IN` clause thay vì query riêng lẻ.

```sql
-- Thay vì 5 queries riêng:
-- SELECT * FROM books WHERE author_id = 1
-- SELECT * FROM books WHERE author_id = 2
-- ...

-- Hibernate gộp thành 1 query:
SELECT b.id, b.title, b.publish_year, b.author_id
FROM books b
WHERE b.author_id IN (1, 2, 3, 4, 5)
```

#### Code
```java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
@BatchSize(size = 5)
private List<Book> books = new ArrayList<>();
```

#### Số queries
- N authors, batch size B → `1 + ceil(N / B)` queries
- 5 authors, batch size 5 → `1 + ceil(5/5) = 2` queries
- 100 authors, batch size 25 → `1 + ceil(100/25) = 5` queries

#### Ưu điểm
- **Transparent**: không cần sửa queries hay services
- Phù hợp với nhiều collections (mỗi collection có @BatchSize riêng)
- Không có vấn đề Cartesian product
- Pagination vẫn hoạt động bình thường

#### Nhược điểm
- Vẫn có nhiều queries khi data lớn (không phải 1 query)
- `@BatchSize` là Hibernate-specific annotation (không phải JPA chuẩn)
- Phụ thuộc vào việc nhiều entities ở cùng session

#### Khi nào dùng
- Có **nhiều `@OneToMany` collections** trên cùng entity (tránh Cartesian product)
- Cần pagination trên parent entity
- Không muốn sửa repository queries
- Acceptable với 2-3 queries thay vì N+1

---

### Module 05: @Fetch(FetchMode.SUBSELECT)

#### Cơ chế
`@Fetch(FetchMode.SUBSELECT)` khiến Hibernate, khi khởi tạo lazy collection cho bất kỳ
entity nào trong session, sẽ dùng **subquery** để load collections cho TẤT CẢ entities
trong session hiện tại.

```sql
-- Query 1: Load all authors
SELECT a.id, a.name FROM authors a

-- Query 2: Load books for ALL authors using subquery
SELECT b.id, b.title, b.publish_year, b.author_id
FROM books b
WHERE b.author_id IN (
    SELECT a2.id FROM authors a2
)
```

#### Code
```java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
@Fetch(FetchMode.SUBSELECT)
private List<Book> books = new ArrayList<>();
```

#### Số queries
- **Luôn đúng 2 queries** — không phụ thuộc vào số lượng parent entities
- 5 authors → 2 queries
- 1000 authors → vẫn 2 queries

#### Ưu điểm
- **Luôn 2 queries** — predictable, không phụ thuộc N
- Transparent như @BatchSize
- Đặc biệt tốt khi N rất lớn (hundreds/thousands of authors)

#### Nhược điểm
- Hibernate-specific
- Load collections cho **tất cả entities trong session**, dù chỉ cần 1 — có thể load thừa
- Subquery có thể chậm hơn JOIN nếu DB optimizer không handle tốt
- Không thể kiểm soát được "chỉ load cho một vài entities"

#### Khi nào dùng
- N (số parent entities) rất lớn và batch size không hiệu quả
- Cần guaranteeing chính xác 2 queries
- Toàn bộ session cần cùng collection data

---

## Quy tắc chọn solution

```
Cần full entity? ──No──> DTO Projection (Module 03)
       │
      Yes
       │
Chỉ 1 collection? ──Yes──> JOIN FETCH (01) hoặc @EntityGraph (02)
       │
      No (nhiều collections)
       │
N nhỏ/vừa? ──Yes──> @BatchSize (04)
       │
      No (N lớn)
       │
       └──> @Fetch(SUBSELECT) (05)
```

## Best Practices

1. **Luôn dùng `FetchType.LAZY`** cho `@OneToMany` / `@ManyToMany` — đây là default đúng đắn
2. **Dùng JOIN FETCH hoặc EntityGraph** khi biết trước cần load collection
3. **Dùng DTO Projection** khi chỉ cần một tập con dữ liệu (read-only views)
4. **Dùng @BatchSize** khi có nhiều collection và không muốn sửa từng query
5. **Tránh `FetchType.EAGER`** — nó "giải quyết" N+1 bằng cách tạo ra Cartesian product không kiểm soát được
6. **Phát hiện N+1** với: `logging.level.org.hibernate.SQL=DEBUG` hoặc tools như Hypersistence Optimizer, p6spy

## Phát hiện N+1 trong production

1. **application.properties**: `logging.level.org.hibernate.SQL=DEBUG`
2. **p6spy**: Thư viện log SQL + timing
3. **Hypersistence Optimizer**: Phân tích static, tìm N+1 tại compile time
4. **Spring Boot Actuator + Micrometer**: Metric về số queries
5. **DataSource Proxy**: Wrap datasource để đếm và log queries
