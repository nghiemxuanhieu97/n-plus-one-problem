# Module 02: JPA EntityGraph

Mục tiêu:

```text
EntityGraph giải quyết câu hỏi:
"Trong use case này, association nào cần được load?"

Nó giúp tránh N+1 mà không phải đổi association thành EAGER toàn cục
và không bắt buộc nhúng fetch join vào JPQL.
```

---

## 0. Chuẩn bị demo

Khởi động module:

```powershell
.\gradlew.bat :02-entity-graph:bootRun
```

Mở Swagger:

```text
http://localhost:8082/swagger-ui/index.html
```

Mỗi endpoint:

```text
1. Chạy đúng một scenario.
2. Trả response mô tả kết quả kỳ vọng.
3. In SQL và block [METRICS] trong console.
```

Dataset:

```text
5 Authors
15 Books          = mỗi Author có 3 Books
10 Awards         = mỗi Author có 2 Awards
5 Publishers
3 Countries
```

Relationship chính:

```text
Country   1 ----- N Author
Author    1 ----- N Book
Publisher 1 ----- N Book
Author    1 ----- N Award
```

Mapping trọng tâm:

```java
@OneToMany(mappedBy = "author", fetch = FetchType.LAZY)
private List<Book> books;
```

`Author.books` là `LAZY`: query Authors không mặc định lấy Books.

`Author.country` được cố tình để `EAGER` chỉ nhằm làm demo `FETCH` graph và `LOAD` graph quan sát được. Đây không phải mapping được khuyến nghị cho production.

---

# Part 1 — Problem

## 1. N+1 xuất hiện như thế nào?

Endpoint:

```http
GET /api/entity-graph/01-baseline-n-plus-one
```

Service:

```java
List<Author> authors = authorRepository.findAll();

for (Author author : authors) {
    author.getBooks().size();
}
```

Flow:

```text
Repository query lấy 5 Authors
  -> Country được load theo mapping EAGER
  -> Author.books vẫn chưa được load
  -> vòng lặp chạm books của Author 1
  -> Hibernate chạy một query Books
  -> chạm Author 2
  -> Hibernate chạy thêm một query
  -> lặp lại cho cả 5 Authors
```

SQL rút gọn:

```sql
-- Query 1: lấy Authors
SELECT a.id, a.name
FROM authors a
ORDER BY a.id;

-- Query 2..4: ba Countries được tham chiếu bởi Authors
SELECT c.* FROM countries c WHERE c.id = 1;
SELECT c.* FROM countries c WHERE c.id = 2;
SELECT c.* FROM countries c WHERE c.id = 3;

-- Query 5
SELECT b.*
FROM books b
WHERE b.author_id = 1;

-- Query 6
SELECT b.*
FROM books b
WHERE b.author_id = 2;

-- Query 7, 8, 9 tương tự cho ba Authors còn lại
```

Kết quả:

```text
1 query lấy Authors
+ 3 query EAGER Countries
+ 5 query lazy Books
= 9 JDBC statements
```

Metrics:

```text
JDBC statements:      9
entities loaded:      23
collections loaded:   5
collections fetched:  5
```

Giải thích:

```text
entities loaded:
5 Authors + 3 Countries + 15 Books = 23

collections loaded:
5 Author.books collections được initialized

collections fetched:
5 lazy collection fetch events
```

### Tác động

```text
N+1 không làm dữ liệu sai.
Nó làm tăng database round-trips.

Số Authors càng lớn:
1 + N queries càng lớn.
```

Ví dụ production:

```text
Page có 100 Authors
-> 1 query Authors
-> có thể thêm 100 query Books
-> tổng 101 statements
```

Vấn đề nằm ở access pattern:

```text
load nhiều parent
-> truy cập một LAZY association trong vòng lặp
```

### Vì sao có thêm ba Country queries?

`Author.country` đang cố tình mapping `EAGER` để scenario 08 có thể so sánh `FETCH` và `LOAD`.

```text
Country EAGER:
3 Countries được load cùng use case.

Books LAZY:
5 query lặp lại khi vòng lặp truy cập Author.books.
```

Ba Country queries không phải N+1 đang được phân tích. Dấu hiệu N+1 chính là năm câu SQL Books có cùng shape nhưng khác `author_id`.

---

# Part 2 — Solution

## 2. EntityGraph là gì?

EntityGraph là một fetch plan:

```text
Query:
chọn root entities nào.

EntityGraph:
association nào của root entities phải được initialized.
```

Ví dụ:

```java
@EntityGraph(attributePaths = "books")
@Query("SELECT a FROM Author a ORDER BY a.id")
List<Author> findAllWithDynamicGraph();
```

Ý nghĩa:

```text
JPQL:
lấy tất cả Authors theo thứ tự ID.

EntityGraph:
khi trả kết quả, Books của các Authors phải được load.
```

Hibernate thường kết hợp thành:

```sql
SELECT a.id, a.name, b.*
FROM authors a
LEFT JOIN books b ON b.author_id = a.id
ORDER BY a.id;
```

So với baseline:

```text
Baseline:
1 Author query + 3 Country queries + 5 Book queries = 9 statements

EntityGraph:
1 joined query = 1 statement
```

Metrics:

```text
JDBC statements:      1
entities loaded:      20
collections loaded:   5
collections fetched:  0
```

`collections fetched = 0` vì không có lazy collection SELECT riêng. Books đến từ root query.

## 3. Database rows trở thành object graph như thế nào?

Database trả 15 joined rows:

```text
A1-B1
A1-B2
A1-B3
A2-B4
...
A5-B15
```

Hibernate dùng primary keys và Persistence Context để hydrate thành:

```text
5 Author objects
15 Book objects
5 initialized Author.books collections
```

Nó không tạo 15 Author objects độc lập.

```text
Các rows có cùng author_id
-> cùng một managed Author identity
-> Books được thêm vào collection tương ứng
```

EntityGraph giảm statements, nhưng JOIN có thể nhân SQL rows. Đây sẽ là giới hạn được demo ở phần sau.

## 4. Điều EntityGraph cam kết và không cam kết

EntityGraph cam kết về fetch state:

```text
Association trong graph phải available/initialized theo graph semantics.
```

EntityGraph không cam kết:

```text
- luôn đúng một SQL;
- luôn dùng LEFT JOIN;
- luôn nhanh hơn;
- result set luôn nhỏ;
- pagination ToMany luôn an toàn;
- fetch nhiều collections không có trade-off.
```

Provider có thể chọn:

```text
- JOIN trong root query;
- secondary SELECT;
- kết hợp nhiều chiến lược.
```

Tóm lại:

```text
EntityGraph mô tả "cần load cái gì",
không phải một lời cam kết "phải load bằng đúng SQL nào".
```

## 5. Ba cách khai báo graph

### Dynamic graph

```java
@EntityGraph(attributePaths = "books")
List<Author> findAllWithDynamicGraph();
```

Phù hợp khi graph:

```text
- ngắn;
- chỉ dùng ở một repository method;
- nhìn ngay method là hiểu.
```

### Named graph

Khai báo trên entity:

```java
@NamedEntityGraph(
    name = "Author.withBooks",
    attributeNodes = @NamedAttributeNode("books")
)
```

Sử dụng:

```java
@EntityGraph("Author.withBooks")
List<Author> findAllWithNamedGraph();
```

Phù hợp khi:

```text
- fetch plan được tái sử dụng;
- có nested subgraph;
- muốn đặt tên cho use case.
```

Named graph không tự động nhanh hơn dynamic graph. Với graph chỉ có Books, chúng tạo SQL và metrics giống nhau.

### Programmatic graph

```java
EntityGraph<Author> graph = entityManager.createEntityGraph(Author.class);
graph.addAttributeNodes("books");

Author author = entityManager.find(
    Author.class,
    id,
    Map.of("jakarta.persistence.fetchgraph", graph)
);
```

Phù hợp khi fetch plan phụ thuộc runtime flags, permissions hoặc request shape.

Trade-off:

```text
+ linh hoạt
- verbose
- dùng string attribute names
- số combinations có thể tăng nhanh
```

---

# Part 3 — Demo

## 6. Thông tin quan sát trong mỗi scenario

Mỗi endpoint được phân tích theo cùng một cấu trúc:

```text
1. Use case cần gì?
2. Repository code là gì?
3. SQL shape thay đổi ra sao?
4. Metrics nói gì?
5. Trade-off hoặc conclusion là gì?
```

Ưu tiên đọc metrics:

```text
1. JDBC statements
2. SQL shape
3. entities loaded
4. collections loaded
5. collections fetched
```

Không nên kết luận từ `elapsed` của một lần chạy H2 local.

## 7. Bảng kết quả tổng hợp

Các số dưới đây dùng dataset mặc định:

| # | Scenario | JDBC | Entities | Collections loaded | Collections fetched |
|---:|---|---:|---:|---:|---:|
| 01 | Baseline N+1 | 9 | 23 | 5 | 5 |
| 02 | Dynamic graph | 1 | 20 | 5 | 0 |
| 03 | Named graph | 1 | 20 | 5 | 0 |
| 04 | Derived query | 1 | 8 | 2 | 0 |
| 05 | `findById` graph | 1 | 4 | 1 | 0 |
| 06 | Nested dynamic | 1 | 25 | 5 | 0 |
| 06 | Nested named | 1 | 25 | 5 | 0 |
| 07 | JPQL + graph | 1 | 12 | 3 | 0 |
| 08 | `FETCH` graph | 1 | 20 | 5 | 0 |
| 08 | `LOAD` graph | 4 | 23 | 5 | 0 |
| 09 | Programmatic graph | 1 | 4 | 1 | 0 |
| 10 | Runtime: no collections | 1 | 2 | 0 | 0 |
| 10 | Runtime: Books only | 1 | 6 | 1 | 0 |
| 10 | Runtime: Awards only | 1 | 4 | 1 | 0 |
| 10 | Runtime: Books + Awards | 3 | 8 | 2 | 1 |
| 11 | Books + Awards | 1 | 30 | 10 | 0 |
| 12 | Pagination ToOne | 2 | 3 | 0 | 0 |
| 12 | Pagination ToMany | 2 | 20 | 5 | 0 |
| 12 | Two-step pagination | 3 | 8 | 2 | 0 |

SQL alias, column order và execution plan có thể thay đổi theo Hibernate version và database dialect. Ý nghĩa fetch plan mới là phần ổn định.

---

## Demo stage A — Từ problem đến fix

### 8. Scenario 01 — Baseline N+1

```http
GET /api/entity-graph/01-baseline-n-plus-one
```

Quan sát:

```text
SELECT Authors
SELECT Books WHERE author_id = ?
SELECT Books WHERE author_id = ?
...
```

Kết luận:

```text
LAZY không xấu.
N+1 xuất hiện khi code access LAZY association lặp lại trên nhiều parents.
```

### 9. Scenario 02 — Dynamic EntityGraph

```http
GET /api/entity-graph/02-dynamic-graph
```

Repository:

```java
@EntityGraph(attributePaths = "books")
@Query("SELECT a FROM Author a ORDER BY a.id")
List<Author> findAllWithDynamicGraph();
```

Quan sát:

```text
9 JDBC -> 1 JDBC
5 lazy fetch events -> 0
```

SQL:

```sql
SELECT a.*, b.*
FROM authors a
LEFT JOIN books b ON b.author_id = a.id
ORDER BY a.id;
```

Kết luận:

```text
Ta không đổi Author.books thành EAGER.
Chỉ repository method này yêu cầu Books.
```

### 10. Scenario 03 — Named EntityGraph

```http
GET /api/entity-graph/03-named-graph
```

Named graphs hiện có:

```text
@NamedEntityGraphs
├── Author.withBooks
├── Author.withBooksAndPublisher
└── Author.withBooksAndAwards
```

Đây là ba graphs độc lập được đặt trong một container annotation. Hibernate không tự gộp cả ba.

SQL và metrics giống Dynamic graph.

Kết luận:

```text
Dynamic và Named không khác nhau về performance trong graph đơn giản này.
Named graph có giá trị ở naming, reuse và nested subgraph.
```

---

## Demo stage B — Áp dụng graph vào các query styles

### 11. Scenario 04 — Derived query + EntityGraph

```http
GET /api/entity-graph/04-derived-query
```

Repository:

```java
@EntityGraph("Author.withBooks")
List<Author> findByNameContainingIgnoreCaseOrderById(String name);
```

Service truyền `"George"`.

Spring Data tạo phần filter:

```sql
WHERE LOWER(a.name) LIKE LOWER('%George%')
```

EntityGraph thêm fetch plan cho Books.

Metrics:

```text
2 Authors + 6 Books = 8 entities
1 JDBC statement
```

Kết luận:

```text
Derived method quyết định rows.
EntityGraph quyết định associations.
Không cần viết JPQL chỉ để fetch Books.
```

### 12. Scenario 05 — EntityGraph trên `findById`

```http
GET /api/entity-graph/05-find-by-id
```

Repository:

```java
@Override
@EntityGraph("Author.withBooks")
Optional<Author> findById(Long id);
```

Use case:

```text
Author detail luôn cần Books.
```

Metrics:

```text
1 Author + 3 Books = 4 entities
1 JDBC statement
```

Trade-off:

```text
Override ảnh hưởng mọi caller của findById.
Nếu có caller không cần Books, tạo method riêng rõ intent hơn.
```

### 13. Scenario 06 — Nested EntityGraph

```http
GET /api/entity-graph/06-nested-graph
```

Fetch plan:

```text
Author
└── Books
    └── Publisher
```

Dynamic:

```java
@EntityGraph(attributePaths = {"books", "books.publisher"})
```

Named:

```text
Author.withBooksAndPublisher
└── @NamedSubgraph books.publisher
```

SQL:

```sql
SELECT a.*, b.*, p.*
FROM authors a
LEFT JOIN books b      ON b.author_id = a.id
LEFT JOIN publishers p ON p.id = b.publisher_id
ORDER BY a.id;
```

Metrics cho mỗi form:

```text
5 Authors + 15 Books + 5 Publishers = 25 entities
1 JDBC statement
```

Kết luận:

```text
Nested graph tránh N+1 tiếp theo khi code gọi:
book.getPublisher().getName()
```

### 14. Scenario 07 — Custom JPQL + EntityGraph

```http
GET /api/entity-graph/07-query-plus-graph
```

Repository:

```java
@EntityGraph("Author.withBooks")
@Query("""
    SELECT a
    FROM Author a
    WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
       OR a.id > :minId
    ORDER BY a.id
    """)
List<Author> searchWithQueryAndEntityGraph(...);
```

Service truyền:

```text
keyword = George
minId   = 4
```

Authors được chọn:

```text
ID 3: George R.R. Martin
ID 4: George Orwell
ID 5: Alexandre Dumas
```

Metrics:

```text
3 Authors + 9 Books = 12 entities
1 JDBC statement
```

Kết luận:

```text
JPQL:
WHERE, ORDER BY và root rows.

EntityGraph:
fetch Books.

Hibernate:
kết hợp cả hai thành execution plan.
```

Flow đầy đủ từ JPQL xuống database được tách tại [Từ JPQL đến Database và trở lại Java](JPQL_TO_DATABASE_FLOW.md).

---

## Demo stage C — Semantics và runtime fetch plans

### 15. Scenario 08 — `FETCH` graph và `LOAD` graph

```http
GET /api/entity-graph/08-fetch-vs-load
```

Graph chỉ chứa Books. `Author.country` đang mapping EAGER để tạo sự khác biệt.

`FETCH` graph:

```text
Trong graph  -> fetch
Ngoài graph -> xem như LAZY cho operation này
```

Kết quả:

```text
Books được load
Country không được load
1 JDBC
20 entities
```

`LOAD` graph:

```text
Trong graph  -> fetch
Ngoài graph -> giữ mapping mặc định
```

Country giữ EAGER. Hibernate 6.4 hiện dùng ba secondary selects cho ba Countries:

```text
1 root query
+ 3 Country queries
= 4 JDBC
```

Metrics:

```text
5 Authors + 15 Books + 3 Countries = 23 entities
```

Kết luận:

```text
FETCH/LOAD là semantics của fetch plan.
Không phải hai keyword bắt Hibernate dùng hai SQL shapes cố định.
EAGER cũng không đồng nghĩa luôn JOIN.
```

### 16. Scenario 09 — Programmatic EntityGraph

```http
GET /api/entity-graph/09-programmatic-graph
```

Code:

```java
EntityGraph<Author> graph = entityManager.createEntityGraph(Author.class);
graph.addAttributeNodes("books");

Author author = entityManager.find(
    Author.class,
    2L,
    Map.of("jakarta.persistence.fetchgraph", graph)
);
```

Metrics:

```text
1 Author + 3 Books = 4 entities
1 JDBC statement
```

Kết luận:

```text
Graph có thể được build ở runtime,
không bắt buộc phải nằm trong annotation.
```

### 17. Scenario 10 — Runtime graph theo request flags

Endpoint:

```http
GET /api/entity-graph/10-runtime-graph/authors/{id}
    ?includeBooks=...
    &includeAwards=...
```

Graph luôn chứa Country vì DTO luôn trả Country.

Khi `includeBooks=true`, graph thêm:

```text
Books -> Publisher
```

Khi `includeAwards=true`, graph thêm Awards.

#### Case A — Không collection

```http
GET /api/entity-graph/10-runtime-graph/authors/1?includeBooks=false&includeAwards=false
```

```text
1 Author + 1 Country
1 JDBC
0 collections
```

#### Case B — Chỉ Books

```http
GET /api/entity-graph/10-runtime-graph/authors/1?includeBooks=true&includeAwards=false
```

```text
Author + Country + 3 Books + 1 Publisher
1 JDBC
1 collection
```

#### Case C — Chỉ Awards

```http
GET /api/entity-graph/10-runtime-graph/authors/1?includeBooks=false&includeAwards=true
```

```text
Author + Country + 2 Awards
1 JDBC
1 collection
```

#### Case D — Books và Awards

```http
GET /api/entity-graph/10-runtime-graph/authors/1?includeBooks=true&includeAwards=true
```

Hibernate 6.4 hiện quan sát được:

```text
Query 1: Author + Country + Awards
Query 2: Books
Query 3: Publisher
```

Metrics:

```text
JDBC statements:      3
entities loaded:      8
collections loaded:   2
collections fetched:  1
```

Vì sao không phải một query?

```text
Graph yêu cầu Books và Awards được load.
Graph không buộc provider JOIN cả hai collections.

Hibernate có thể chọn secondary select để tránh hoặc quản lý
row multiplication của nhiều ToMany associations.
```

DTO convention:

```text
null -> client không yêu cầu field
[]   -> client yêu cầu nhưng không có dữ liệu
```

Kết luận:

```text
Programmatic graph phù hợp với runtime flags,
nhưng API phải giới hạn combinations và graph depth.
```

---

## Demo stage D — Limitations

### 18. Scenario 11 — Hai ToMany collections

```http
GET /api/entity-graph/11-multiple-collections
```

Graph:

```text
Author
├── 3 Books
└── 2 Awards
```

JOIN tạo cho mỗi Author:

```text
3 Books × 2 Awards = 6 SQL rows
```

Toàn dataset:

```text
5 Authors × 6 rows = 30 joined rows
```

SQL:

```sql
SELECT a.*, b.*, aw.*
FROM authors a
LEFT JOIN books b   ON b.author_id = a.id
LEFT JOIN awards aw ON aw.author_id = a.id
ORDER BY a.id;
```

Metrics:

```text
JDBC statements:      1
entities loaded:      30
collections loaded:   10
collections fetched:  0
```

Một statement không đồng nghĩa một result set nhỏ.

`Author.awards` dùng `Set`, nên Hibernate có thể fetch cùng `List<Book>` mà không gặp trường hợp hai Bags. Tuy nhiên:

```text
Set tránh MultipleBagFetchException trong mapping này.
Set không tránh Cartesian row multiplication.
Set không làm pagination ToMany an toàn.
```

Kết luận:

```text
Tối ưu query count mà bỏ qua row count có thể tạo một query rất nặng.
```

Phần Bag, `PersistentBag`, duplicate semantics và cách tái hiện exception nằm trong [EntityGraph deep-dive reference](ENTITY_GRAPH_DEEP_DIVE.md).

### 19. Scenario 12 — Pagination boundaries

```http
GET /api/entity-graph/12-pagination
```

Endpoint chạy ba trường hợp với:

```java
PageRequest.of(0, 2)
```

#### Case A — ToOne Country: an toàn

```text
Một Author có tối đa một Country.
JOIN vẫn gần một SQL row cho mỗi Author.
Database có thể áp OFFSET/LIMIT trực tiếp.
```

SQL:

```sql
SELECT a.*, c.*
FROM authors a
LEFT JOIN countries c ON c.id = a.country_id
ORDER BY a.id
OFFSET 0 ROWS FETCH FIRST 2 ROWS ONLY;

SELECT COUNT(a.id) FROM authors a;
```

Metrics:

```text
2 JDBC
2 Authors + 1 shared Country = 3 entities
```

#### Case B — ToMany Books: không an toàn

JOIN rows:

```text
A1-B1
A1-B2
A1-B3
A2-B4
A2-B5
A2-B6
```

Nếu database cắt hai rows:

```text
không có hai Authors hoàn chỉnh
và Books collection của A1 cũng chưa đủ.
```

Với config hiện tại:

```yaml
hibernate:
  query:
    fail_on_pagination_over_collection_fetch: false
```

Hibernate bỏ SQL-level pagination, load toàn bộ joined result rồi cắt hai Authors trong memory.

Warning:

```text
HHH90003004: firstResult/maxResults specified with collection fetch;
applying in memory
```

Metrics:

```text
page size = 2
nhưng Hibernate load 5 Authors + 15 Books

JDBC statements:      2
entities loaded:      20
collections loaded:   5
```

`fail_on_pagination_over_collection_fetch=true` chỉ biến warning thành exception fail-fast. Nó không tự sửa query.

#### Case C — Two-step pagination: an toàn

Bước 1 page Author IDs:

```sql
SELECT a.id
FROM authors a
ORDER BY a.id
OFFSET 0 ROWS FETCH FIRST 2 ROWS ONLY;
```

Bước 2 count:

```sql
SELECT COUNT(a.id)
FROM authors a;
```

Bước 3 fetch đúng Authors trong page cùng Books:

```sql
SELECT a.*, b.*
FROM authors a
LEFT JOIN books b ON b.author_id = a.id
WHERE a.id IN (1, 2)
ORDER BY a.id;
```

Metrics:

```text
JDBC statements:      3
entities loaded:      8
collections loaded:   2
collections fetched:  0
```

Kết luận:

```text
ToOne:
paginate trực tiếp thường an toàn.

ToMany:
page parent IDs trước,
sau đó fetch associations cho đúng page.
```

Ba queries an toàn có thể tốt hơn hai queries nếu hai queries kia phải load toàn bộ dataset rồi paginate trong memory.

---

# Part 4 — Best Practices

## 20. Giữ mapping mặc định `LAZY`

Ưu tiên:

```java
@ManyToOne(fetch = FetchType.LAZY)
@OneToMany(fetch = FetchType.LAZY)
```

Sau đó chọn fetch plan theo từng use case.

Lý do:

```text
EAGER là quyết định toàn cục ở mapping.
EntityGraph là quyết định cục bộ theo repository operation.
```

`Author.country = EAGER` trong module chỉ là teaching setup cho `FETCH` vs `LOAD`.

## 21. Graph phải phản ánh use case

Ví dụ:

```text
Author list:
có thể chỉ cần Author summary.

Author detail:
có thể cần Books.

Author export:
có thể cần Books + Publisher.
```

Không tạo một graph “load everything” rồi dùng cho mọi nơi.

## 22. Chọn declaration phù hợp

```text
Dynamic graph:
graph ngắn, dùng một chỗ.

Named graph:
reuse, nested graph hoặc fetch plan có domain meaning.

Programmatic graph:
runtime flags thực sự cần thiết và combinations được kiểm soát.
```

Đừng dùng Named graph chỉ vì nghĩ nó nhanh hơn Dynamic graph.

## 23. Tách row selection khỏi fetch plan

EntityGraph không thay thế JPQL.

```text
JPQL/derived query:
filter, sort, aggregate, chọn root rows.

EntityGraph:
chọn associations cần initialized.
```

Nếu query cần điều kiện phức tạp:

```java
@Query("...")
@EntityGraph("...")
```

là một combination hợp lệ.

## 24. Không đánh giá chỉ bằng số statements

Luôn nhìn:

```text
- số JDBC statements;
- SQL shape;
- số joined rows;
- entities loaded;
- collections loaded/fetched;
- pagination xảy ra ở database hay memory;
- execution plan trên database production.
```

Ví dụ:

```text
Books + Awards:
1 statement nhưng 30 joined rows.

Two-step pagination:
3 statements nhưng chỉ load dữ liệu của đúng 2 Authors.
```

## 25. Cẩn thận với nhiều ToMany collections

Trước khi fetch cùng lúc:

```text
ước lượng:
parent count × collection A cardinality × collection B cardinality
```

Nếu result set lớn:

```text
- fetch một collection trước;
- dùng secondary query;
- dùng BatchSize;
- dùng DTO projection;
- chia use case/API;
- tránh client chọn graph không giới hạn.
```

Đổi `List` thành `Set` có thể tránh MultipleBagFetchException, nhưng không phải performance fix cho Cartesian product.

## 26. Không collection-fetch-join trực tiếp với Pageable

Khuyến nghị cho ToMany:

```text
1. Page parent IDs.
2. Fetch entities/children theo IDs.
```

Hoặc cân nhắc:

```text
- BatchSize;
- DTO projection;
- keyset pagination;
- query chuyên dụng.
```

Ở môi trường production, cân nhắc:

```yaml
hibernate:
  query:
    fail_on_pagination_over_collection_fetch: true
```

để unsafe pagination fail sớm thay vì âm thầm load toàn bộ.

## 27. Giữ transaction boundary rõ ràng

Module dùng:

```yaml
spring.jpa.open-in-view: false
```

và:

```java
@Transactional(readOnly = true)
```

Fetch và map DTO bên trong service transaction.

Không để Jackson vô tình gọi LAZY getters và phát sinh SQL ở web layer.

## 28. DTO projection khi không cần managed entity

EntityGraph phù hợp khi use case cần entity graph.

Nếu API chỉ cần:

```text
authorName
bookCount
latestBookTitle
```

DTO projection có thể rõ và nhẹ hơn vì select đúng columns cần trả về.

## 29. Chọn strategy theo access pattern

| Nhu cầu | Strategy nên cân nhắc |
|---|---|
| Cần entity + association ngay trong use case | EntityGraph hoặc JOIN FETCH |
| Fetch plan thay đổi theo repository method | EntityGraph |
| Query cần điều khiển JOIN/filter trực tiếp | JPQL JOIN/JOIN FETCH |
| Giữ LAZY nhưng access nhiều targets theo lô | BatchSize |
| API read-only chỉ cần vài fields | DTO projection |
| Parent pagination + ToMany | Two-step, BatchSize hoặc DTO |
| Nhiều collections lớn | Tách queries/use cases |

## 30. Checklist trước khi merge một graph

```text
[ ] Use case có thực sự cần managed entities không?
[ ] Association nào thực sự được sử dụng?
[ ] Graph có ToMany không?
[ ] Có fetch nhiều ToMany cùng lúc không?
[ ] Ước lượng joined rows là bao nhiêu?
[ ] Query có Pageable không?
[ ] SQL có secondary selects không?
[ ] Metrics có giảm query nhưng tăng data load quá nhiều không?
[ ] Named graph có thực sự được reuse không?
[ ] Runtime graph combinations có được giới hạn không?
[ ] Có integration test trên database thật cho query quan trọng không?
```

---

# Tổng kết

## 31. Kết luận

```text
N+1 xuất hiện khi load nhiều parent rồi lazy-load association từng parent.

EntityGraph cho phép chọn association cần load theo từng use case,
trong khi vẫn giữ mapping mặc định LAZY.

Nó tách row selection khỏi fetch plan:
query chọn Authors,
graph chọn associations.

Nhưng EntityGraph không phải performance guarantee.
Một statement có thể tạo result set lớn,
nhiều ToMany có thể nhân rows,
và collection pagination có thể chạy trong memory.

Vì vậy phải chọn graph theo use case
và đánh giá bằng SQL shape, row count và metrics,
không chỉ bằng số query.
```

---

## 32. Tài liệu đọc sâu

Các chủ đề nền tảng và phân tích chi tiết được tách thành tài liệu tham khảo:

- [EntityGraph deep-dive reference](ENTITY_GRAPH_DEEP_DIVE.md)

  Bao gồm primary key, foreign key, relationship/association, cascade, JPQL/HQL/SQL, SQL AST, hydration, Bag, `PersistentBag`, `MultipleBagFetchException`, metrics chi tiết, pagination internals và cấu hình demo runner.

- [Từ JPQL đến Database và trở lại Java](JPQL_TO_DATABASE_FLOW.md)

  Flow đầy đủ `HTTP → Controller → Transaction → Repository → JPQL → SQM → SQL AST → JDBC → Database → ResultSet → hydration → Persistence Context → response`.

Source code chính:

- [EntityGraphDemoController](src/main/java/com/example/entitygraph/controller/EntityGraphDemoController.java)
- [AuthorService](src/main/java/com/example/entitygraph/service/AuthorService.java)
- [AuthorRepository](src/main/java/com/example/entitygraph/repository/AuthorRepository.java)
- [Author entity và NamedEntityGraphs](src/main/java/com/example/entitygraph/entity/Author.java)
