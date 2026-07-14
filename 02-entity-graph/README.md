# Module 02: EntityGraph

Module này demo cách dùng JPA `@EntityGraph` để xử lý N+1 problem trong Spring Data JPA.

Thông điệp chính:

```text
Repository method / JPQL query quyết định: lấy Author nào?
EntityGraph quyết định: khi lấy Author thì load kèm association nào?
```

Nói ngắn gọn: EntityGraph là cách nói với Hibernate rằng:

```text
Riêng lần query này, đừng để lazy nữa. Hãy load trước các quan hệ này luôn.
```

Trong module này, domain model là:

```text
Author 1 -- N Book N -- 1 Publisher
```

Data demo:

```text
5 authors
15 books
5 publishers
```

## Cách Chạy Demo

Từ root project:

```powershell
.\gradlew.bat :02-entity-graph:bootRun
```

Nếu dùng Git Bash / Linux / macOS:

```bash
./gradlew :02-entity-graph:bootRun
```

Khi chạy, đọc log theo thứ tự:

```text
[PROBLEM] N+1 Queries
[1] Dynamic @EntityGraph
[2] Named @EntityGraph
[3] EntityGraph on a derived query
[4] EntityGraph on findById
[5] EntityGraph type: FETCH vs LOAD
[6] Nested EntityGraph
[7] JPQL @Query + EntityGraph
[8] Programmatic EntityGraph
[9] Named graph through EntityManager hint
```

Trong log SQL:

```text
Nhiều câu "where author_id=?" lặp lại  -> N+1
Một câu "left join books"              -> load Author kèm Books
Một câu "left join publishers"         -> load sâu hơn tới Publisher
```

Mỗi scenario cũng in thêm block `[METRICS]`:

```text
[METRICS] dynamic EntityGraph: authors + books
    elapsed:              4 ms
    JDBC statements:      1
    entities loaded:      20
    collections loaded:   5
    collections fetched:  0
```

Ý nghĩa:

| Metric | Ý nghĩa | Cách đọc |
|--------|---------|----------|
| `elapsed` | Thời gian chạy scenario | Số nhỏ hơn thường tốt hơn, nhưng demo local rất nhỏ nên chỉ tham khảo |
| `JDBC statements` | Số câu SQL/JDBC đã prepare | Đây là chỉ số rõ nhất để thấy N+1 |
| `entities loaded` | Số entity Hibernate load vào persistence context | Cao hơn nghĩa là object graph nặng hơn |
| `collections loaded` | Số collection được load | Ví dụ 5 authors thì 5 collection `books` |
| `collections fetched` | Số collection phải fetch riêng vì lazy access | Baseline N+1 thường thấy số này tăng |

Khi so sánh performance, ưu tiên đọc theo thứ tự:

```text
1. JDBC statements
2. entities loaded / collections loaded
3. elapsed time
```

Với demo nhỏ, tập trung vào SQL và số entity/collection để giải thích fetch behavior. Không dùng một lần chạy local để kết luận benchmark production.

## Phân Tích Log Thực Tế

Log thực tế được chạy từ IntelliJ với Java 17 và option:

```text
-XX:TieredStopAtLevel=1
```

Option này giúp startup/dev run nhanh hơn nhưng cũng làm benchmark thời gian không đại diện cho production. Vì vậy trong bảng dưới đây, `elapsed` chỉ dùng để quan sát tương đối trong một lần chạy demo, không dùng để kết luận tuyệt đối.

Kết quả `[METRICS]` từ log:

| Scenario | JDBC statements | Entities loaded | Collections loaded | Collections fetched | Elapsed |
|----------|-----------------|-----------------|--------------------|---------------------|---------|
| Baseline N+1: `findAll()` + lazy `getBooks()` | 6 | 20 | 5 | 5 | 6173 KB | 91 ms |
| Dynamic EntityGraph: authors + books | 1 | 20 | 5 | 0 | 994 KB | 23 ms |
| Named EntityGraph: `Author.withBooks` | 1 | 20 | 5 | 0 | 0 KB | 6 ms |
| Derived query + named EntityGraph | 1 | 8 | 2 | 0 | 1024 KB | 20 ms |
| `findById` override + named EntityGraph | 1 | 4 | 1 | 0 | 0 KB | 9 ms |
| `FETCH` graph type | 1 | 20 | 5 | 0 | 0 KB | 4 ms |
| `LOAD` graph type | 1 | 20 | 5 | 0 | 0 KB | 4 ms |
| Dynamic nested graph: books + publisher | 1 | 25 | 5 | 0 | 0 KB | 6 ms |
| Named nested graph: `Author.withBooksAndPublisher` | 1 | 25 | 5 | 0 | 0 KB | 3 ms |
| JPQL `@Query` + named EntityGraph | 1 | 12 | 3 | 0 | 1024 KB | 9 ms |
| Programmatic fetchgraph hint | 1 | 4 | 1 | 0 | 0 KB | 2 ms |
| EntityManager query hint + named graph | 1 | 8 | 2 | 0 | 1024 KB | 4 ms |

### Kết Luận Từ Log

#### 1. Baseline N+1 là case tệ nhất về số query

Baseline:

```text
JDBC statements:     6
collections fetched: 5
```

Ý nghĩa:

```text
1 query lấy authors
5 query lazy fetch books cho 5 authors
```

Đây chính là N+1. Dấu hiệu rõ nhất không phải `elapsed`, mà là:

```text
collections fetched = 5
JDBC statements = 6
```

Khi data tăng lên 100 authors, pattern này có thể thành:

```text
JDBC statements = 101
collections fetched = 100
```

#### 2. Dynamic và Named EntityGraph giải quyết cùng một vấn đề

Dynamic graph:

```text
JDBC statements:     1
entities loaded:     20
collections loaded:  5
collections fetched: 0
```

Named graph:

```text
JDBC statements:     1
entities loaded:     20
collections loaded:  5
collections fetched: 0
```

Hai case này gần như giống nhau về bản chất runtime:

```text
5 Author
15 Book
= 20 entities

5 Author.books collections
= 5 collections loaded
```

Điểm khác nhau không nằm ở performance trong demo này. Điểm khác nằm ở cách tổ chức code:

```text
Dynamic graph: ghi trực tiếp attributePaths = {"books"}.
Named graph: gọi lại fetch plan tên Author.withBooks.
```

#### 3. Derived query load ít entity hơn vì filter chỉ match 2 authors

Derived query:

```text
JDBC statements:     1
entities loaded:     8
collections loaded:  2
collections fetched: 0
```

Lý do:

```text
findByNameContainingIgnoreCaseOrderById("George")
```

match 2 authors:

```text
George R.R. Martin
George Orwell
```

Mỗi author có 3 books:

```text
2 Author + 6 Book = 8 entities
2 Author.books collections = 2 collections loaded
```

Điểm đáng chú ý:

```text
collections fetched = 0
```

Tức là dù không viết JPQL `JOIN FETCH`, EntityGraph vẫn giúp load books ngay từ query đầu.

#### 4. `findById` và programmatic graph có số entity thấp vì chỉ lấy 1 author

`findById`:

```text
entities loaded:     4
collections loaded:  1
```

Programmatic graph:

```text
entities loaded:     4
collections loaded:  1
```

Lý do:

```text
1 Author + 3 Book = 4 entities
1 Author.books collection = 1 collection loaded
```

Đây là pattern hợp cho detail screen:

```text
GET /authors/1
```

#### 5. `FETCH` và `LOAD` đang giống nhau trong demo này

Log:

```text
FETCH graph: JDBC statements = 1, entities loaded = 20
LOAD graph:  JDBC statements = 1, entities loaded = 20
```

Trong demo hiện tại, các association khác đều đang `LAZY`, nên `FETCH` và `LOAD` không khác rõ về SQL.

Khác biệt sẽ rõ hơn nếu entity có association mặc định `EAGER`.

#### 6. Nested graph vẫn 1 query nhưng object graph nặng hơn

Nested graph:

```text
JDBC statements:     1
entities loaded:     25
collections loaded:  5
collections fetched: 0
```

Vì nested graph fetch:

```text
Author -> books -> publisher
```

Data được load:

```text
5 Author
15 Book
5 Publisher
= 25 entities
```

So với `Author.withBooks`:

```text
20 entities
```

Nested graph tăng lên:

```text
+5 Publisher entities
```

Đây là lý do nested graph cần dùng có chủ đích. Nó vẫn giảm query, nhưng load nhiều object hơn.

#### 7. JPQL + EntityGraph load đúng số row theo điều kiện query

JPQL + graph:

```text
JDBC statements:     1
entities loaded:     12
collections loaded:  3
```

Query demo:

```java
searchWithQueryAndEntityGraph("George", 4L)
```

Điều kiện là:

```text
name chứa "George"
OR id > 4
```

Kết quả gồm 3 authors:

```text
George R.R. Martin
George Orwell
Alexandre Dumas
```

Mỗi author có 3 books:

```text
3 Author + 9 Book = 12 entities
3 collections loaded
```

#### 8. Metric tập trung vào fetch behavior

Với demo này, kết luận đáng tin nhất là:

```text
JDBC statements và collections fetched cho thấy N+1 hay không.
entities loaded và collections loaded cho thấy object graph được hydrate như thế nào.
```

## Vì Sao Có N+1?

Trong `Author`, relation `books` đang để `LAZY`:

```java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private List<Book> books = new ArrayList<>();
```

`LAZY` nghĩa là:

```text
Khi query Author, Hibernate chưa lấy books.
Chỉ khi code đụng tới author.getBooks() thì Hibernate mới query books.
```

Baseline demo:

```java
List<Author> authors = authorRepository.findAll();

for (Author author : authors) {
    author.getBooks().size();
}
```

SQL bản chất:

```sql
-- Query 1: lấy tất cả authors
select a.id, a.name
from authors a;

-- Query 2..6: mỗi author lại query books riêng
select b.*
from books b
where b.author_id = ?;
```

Với 5 authors:

```text
1 query lấy authors
5 query lấy books của từng author
= 6 queries
```

Với 100 authors:

```text
1 + 100 = 101 queries
```

Đây là N+1 problem.

## EntityGraph Giải Quyết Như Thế Nào?

EntityGraph không bắt mình đổi mapping mặc định của entity. `Author.books` vẫn có thể giữ `LAZY`.

Nhưng với từng query cụ thể, ta có thể nói:

```text
Query này cần Author + books.
Hãy load books ngay trong query này.
```

Hibernate thường sinh SQL dạng:

```sql
select a.*, b.*
from authors a
left join books b on a.id = b.author_id
order by a.id;
```

Lúc này khi Java gọi:

```java
author.getBooks().size();
```

Hibernate không cần bắn thêm query nữa, vì books đã được load trong cùng query đầu tiên.

## Tổng Quan Các Method Trong Demo

| Step | Method | Mục đích |
|------|--------|----------|
| 0 | `findAll()` | Tạo N+1 để đối chiếu |
| 1 | `findAllWithDynamicGraph()` | Dynamic graph: ghi trực tiếp field cần fetch |
| 2 | `findAllWithNamedGraph()` | Named graph: gọi lại fetch plan đã đặt tên |
| 3 | `findByNameContainingIgnoreCaseOrderById(...)` | Dùng EntityGraph với derived query, không cần JPQL |
| 4 | `findById(...)` | Override method có sẵn của `JpaRepository` |
| 5 | `findAllWithFetchGraphType()` / `findAllWithLoadGraphType()` | So sánh `FETCH` và `LOAD` graph |
| 6 | `findAllWithBooksAndPublisher...()` | Nested graph: Author -> Book -> Publisher |
| 7 | `searchWithQueryAndEntityGraph(...)` | JPQL query phức tạp + EntityGraph |
| 8 | `EntityManager#createEntityGraph(...)` | Tạo graph bằng code lúc runtime |
| 9 | `EntityManager#setHint(...)` | Dùng named graph qua JPA query hint |

## 1. Dynamic EntityGraph

Repository code:

```java
@EntityGraph(attributePaths = {"books"})
@Query("SELECT a FROM Author a ORDER BY a.id")
List<Author> findAllWithDynamicGraph();
```

Đọc code này như sau:

```text
@Query:
Lấy tất cả Author, sắp xếp theo id.

@EntityGraph(attributePaths = {"books"}):
Khi lấy Author, load kèm books.
```

SQL bản chất:

```sql
select
    a.id,
    a.name,
    b.id,
    b.title,
    b.publish_year,
    b.author_id
from authors a
left join books b on a.id = b.author_id
order by a.id;
```

Kết quả:

```text
1 query thay vì 1 + N queries.
```

Ưu điểm:

- Nhanh để viết.
- Nhìn ngay vào method là biết method này fetch thêm `books`.
- Hợp với graph đơn giản, ví dụ chỉ fetch `books`.

Nhược điểm:

- Nếu nhiều method cùng cần fetch `books`, bạn sẽ lặp lại `@EntityGraph(attributePaths = {"books"})`.
- Nếu graph dài hơn, ví dụ `books.publisher`, `books.categories`, code trên method sẽ dài và dễ lặp.

Khi nên dùng:

```text
Một vài method cần fetch đơn giản.
Muốn đọc code nhanh, không cần khai báo named graph riêng.
```

## 2. Named EntityGraph

Khai báo trên entity `Author`:

```java
@NamedEntityGraph(
    name = "Author.withBooks",
    attributeNodes = @NamedAttributeNode("books")
)
```

Repository code:

```java
@EntityGraph("Author.withBooks")
@Query("SELECT a FROM Author a ORDER BY a.id")
List<Author> findAllWithNamedGraph();
```

Đọc code này như sau:

```text
Author.withBooks = fetch plan đã đặt tên.
Nó có nghĩa là: lấy Author kèm books.
```

SQL bản chất gần giống dynamic graph:

```sql
select a.*, b.*
from authors a
left join books b on a.id = b.author_id
order by a.id;
```

Trong demo chỉ có mỗi `books`, dynamic và named graph gần như không khác về SQL/performance.

Khác nhau nằm ở cách tổ chức code:

```text
Dynamic graph:
Mỗi method tự ghi attributePaths.

Named graph:
Đặt tên fetch plan một lần, nhiều method gọi lại tên đó.
```

Ưu điểm:

- Có tên rõ ràng: `Author.withBooks`.
- Tốt khi fetch plan dài hoặc dùng lại nhiều nơi.
- Giảm lặp khi có nested graph.

Nhược điểm:

- Với graph quá đơn giản, named graph có vẻ hơi thừa.
- Khai báo graph trên entity có thể làm entity nhiều annotation hơn.

Khi nên dùng:

```text
Fetch plan có ý nghĩa nghiệp vụ rõ ràng:
Author.withBooks
Author.withBooksAndPublisher
Author.detailView
```

## 3. EntityGraph Trên Derived Query

Repository code:

```java
@EntityGraph("Author.withBooks")
List<Author> findByNameContainingIgnoreCaseOrderById(String name);
```

Đây là method quan trọng nhất để thấy EntityGraph khác JOIN FETCH.

Không có `@Query`. Spring Data tự đọc tên method:

```text
findByNameContainingIgnoreCaseOrderById("George")
```

Thành ý nghĩa:

```text
Tìm Author có name chứa "George"
Không phân biệt hoa thường
Order by id
```

EntityGraph thêm phần:

```text
Lấy kèm books.
```

SQL bản chất:

```sql
select
    a.*,
    b.*
from authors a
left join books b on a.id = b.author_id
where upper(a.name) like upper(?)
order by a.id;
```

Parameter:

```text
? = %George%
```

Data demo trả về:

```text
George R.R. Martin -> 3 books
George Orwell      -> 3 books
```

Ưu điểm:

- Không cần viết JPQL.
- Vẫn tránh được N+1.
- Rất hợp với Spring Data derived query.

Nhược điểm:

- Tên method có thể dài.
- Nếu điều kiện search quá phức tạp, derived query sẽ khó đọc.

So với JOIN FETCH:

JOIN FETCH phải viết:

```java
@Query("""
    SELECT a
    FROM Author a
    JOIN FETCH a.books
    WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :name, '%'))
    ORDER BY a.id
""")
List<Author> findByNameWithBooks(String name);
```

EntityGraph thì tách ra:

```java
@EntityGraph("Author.withBooks")
List<Author> findByNameContainingIgnoreCaseOrderById(String name);
```

Nghĩa là:

```text
Tên method quyết định điều kiện search.
EntityGraph quyết định fetch thêm books.
```

## 4. EntityGraph Trên `findById`

Repository code:

```java
@Override
@EntityGraph("Author.withBooks")
Optional<Author> findById(Long id);
```

Bình thường `findById(1L)` chỉ lấy Author. Khi gắn EntityGraph, nó lấy Author kèm books.

SQL bản chất:

```sql
select a.*, b.*
from authors a
left join books b on a.id = b.author_id
where a.id = ?;
```

Use case:

```text
Màn hình detail:
GET /authors/1

Cần:
- thông tin author
- danh sách books
```

Ưu điểm:

- Code gọi vẫn là `findById`.
- Hợp với detail page/API detail.

Nhược điểm:

- Tất cả chỗ nào gọi `findById` trong repository này đều fetch books.
- Nếu có chỗ chỉ cần `id`, `name`, việc fetch books là thừa.

Cẩn thận:

```text
Nếu findById được dùng ở nhiều use case khác nhau, dùng method riêng có tên rõ hơn có thể tốt hơn:

findByIdWithBooks(Long id)
```

## 5. `FETCH` Graph Vs `LOAD` Graph

Repository code:

```java
@EntityGraph(attributePaths = {"books"}, type = EntityGraph.EntityGraphType.FETCH)
@Query("SELECT a FROM Author a ORDER BY a.id")
List<Author> findAllWithFetchGraphType();

@EntityGraph(attributePaths = {"books"}, type = EntityGraph.EntityGraphType.LOAD)
@Query("SELECT a FROM Author a ORDER BY a.id")
List<Author> findAllWithLoadGraphType();
```

Mental model:

```text
FETCH graph:
Chỉ những field trong graph được ép load eager.
Những field khác sẽ bị xem là lazy, kể cả nếu mapping default của nó là EAGER.

LOAD graph:
Những field trong graph được ép load eager.
Những field khác giữ theo mapping mặc định trên entity.
```

Trong module này, các relation đều đang LAZY, nên SQL của `FETCH` và `LOAD` nhìn gần giống nhau:

```sql
select a.*, b.*
from authors a
left join books b on a.id = b.author_id
order by a.id;
```

Khi nào khác biệt rõ?

Giả sử `Book.publisher` được mapping EAGER:

```java
@ManyToOne(fetch = FetchType.EAGER)
private Publisher publisher;
```

Nếu dùng `FETCH` graph chỉ có `books`:

```text
Fetch books.
Publisher không nằm trong graph -> có thể không bị load eager theo graph.
```

Nếu dùng `LOAD` graph chỉ có `books`:

```text
Fetch books.
Publisher có mapping EAGER -> vẫn tôn trọng default, có thể được load.
```

Ưu điểm:

- Cho phép kiểm soát fetch behavior chi tiết hơn.

Nhược điểm:

- Dễ gây rối cho người mới học.
- Trong nhiều project, chỉ cần default type là đủ.

Khuyến nghị:

```text
Demo concept thì nên biết FETCH vs LOAD.
Code production thì dùng rõ ràng, comment nếu cần.
```

## 6. Nested EntityGraph: Author -> Book -> Publisher

Model:

```text
Author
  books
    publisher
```

Dynamic nested graph:

```java
@EntityGraph(attributePaths = {"books", "books.publisher"})
@Query("SELECT a FROM Author a ORDER BY a.id")
List<Author> findAllWithBooksAndPublisherDynamicGraph();
```

Named nested graph:

```java
@NamedEntityGraph(
    name = "Author.withBooksAndPublisher",
    attributeNodes = @NamedAttributeNode(value = "books", subgraph = "books.publisher"),
    subgraphs = @NamedSubgraph(
        name = "books.publisher",
        attributeNodes = @NamedAttributeNode("publisher")
    )
)
```

Repository:

```java
@EntityGraph("Author.withBooksAndPublisher")
@Query("SELECT a FROM Author a ORDER BY a.id")
List<Author> findAllWithBooksAndPublisherNamedGraph();
```

SQL bản chất:

```sql
select
    a.*,
    b.*,
    p.*
from authors a
left join books b on a.id = b.author_id
left join publishers p on p.id = b.publisher_id
order by a.id;
```

Nếu không nested graph:

```text
Query authors
Query books theo từng author
Khi dùng book.getPublisher(), lại query publisher tiếp
```

Nested graph giúp lấy cả 3 tầng trong 1 query.

Ưu điểm:

- Tránh N+1 ở nhiều tầng.
- Đây là ví dụ rõ nhất cho giá trị của Named EntityGraph.

Nhược điểm:

- Join sâu hơn -> SQL result có thể phình to.
- Nếu Author có nhiều Books, và mỗi Book join thêm nhiều bảng, row và column tăng nhanh.

Cẩn thận:

```text
Nested graph tốt khi bạn thực sự cần dữ liệu nested.
Không nên dùng nested graph cho API list lớn nếu UI chỉ cần tên author.
```

## 7. JPQL `@Query` + EntityGraph

Repository code:

```java
@EntityGraph("Author.withBooks")
@Query("""
    SELECT a
    FROM Author a
    WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
       OR a.id > :minId
    ORDER BY a.id
""")
List<Author> searchWithQueryAndEntityGraph(String keyword, Long minId);
```

Bài học:

```text
JPQL query lo phần điều kiện search.
EntityGraph lo phần fetch association.
```

SQL bản chất:

```sql
select a.*, b.*
from authors a
left join books b on a.id = b.author_id
where lower(a.name) like lower(?)
   or a.id > ?
order by a.id;
```

Trong demo:

```java
searchWithQueryAndEntityGraph("George", 4L)
```

Có thể trả về:

```text
George R.R. Martin
George Orwell
Alexandre Dumas -- vì id > 4
```

Ưu điểm:

- Dùng được với query phức tạp.
- Fetch plan vẫn tách khỏi JPQL.

Nhược điểm:

- Vẫn phải viết JPQL.
- Nếu query đã phức tạp mà graph cũng phức tạp, cần đọc SQL log cẩn thận.

## 8. Programmatic EntityGraph Bằng EntityManager

Service code:

```java
EntityGraph<Author> graph = entityManager.createEntityGraph(Author.class);
graph.addAttributeNodes("books");

Author author = entityManager.find(
    Author.class,
    2L,
    Map.of("jakarta.persistence.fetchgraph", graph)
);
```

Bài học:

```text
Không cần annotation.
Có thể tạo graph bằng code lúc runtime.
```

SQL bản chất:

```sql
select a.*, b.*
from authors a
left join books b on a.id = b.author_id
where a.id = ?;
```

Use case:

```text
Request có query param:
GET /authors/2?includeBooks=true

Nếu includeBooks=true  -> add books vào graph
Nếu includeBooks=false -> chỉ load Author
```

Ưu điểm:

- Linh hoạt nhất.
- Fetch plan có thể dựa trên runtime flags.

Nhược điểm:

- Code dài hơn annotation.
- Cần dùng `EntityManager`, không còn gọn như Spring Data repository.
- Dễ bị rối nếu tạo graph quá nhiều nơi.

## 9. Named Graph Qua EntityManager Hint

Service code:

```java
EntityGraph<?> graph = entityManager.getEntityGraph("Author.withBooks");

List<Author> authors = entityManager
    .createQuery("SELECT a FROM Author a WHERE a.name LIKE :name ORDER BY a.id", Author.class)
    .setParameter("name", "%George%")
    .setHint("jakarta.persistence.fetchgraph", graph)
    .getResultList();
```

Bài học:

```text
Named graph không chỉ dùng được qua @EntityGraph.
Nó cũng có thể gắn vào JPA query bằng setHint.
```

SQL bản chất:

```sql
select a.*, b.*
from authors a
left join books b on a.id = b.author_id
where a.name like ?
order by a.id;
```

Ưu điểm:

- Hữu ích trong custom repository.
- Dùng lại được named graph đã khai báo.

Nhược điểm:

- Verbose.
- Cần nhớ đúng key hint: `jakarta.persistence.fetchgraph`.

## EntityGraph Vs JOIN FETCH

### JOIN FETCH

Code:

```java
@Query("""
    SELECT a
    FROM Author a
    JOIN FETCH a.books
    WHERE a.name LIKE :name
""")
List<Author> findByNameWithBooks(String name);
```

Đọc là:

```text
JPQL vừa nói lấy Author nào,
vừa nói fetch books luôn.
```

### EntityGraph

Code:

```java
@EntityGraph("Author.withBooks")
List<Author> findByNameContainingIgnoreCaseOrderById(String name);
```

Đọc là:

```text
Tên method nói lấy Author nào.
EntityGraph nói load kèm association nào.
```

### So Sánh

| Tiêu chí | JOIN FETCH | EntityGraph |
|----------|------------|-------------|
| Nơi khai báo fetch | Trong JPQL | Ngoài query, bằng graph |
| Dùng với derived query | Không | Có |
| Dùng với `findById` | Phải viết query riêng | Có thể override |
| Reuse fetch plan | Kém hơn | Tốt với named graph |
| SQL sinh ra | Có thể giống EntityGraph | Có thể giống JOIN FETCH |
| Dễ đọc với query phức tạp | JPQL dài hơn | Query và fetch tách nhau |
| Cần thận pagination | Có | Có |

Kết luận:

```text
JOIN FETCH và EntityGraph có thể sinh SQL rất giống nhau.
Khác nhau lớn nhất là cách tổ chức code.
```

## EntityGraph Có Liên Quan Performance Không?

Có. EntityGraph là công cụ performance vì nó kiểm soát cách Hibernate load dữ liệu.

### Khi Dùng Đúng

Từ:

```text
1 + N queries
```

Thành:

```text
1 query join
```

Giảm round-trip tới database, thường nhanh hơn rõ rệt.

### Khi Dùng Sai

Nếu API chỉ cần:

```text
Author id
Author name
```

Nhưng graph lại fetch:

```text
Author -> books -> publisher
```

Thì bạn đang load thừa dữ liệu.

Ví dụ:

```text
1000 authors
mỗi author 20 books
```

Join query có thể trả về:

```text
20,000 rows
```

Trong khi chỉ lấy author thì chỉ cần:

```text
1000 rows
```

EntityGraph lúc này có thể làm chậm hơn.

## Lỗi Và Bẫy Thường Gặp

### 1. Pagination Với Collection Fetch

Đây là lỗi rất hay gặp.

Giả sử có:

```text
Author 1 có 10 books
Author 2 có 1 book
Author 3 có 1 book
```

Nếu query:

```java
@EntityGraph("Author.withBooks")
Page<Author> findAll(Pageable pageable);
```

SQL join có dạng:

```sql
select a.*, b.*
from authors a
left join books b on a.id = b.author_id
order by a.id
limit 10;
```

Vấn đề:

```text
Pagination đang cắt trên SQL rows, không phải cắt trên Author objects.

Author 1 có 10 books -> đã chiếm hết 10 rows.
Kết quả page có thể chỉ thấy Author 1.
```

Hibernate có thể phải xử lý pagination trong memory hoặc sinh warning tùy version/case.

Giải pháp an toàn hơn:

```text
Bước 1: Page Author IDs trước
Bước 2: Query authors + books theo id list
```

Ví dụ:

```java
@Query("SELECT a.id FROM Author a ORDER BY a.id")
Page<Long> findPageIds(Pageable pageable);

@EntityGraph("Author.withBooks")
List<Author> findByIdIn(List<Long> ids);
```

Hoặc cân nhắc:

```text
DTO projection
BatchSize
two-step query
```

### 2. Fetch Thừa Dữ Liệu

Sai:

```java
@EntityGraph("Author.withBooksAndPublisher")
List<Author> findAllForDropdown();
```

Nếu dropdown chỉ cần:

```text
id
name
```

Thì fetch books/publisher là thừa.

Tốt hơn:

```java
@Query("SELECT new ...AuthorOptionDto(a.id, a.name) FROM Author a")
List<AuthorOptionDto> findAuthorOptions();
```

### 3. Nhiều Collection Cùng Lúc

Giả sử Author có:

```text
books
awards
```

Nếu graph fetch cả hai:

```java
@EntityGraph(attributePaths = {"books", "awards"})
```

SQL có thể tạo row multiplication:

```text
1 author có 3 books và 4 awards
=> 3 x 4 = 12 rows cho 1 author
```

Nếu list có nhiều authors, result set phình rất nhanh.

Giải pháp:

```text
Fetch một collection chính bằng EntityGraph.
Collection còn lại dùng BatchSize hoặc query riêng.
```

### 4. Nested Graph Quá Sâu

Graph:

```text
Author -> books -> publisher -> country -> region
```

Có thể sinh SQL join rất lớn:

```sql
authors
left join books
left join publishers
left join countries
left join regions
```

Nhược điểm:

```text
Nhiều column
Nhiều row
Khó debug
Tăng memory
```

Chỉ nên nested graph khi UI/API thực sự cần đầy đủ dữ liệu đó.

### 5. Tưởng EntityGraph Luôn Nhanh Hơn

Sai.

EntityGraph nhanh hơn khi nó tránh N+1 và load đúng dữ liệu cần.

EntityGraph chậm hơn khi:

```text
Fetch thừa
Join quá nhiều bảng
Fetch collection lớn
Dùng với pagination không cẩn thận
```

## Khi Nào Nên Dùng Cách Nào?

| Nhu cầu | Gợi ý |
|--------|-------|
| Chỉ cần fix N+1 cho 1 method đơn giản | Dynamic EntityGraph |
| Nhiều method dùng chung fetch plan | Named EntityGraph |
| Dùng Spring Data derived query | EntityGraph trên derived query |
| Detail page cần `findById` kèm children | Override `findById` hoặc tạo `findByIdWithBooks` |
| Cần fetch nhiều tầng | Nested EntityGraph |
| Query phức tạp nhưng muốn tách fetch plan | JPQL `@Query` + EntityGraph |
| Fetch plan phụ thuộc runtime flag | Programmatic EntityGraph |
| Custom repository với EntityManager | Named graph + query hint |
| List lớn + pagination | Cẩn thận; cân nhắc two-step query / BatchSize / DTO |
| API read-only chỉ cần vài field | DTO Projection |

## Script Ngắn Để Thuyết Trình

Có thể nói như sau:

```text
Mặc định relation Author.books là LAZY. Khi query list Author, Hibernate chưa lấy books.
Nếu code loop qua từng author và gọi getBooks(), Hibernate sẽ query books từng author một.
Đó là N+1.

EntityGraph cho phép mình nói với Hibernate ở từng use case:
"Query này cần load thêm books."

Dynamic graph viết trực tiếp books trên repository method.
Named graph đặt tên fetch plan để tái sử dụng.
Derived query là điểm hay: mình không cần viết JPQL JOIN FETCH, Spring Data vẫn tự tạo query theo tên method, còn EntityGraph gắn thêm phần fetch books.

Tuy nhiên EntityGraph không phải lúc nào cũng nhanh.
Nó tốt khi tránh N+1 và load đúng dữ liệu cần.
Nó có thể tệ nếu fetch thừa, fetch collection quá lớn, nested graph quá sâu, hoặc dùng với pagination không cẩn thận.
```

## Tóm Tắt Cuối

```text
EntityGraph = per-query fetch plan.

Nó giúp:
- tránh N+1
- giữ entity LAZY mặc định
- tách điều kiện query khỏi fetch strategy
- dùng được với derived query, JPQL, findById, EntityManager

Nhưng cần cảnh giác:
- fetch thừa
- join phình row
- pagination với collection fetch
- nested graph quá sâu
```
