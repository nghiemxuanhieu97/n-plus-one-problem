# EntityGraph deep-dive reference

> Đây là tài liệu tra cứu chuyên sâu được tách khỏi flow thuyết trình chính.
> Quay lại [README — Problem → Solution → Demo → Best Practices](README.md).

Module này giải thích cách giải quyết N+1 bằng `EntityGraph`, nhưng mục tiêu quan trọng hơn là hiểu toàn bộ đường đi:

```text
HTTP request
    -> Controller
    -> Service + transaction
    -> Spring Data repository / EntityManager
    -> JPQL/HQL + EntityGraph fetch plan
    -> Hibernate query model + SQL AST
    -> SQL + JDBC parameters
    -> Database result rows
    -> Hibernate hydration
    -> Author object graph
    -> DTO / HTTP response
```

Tài liệu được viết để người mới có thể đọc từ đầu, sau đó mở Swagger và chạy từng scenario theo đúng thứ tự.

Tài liệu chuyên sâu về toàn bộ query lifecycle:

- [Từ JPQL đến Database và trở lại Java](JPQL_TO_DATABASE_FLOW.md)

## 1. Điều cần nhớ trước tiên

```text
JPQL/query quyết định lấy Author nào.
EntityGraph quyết định association nào cần được load cùng use case đó.
Hibernate quyết định SQL cụ thể để thực hiện query và fetch plan.
Database chỉ hiểu SQL, không hiểu JPQL, HQL hay EntityGraph.
```

`EntityGraph` là fetch plan, không phải lời cam kết rằng Hibernate luôn sinh đúng một câu `JOIN`.

## 2. Domain và dữ liệu demo

```text
Country   1 -------- N Author
Author    1 -------- N Book
Author    1 -------- N Award
Publisher 1 -------- N Book
```

Dữ liệu khởi tạo:

- 5 Authors.
- 15 Books, mỗi Author có 3 Books.
- 10 Awards, mỗi Author có 2 Awards.
- 5 Publishers.
- 3 Countries.

Các bảng và foreign key chính:

```text
authors.id              primary key
books.id                primary key
awards.id               primary key
countries.id            primary key
publishers.id           primary key

authors.country_id      -> countries.id
books.author_id         -> authors.id
books.publisher_id      -> publishers.id
awards.author_id        -> authors.id
```

`Author.country` được đặt `EAGER` có chủ đích để scenario `FETCH graph` và `LOAD graph` cho kết quả khác nhau. Đây là thiết kế phục vụ demo, không phải khuyến nghị để mọi `ManyToOne` trong production đều là `EAGER`.

## 3. Primary key, foreign key và relationship

### 3.1 Primary key

Primary key nhận diện duy nhất một row trong một table:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

Database bảo đảm `id` không trùng và không `NULL`. Với `IDENTITY`, database sinh ID khi `INSERT`, sau đó Hibernate gán ID đó trở lại entity Java.

### 3.2 Foreign key

Foreign key là column tham chiếu primary key của table khác. Ví dụ:

```text
books.author_id = 1
```

nghĩa là Book thuộc Author có `authors.id = 1`.

Database dùng foreign-key constraint để ngăn dữ liệu như `author_id = 999` khi Author 999 không tồn tại.

### 3.3 Relationship trong JPA

Hai phía của quan hệ Author–Book:

```java
// Author.java
@OneToMany(mappedBy = "author", fetch = FetchType.LAZY)
private List<Book> books;
```

```java
// Book.java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "author_id")
private Author author;
```

`Book.author` là owning side vì nó giữ `@JoinColumn` và quyết định giá trị `books.author_id`.

`Author.books` là inverse side. `mappedBy = "author"` nói rằng relationship đã được quản lý bởi field `Book.author`; Hibernate không cần tạo thêm join table hay foreign key thứ hai.

Khi xây dựng quan hệ hai chiều, phải giữ hai phía đồng bộ:

```java
author.getBooks().add(book);
book.setAuthor(author);
```

Database cuối cùng chỉ lưu foreign key ở `books.author_id`; collection `author.books` là object view mà Hibernate xây dựng từ các rows đó.

### 3.4 Association khác Relationship như thế nào?

Hai từ này thường được dùng thay nhau trong giao tiếp hằng ngày, nhưng có thể phân biệt chính xác hơn:

```text
Relationship
= sự thật/mối quan hệ giữa hai khái niệm hoặc hai nhóm dữ liệu.

Association
= cách object/entity model biểu diễn và cho phép điều hướng relationship đó.
```

Ví dụ domain có relationship:

```text
Một Author viết nhiều Books.
Mỗi Book thuộc một Author.
```

Ở database, relationship được hiện thực bằng foreign key:

```text
books.author_id -> authors.id
```

Ở Java/JPA, relationship được biểu diễn bằng các associations:

```java
// Association đi từ Author sang Books
@OneToMany(mappedBy = "author")
private List<Book> books;

// Association đi từ Book sang Author
@ManyToOne
@JoinColumn(name = "author_id")
private Author author;
```

Có thể hình dung ba tầng:

```text
Domain relationship:
Author 1 --- N Book

Database representation:
books.author_id references authors.id

JPA associations:
Author.books
Book.author
```

`Author.books` và `Book.author` là hai association ends/hướng điều hướng của cùng một relationship Author–Book:

```text
author.getBooks() -> đi từ Author sang Books
book.getAuthor()  -> đi từ Book sang Author
```

Nếu entity chỉ có:

```java
@ManyToOne
private Author author;
```

mà Author không có `List<Book>`, relationship trong database vẫn tồn tại, nhưng object model chỉ có association một chiều:

```text
Book -> Author       đi được
Author -> Books      không đi trực tiếp được
```

Khi có cả hai fields, đây là bidirectional association. `mappedBy` cho Hibernate biết hai hướng này mô tả cùng một foreign-key relationship, không phải hai relationships độc lập.

| Khái niệm | Câu hỏi nó trả lời | Ví dụ |
|---|---|---|
| Relationship | Hai loại dữ liệu liên quan với nhau thế nào? | Một Author có nhiều Books |
| Foreign key | Database lưu/bảo vệ quan hệ bằng gì? | `books.author_id` |
| Association | Entity/object điều hướng quan hệ bằng field nào? | `Author.books`, `Book.author` |
| Cardinality | Mỗi phía có bao nhiêu phần tử? | `OneToMany`, `ManyToOne` |

Các thiết lập như `fetch`, `cascade`, owning side và `mappedBy` thuộc về association mapping trong JPA. Chúng hướng dẫn Hibernate cách load và quản lý object relationship; chúng không thay đổi sự thật domain rằng Book thuộc Author.

Cách nhớ ngắn:

```text
Relationship là mối quan hệ.
Association là đường nối trong object model để biểu diễn/đi theo mối quan hệ đó.
Foreign key hoặc join table là cách database lưu đường nối.
```

## 4. Cascade và orphan removal

`cascade` trả lời câu hỏi:

> Khi một EntityManager operation được thực hiện trên Author, operation đó có lan xuống Books và Awards không?

Các loại cascade chuẩn JPA:

| Loại | Ý nghĩa |
|---|---|
| `PERSIST` | Persist cha thì persist con |
| `MERGE` | Merge cha thì merge con |
| `REMOVE` | Remove cha thì remove con |
| `REFRESH` | Refresh cha thì refresh con |
| `DETACH` | Detach cha thì detach con |
| `ALL` | Bao gồm tất cả loại trên |

Mapping hiện tại:

```java
@OneToMany(
    mappedBy = "author",
    cascade = CascadeType.ALL,
    orphanRemoval = true,
    fetch = FetchType.LAZY
)
private List<Book> books;
```

Khi `authorRepository.save(newAuthor)` được gọi, Spring Data nhận ra entity mới và dùng `EntityManager.persist(author)`. Vì `ALL` bao gồm `PERSIST`, Hibernate tiếp tục persist Books và Awards trong collection:

```text
persist(author)
    -> persist(book 1)
    -> persist(book 2)
    -> persist(book 3)
    -> persist(award 1)
    -> persist(award 2)
```

Điều này giải thích vì sao `DataInitializer` chỉ cần save Author sau khi đã gắn children đúng hai phía.

Ba khái niệm không được trộn với nhau:

```text
cascade -> operation persist/merge/remove có lan không
fetch   -> lúc đọc entity có load association ngay không
FK      -> database bảo vệ liên kết giữa các tables
```

`orphanRemoval = true` nghĩa là nếu một managed Book bị loại khỏi `author.books`, Hibernate có thể `DELETE` Book đó khi flush.

`CascadeType.REMOVE` lan thao tác khi xóa Author. Database `ON DELETE CASCADE` là một cơ chế khác, do database thực thi.

## 5. SQL, JPQL, HQL và native SQL

### 5.1 SQL

SQL làm việc trực tiếp với table và column:

```sql
SELECT a.id, a.name
FROM authors a
WHERE a.id > ?
ORDER BY a.id;
```

H2, PostgreSQL hoặc MySQL hiểu SQL. Database không biết Java entity `Author` là gì.

### 5.2 JPQL

JPQL là Jakarta Persistence Query Language. Nó query entity và Java field:

```jpql
SELECT a
FROM Author a
WHERE a.id > :minId
ORDER BY a.id
```

Khác biệt:

```text
JPQL: Author, a.name, a.books
SQL:  authors, name, books.author_id
```

Hibernate đọc mapping:

```java
@Entity
@Table(name = "authors")
class Author { ... }
```

rồi biết `Author` phải được dịch thành table `authors`.

Database không bao giờ nhận `SELECT a FROM Author a`. Nó nhận SQL do Hibernate tạo.

### 5.3 HQL

HQL là Hibernate Query Language:

```text
JPQL = ngôn ngữ query entity theo chuẩn Jakarta Persistence.
HQL  = ngôn ngữ của Hibernate, tương thích phần lớn JPQL và có thêm extension.
```

Một query đơn giản thường vừa là JPQL hợp lệ vừa là HQL hợp lệ, nên hai thuật ngữ hay được dùng lẫn trong project Hibernate. Nên ưu tiên cú pháp JPQL chuẩn nếu không cần tính năng riêng của Hibernate.

### 5.4 `@Query`, `Query` và `TypedQuery`

`@Query` là annotation Spring Data chứa query:

```java
@Query("SELECT a FROM Author a WHERE a.id > :minId")
List<Author> search(@Param("minId") Long minId);
```

JPA cũng có API runtime:

```java
TypedQuery<Author> query = entityManager.createQuery(
    "SELECT a FROM Author a WHERE a.id = :id",
    Author.class
);
```

`TypedQuery<Author>` cho compiler biết result là `Author`. `Query` không mang result type cụ thể.

### 5.5 Native SQL

Nếu dùng:

```java
@Query(value = "SELECT * FROM authors WHERE id > :minId", nativeQuery = true)
```

thì developer tự viết SQL. Hibernate không cần dịch `Author` thành `authors`, nhưng vẫn bind parameters, gọi JDBC, đọc result set và map rows về entity/DTO.

### 5.6 Flow dịch query xuống database

```text
repository method được gọi
    -> Spring Data đọc @Query hoặc derived-method name
    -> Hibernate parse JPQL/HQL
    -> kiểm tra entity và attributes
    -> kết hợp EntityGraph nếu có
    -> tạo semantic query model
    -> tạo SQL AST
    -> database dialect render SQL
    -> JDBC gửi SQL và bind parameters
    -> database chạy SQL
    -> Hibernate hydrate result rows thành objects
```

Một JPQL không bắt buộc tương ứng đúng một SQL. Lazy loading, pagination count query, secondary selects và fetch strategy có thể làm phát sinh thêm statements.

## 6. SQL AST là gì?

AST là Abstract Syntax Tree. Hibernate không chỉ thay chuỗi `Author` thành `authors`; nó xây một tree nội bộ gần giống:

```text
SelectStatement
    From: authors a
    LeftJoin: books b
        Condition: b.author_id = a.id
    Predicate: a.id > parameter
    OrderBy: a.id
    Selections:
        author columns
        book columns
```

Dialect sau đó render SQL AST thành cú pháp phù hợp database, ví dụ `LIMIT/OFFSET`. SQL AST cũng là nơi Hibernate kết hợp query predicates, joins do graph yêu cầu, pagination và column selections.

## 7. Hibernate hydration: từ 15 rows thành object graph

Fetch 5 Authors, mỗi Author có 3 Books, database trả về 15 rows phẳng:

```text
A1 - B1
A1 - B2
A1 - B3
A2 - B4
A2 - B5
A2 - B6
...
```

Database không trả một JSON/object tree. Hibernate đọc từng row và dùng Persistence Context như identity map:

```text
(Author, 1) -> Author object A1
(Book, 1)   -> Book object B1
(Book, 2)   -> Book object B2
```

Khi Author ID 1 lặp lại ở row thứ hai, Hibernate dùng lại đúng A1 thay vì tạo một Author mới. Book ID mới được tạo thành Book object rồi thêm vào collection đúng owner.

Kết quả:

```text
15 flat SQL rows
    -> 5 Author objects
    -> 15 Book objects
    -> 5 initialized Author.books collections
```

Quá trình đọc rows, tạo/reuse entity và gắn association này được gọi là hydration/materialization.

## 8. Semantics, Bag và PersistentBag

### 8.1 “Semantics” nghĩa là gì?

Trong tài liệu này, `semantics` có thể hiểu là:

> Ý nghĩa và bộ quy tắc mà Hibernate phải bảo toàn.

Collection semantics trả lời các câu hỏi:

```text
Collection có cho phép duplicate không?
Collection có persistent index/vị trí không?
Hai phần tử được xem là giống nhau khi nào?
Hibernate có được phép loại phần tử lặp không?
```

| Loại collection | Cho duplicate | Có persistent index |
|---|---:|---:|
| Bag | Có | Không |
| Set | Không | Không |
| Indexed List | Có | Có |

### 8.2 Bag chính xác là gì?

Bag là collection:

```text
cho phép duplicate
nhưng không lưu index/vị trí của từng lần xuất hiện
```

Java Collections Framework không có interface `Bag`. Đây là cách Hibernate phân loại mapping.

Mapping hiện tại:

```java
@OneToMany(mappedBy = "author")
private List<Book> books;
```

không có `@OrderColumn`, vì vậy mặc định được Hibernate xử lý với Bag semantics:

```text
Java declaration:       List<Book>
Hibernate semantics:    BAG
Runtime wrapper:        PersistentBag
```

Không phải Book thiếu ID. Hai khái niệm hoàn toàn khác nhau:

```text
Book.id       -> đây là Book entity nào?
book_position -> Book nằm ở vị trí nào trong collection?
```

`Book.id` không làm collection hết là Bag. Muốn có indexed-list semantics, cần lưu vị trí collection:

```java
@OrderColumn(name = "book_position")
private List<Book> books;
```

`@OrderBy("title ASC")` chỉ sort khi đọc; nó không lưu index và không tương đương `@OrderColumn`.

### 8.3 `PersistentBag` là gì?

Trong source code, object mới bắt đầu bằng:

```java
private List<Book> books = new ArrayList<>();
```

Khi Author được Hibernate quản lý, Hibernate thường thay collection bằng `PersistentBag`. Wrapper này theo dõi:

- Collection đã initialized chưa.
- Collection thuộc Author nào.
- Hibernate Session nào đang quản lý.
- Snapshot ban đầu để dirty checking.
- Phần tử nào được thêm hoặc xóa.
- Có cần sinh `INSERT`, `UPDATE`, `DELETE` khi flush không.

Với Books `LAZY`:

```text
find Author
    -> author.books = PersistentBag(uninitialized)

author.getBooks().size()
    -> SELECT books WHERE author_id = ?
    -> PersistentBag(initialized)
```

`Bag` là semantics; `PersistentBag` là implementation runtime Hibernate dùng để quản lý semantics đó.

## 9. MultipleBagFetchException và row multiplication

### 9.1 Duplicate thật và duplicate do JOIN

Giả sử dữ liệu thật của Author A1:

```text
Books  = [B1, B2, B3]
Awards = [W1, W2]
```

Join hai ToMany collections sinh Cartesian combinations:

| Author | Book | Award |
|---|---|---|
| A1 | B1 | W1 |
| A1 | B1 | W2 |
| A1 | B2 | W1 |
| A1 | B2 | W2 |
| A1 | B3 | W1 |
| A1 | B3 | W2 |

Nhìn riêng từng cột:

```text
Books:  B1, B1, B2, B2, B3, B3
Awards: W1, W2, W1, W2, W1, W2
```

Database trả sáu rows, nhưng dữ liệu domain chỉ có ba Books và hai Awards.

### 9.2 Vì sao hai Bags bị chặn?

Nếu mapping là:

```java
List<Book> books;    // Bag
List<Award> awards;  // Bag
```

cả hai collections đều:

- Cho phép duplicate.
- Không có persistent index.
- Không cho Hibernate tự ý loại duplicate mà không thay đổi collection semantics.

Hibernate áp dụng quy tắc an toàn tổng quát và từ chối fetch đồng thời hai bags:

```text
MultipleBagFetchException:
cannot simultaneously fetch multiple bags
```

Hibernate biết hai SQL rows chứa cùng `Book.id`; điều nó không có là identity cho từng lần Book xuất hiện trong Bag. Entity identity và collection-membership identity là hai thứ khác nhau.

Với đúng `OneToMany` trong demo, database thực tế không thể lưu cùng một Book row hai lần cho một Author. Tuy nhiên Hibernate vẫn phân loại mapping theo Bag semantics và áp dụng validation tổng quát khi xây fetch plan, thay vì suy luận riêng từ dữ liệu hiện tại.

### 9.3 Vì sao `Set` tránh exception?

Module dùng:

```java
List<Book> books;   // Bag
Set<Award> awards;  // Set
```

Set không cho duplicate:

```java
awards.add(W1); // thêm
awards.add(W2); // thêm
awards.add(W1); // đã có, bỏ qua
awards.add(W2); // đã có, bỏ qua
```

Vì chỉ còn một Bag, Hibernate không ném `MultipleBagFetchException`.

Nhưng Set chỉ tránh exception, không tránh Cartesian multiplication. Endpoint hiện tại cho thấy:

```text
3 distinct Books x 2 Awards = 6 SQL rows/Author
hydrated List<Book> size     = 6
```

`Set<Award>` loại Awards lặp, nhưng `List<Book>` vẫn có thể nhận sáu references do join.

Với 5 Authors:

```text
5 x 3 Books x 2 Awards = 30 joined rows
```

### 9.4 Cách tái hiện `MultipleBagFetchException`

Tạm đổi:

```java
private Set<Award> awards = new HashSet<>();
```

thành:

```java
private List<Award> awards = new ArrayList<>();
```

Sau đó restart và gọi:

```http
GET /api/entity-graph/11-multiple-collections
```

Graph `Author.withBooksAndAwards` sẽ chứa hai Bags và Hibernate có thể ném exception trước khi gửi SQL. Sau khi quan sát, đổi Awards trở lại `Set` để tiếp tục demo row multiplication.

Không nên đổi List thành Set trong production chỉ để né exception nếu domain thực sự cần thứ tự hoặc duplicate. Các giải pháp khác gồm tách query, BatchSize, subselect fetching hoặc DTO.

## 10. EntityGraph là gì?

EntityGraph mô tả attributes/associations cần được initialized cho một use case cụ thể mà không phải đổi mapping LAZY toàn cục.

Ví dụ:

```java
@EntityGraph(attributePaths = "books")
@Query("SELECT a FROM Author a ORDER BY a.id")
List<Author> findAllWithDynamicGraph();
```

Query quyết định lấy tất cả Authors theo ID. Graph yêu cầu Books phải được load trong cùng fetch plan.

Hibernate thường sinh SQL có `LEFT JOIN books`, nhưng chuẩn EntityGraph chỉ nói dữ liệu nào phải available; provider vẫn có thể dùng secondary selects.

## 11. Các phương pháp, SQL và metrics

Các SQL bên dưới là dạng rút gọn tương đương để dễ đọc. Hibernate có thể đổi alias như `a1_0`, thứ tự columns và cú pháp pagination theo database dialect.

Metrics giả định dataset mặc định và `DemoMetrics` đã `entityManager.clear()` rồi reset Hibernate Statistics trước từng block. Không học thuộc `elapsed`; chỉ bốn metric sau ổn định cho demo:

| Scenario | JDBC | Entities | Collections loaded | Collections fetched |
|---|---:|---:|---:|---:|
| Baseline N+1 | 9 | 23 | 5 | 5 |
| Dynamic graph | 1 | 20 | 5 | 0 |
| Named graph | 1 | 20 | 5 | 0 |
| Derived query | 1 | 8 | 2 | 0 |
| `findById` graph | 1 | 4 | 1 | 0 |
| Nested dynamic | 1 | 25 | 5 | 0 |
| Nested named | 1 | 25 | 5 | 0 |
| JPQL + graph | 1 | 12 | 3 | 0 |
| FETCH graph | 1 | 20 | 5 | 0 |
| LOAD graph | 4 | 23 | 5 | 0 |
| Programmatic graph | 1 | 4 | 1 | 0 |
| Books + Awards | 1 | 30 | 10 | 0 |
| Pagination ToOne | 2 | 3 | 0 | 0 |
| Pagination ToMany | 2 | 20 | 5 | 0 |
| Two-step pagination | 3 | 8 | 2 | 0 |

### 11.1 Baseline N+1

Endpoint:

```http
GET /api/entity-graph/01-baseline-n-plus-one
```

Code:

```java
List<Author> authors = repository.findAll();
for (Author author : authors) {
    author.getBooks().size();
}
```

SQL rút gọn:

```sql
-- Query 1: lấy 5 Authors
SELECT a.id, a.name
FROM authors a
ORDER BY a.id;

-- Query 2..4: Author.country là EAGER, ba Countries được load
SELECT c.* FROM countries c WHERE c.id = 1;
SELECT c.* FROM countries c WHERE c.id = 2;
SELECT c.* FROM countries c WHERE c.id = 3;

-- Query 5..9: mỗi Author trigger một lazy collection fetch
SELECT b.* FROM books b WHERE b.author_id = 1;
SELECT b.* FROM books b WHERE b.author_id = 2;
SELECT b.* FROM books b WHERE b.author_id = 3;
SELECT b.* FROM books b WHERE b.author_id = 4;
SELECT b.* FROM books b WHERE b.author_id = 5;
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
5 Authors + 3 Countries + 15 Books = 23 entities
5 Authors             = 5 Books collection instances
5 lazy SELECTs        = 5 collection fetch events
```

`findAll()` giữ mapping EAGER của `Author.country`, nên Hibernate hiện load thêm 3 Countries:

```text
1 Author query + 3 Country queries + 5 Book queries = 9 JDBC
5 Authors + 3 Countries + 15 Books                  = 23 entities
```

Country là `ManyToOne`, không phải collection, nên collection metrics vẫn là `5/5`.

N+1 không làm sai dữ liệu; nó tạo quá nhiều round trips vì truy cập LAZY collection trong vòng lặp.

### 11.2 Dynamic EntityGraph

Endpoint:

```http
GET /api/entity-graph/02-dynamic-graph
```

Repository:

```java
@EntityGraph(attributePaths = "books")
@Query("SELECT a FROM Author a ORDER BY a.id")
List<Author> findAllWithDynamicGraph();
```

SQL rút gọn:

```sql
SELECT a.id, a.name,
       b.id, b.author_id, b.title, b.publish_year, b.publisher_id
FROM authors a
LEFT JOIN books b ON b.author_id = a.id
ORDER BY a.id;
```

Database trả 15 joined rows; Hibernate hydrate thành 5 Authors và 15 Books.

Metrics:

```text
JDBC statements:      1
entities loaded:      20
collections loaded:   5
collections fetched:  0
```

`collections loaded=5` vì năm `Author.books` đã initialized. `collections fetched=0` vì Books đến từ root joined query, không có collection SELECT riêng.

Điểm nói khi demo: từ `6` JDBC xuống `1`, nhưng object graph vẫn là `5 Authors + 15 Books`.

### 11.3 Named EntityGraph

Endpoint:

```http
GET /api/entity-graph/03-named-graph
```

Khai báo và sử dụng:

```java
@NamedEntityGraph(
    name = "Author.withBooks",
    attributeNodes = @NamedAttributeNode("books")
)

@EntityGraph("Author.withBooks")
List<Author> findAllWithNamedGraph();
```

`@NamedEntityGraphs` số nhiều chỉ là container gom nhiều graph độc lập:

```text
@NamedEntityGraphs
├── Author.withBooks
├── Author.withBooksAndPublisher
└── Author.withBooksAndAwards
```

Repository chọn một graph bằng tên; Hibernate không tự gộp cả ba. `@NamedSubgraph` mới là nhánh con bên trong một graph, ví dụ `Author -> Books -> Publisher`.

SQL và metrics giống Dynamic graph:

```sql
SELECT a.id, a.name, b.*
FROM authors a
LEFT JOIN books b ON b.author_id = a.id
ORDER BY a.id;
```

```text
JDBC statements:      1
entities loaded:      20
collections loaded:   5
collections fetched:  0
```

Không có Country trong metrics vì Spring Data `@EntityGraph` mặc định là `FETCH`. Country nằm ngoài `Author.withBooks` nên bị xem như LAZY cho operation này, dù mapping gốc là EAGER.

Named graph không nhanh hơn Dynamic graph trong ví dụ đơn giản. Lợi ích là đặt tên và tái sử dụng fetch plan, đặc biệt khi có subgraphs.

### 11.4 Derived query + EntityGraph

Endpoint:

```http
GET /api/entity-graph/04-derived-query
```

Repository:

```java
@EntityGraph("Author.withBooks")
List<Author> findByNameContainingIgnoreCaseOrderById(String name);
```

Service truyền `"George"`, nên Spring Data tạo điều kiện tương đương:

```sql
SELECT a.id, a.name, b.*
FROM authors a
LEFT JOIN books b ON b.author_id = a.id
WHERE LOWER(a.name) LIKE LOWER('%George%')
ORDER BY a.id;
```

Hai Authors khớp:

```text
George R.R. Martin
George Orwell
```

Metrics:

```text
JDBC statements:      1
entities loaded:      8
collections loaded:   2
collections fetched:  0
```

Tính toán:

```text
2 Authors + 6 Books = 8 entities
2 Authors           = 2 Books collections
```

Điểm nói khi demo: Spring Data tạo phần `WHERE`; EntityGraph vẫn quyết định fetch Books. Không cần viết JPQL chỉ để gắn fetch plan.

### 11.5 EntityGraph trên `findById`

Endpoint:

```http
GET /api/entity-graph/05-find-by-id
```

Repository:

```java
@Override
@EntityGraph("Author.withBooks")
Optional<Author> findById(Long id);
```

SQL cho ID 1:

```sql
SELECT a.id, a.name, b.*
FROM authors a
LEFT JOIN books b ON b.author_id = a.id
WHERE a.id = 1;
```

Metrics:

```text
JDBC statements:      1
entities loaded:      4
collections loaded:   1
collections fetched:  0
```

```text
1 Author + 3 Books = 4 entities
```

Country bị suppress bởi `FETCH` graph. Override ảnh hưởng mọi caller dùng method `findById`; nếu có caller không cần Books, nên tạo repository method có tên riêng.

### 11.6 Nested EntityGraph

Endpoint:

```http
GET /api/entity-graph/06-nested-graph
```

Fetch plan:

```text
Author
└── Books
    └── Publisher
```

Dynamic form:

```java
@EntityGraph(attributePaths = {"books", "books.publisher"})
```

Named form dùng `Author.withBooksAndPublisher` và `@NamedSubgraph`. Endpoint chạy hai metric blocks riêng; `DemoMetrics` clear Persistence Context giữa hai blocks.

SQL rút gọn của cả hai:

```sql
SELECT a.id, a.name,
       b.id, b.title, b.publish_year,
       p.id, p.name
FROM authors a
LEFT JOIN books b      ON b.author_id = a.id
LEFT JOIN publishers p ON p.id = b.publisher_id
ORDER BY a.id;
```

Metric cho từng block:

```text
JDBC statements:      1
entities loaded:      25
collections loaded:   5
collections fetched:  0
```

```text
5 Authors + 15 Books + 5 Publishers = 25 entities
```

Publisher là `ManyToOne`, không phải collection, nên chỉ có năm Books collections. Graph nested tránh query Publisher khi service gọi `book.getPublisher().getName()`.

### 11.7 JPQL + EntityGraph

Endpoint:

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

Service truyền `keyword="George"`, `minId=4`. SQL tương đương:

```sql
SELECT a.id, a.name, b.*
FROM authors a
LEFT JOIN books b ON b.author_id = a.id
WHERE LOWER(a.name) LIKE LOWER('%George%')
   OR a.id > 4
ORDER BY a.id;
```

Authors được chọn là IDs 3, 4 và 5.

Metrics:

```text
JDBC statements:      1
entities loaded:      12
collections loaded:   3
collections fetched:  0
```

```text
3 Authors + 9 Books = 12 entities
```

Điểm nói khi demo:

```text
JPQL        -> chọn rows bằng WHERE/ORDER BY
EntityGraph -> yêu cầu Books được initialized
Hibernate   -> kết hợp thành SQL execution plan
```

### 11.8 `FETCH` graph và `LOAD` graph

Endpoint:

```http
GET /api/entity-graph/08-fetch-vs-load
```

Graph ở cả hai trường hợp chỉ chứa Books. `Author.country` được mapping EAGER có chủ đích.

#### FETCH graph

```text
Association trong graph -> fetch
Association ngoài graph  -> xem như LAZY cho operation này
```

SQL tương đương Dynamic graph:

```sql
SELECT a.id, a.name, b.*
FROM authors a
LEFT JOIN books b ON b.author_id = a.id
ORDER BY a.id;
```

Metrics:

```text
JDBC statements:      1
entities loaded:      20
collections loaded:   5
collections fetched:  0
```

Country nằm ngoài graph nên không load.

#### LOAD graph

```text
Association trong graph -> fetch
Association ngoài graph  -> giữ fetch type từ entity mapping
```

Hibernate 6.4 hiện lấy Authors + Books, sau đó secondary-select ba Countries duy nhất:

```sql
SELECT a.id, a.name, a.country_id, b.*
FROM authors a
LEFT JOIN books b ON b.author_id = a.id
ORDER BY a.id;

SELECT c.* FROM countries c WHERE c.id = 1;
SELECT c.* FROM countries c WHERE c.id = 2;
SELECT c.* FROM countries c WHERE c.id = 3;
```

Metrics:

```text
JDBC statements:      4
entities loaded:      23
collections loaded:   5
collections fetched:  0
```

```text
5 Authors + 15 Books + 3 Countries = 23 entities
```

EAGER bảo đảm Country initialized, không bảo đảm Hibernate phải join. Country không làm tăng collection metrics vì nó là `ManyToOne`.

### 11.9 Programmatic EntityGraph

Endpoint:

```http
GET /api/entity-graph/09-programmatic-graph
```

Code lấy Author ID 2:

```java
EntityGraph<Author> graph = entityManager.createEntityGraph(Author.class);
graph.addAttributeNodes("books");

Author author = entityManager.find(
    Author.class,
    2L,
    Map.of("jakarta.persistence.fetchgraph", graph)
);
```

SQL:

```sql
SELECT a.id, a.name, b.*
FROM authors a
LEFT JOIN books b ON b.author_id = a.id
WHERE a.id = 2;
```

Metrics:

```text
JDBC statements:      1
entities loaded:      4
collections loaded:   1
collections fetched:  0
```

```text
1 Author + 3 Books = 4 entities
```

Hint là `fetchgraph`, nên Country không có trong runtime graph bị suppress. Programmatic graph phù hợp khi fetch plan phụ thuộc flags hoặc permissions, nhưng verbose và dùng string attributes.

### 11.10 Runtime graph theo request flags

Endpoint:

```http
GET /api/entity-graph/10-runtime-graph/authors/{id}?includeBooks=...&includeAwards=...
```

Graph luôn chứa Country vì DTO luôn trả Country. Khi có Books, graph thêm nested Publisher.

#### Không Books, không Awards

```http
GET /api/entity-graph/10-runtime-graph/authors/1?includeBooks=false&includeAwards=false
```

```sql
SELECT a.id, a.name, c.id, c.name
FROM authors a
LEFT JOIN countries c ON c.id = a.country_id
WHERE a.id = 1;
```

```text
JDBC statements:      1
entities loaded:      2   = 1 Author + 1 Country
collections loaded:   0
collections fetched:  0
```

#### Chỉ Books

```http
GET /api/entity-graph/10-runtime-graph/authors/1?includeBooks=true&includeAwards=false
```

```sql
SELECT a.*, c.*, b.*, p.*
FROM authors a
LEFT JOIN countries c  ON c.id = a.country_id
LEFT JOIN books b      ON b.author_id = a.id
LEFT JOIN publishers p ON p.id = b.publisher_id
WHERE a.id = 1;
```

```text
JDBC statements:      1
entities loaded:      6   = Author + Country + 3 Books + Publisher
collections loaded:   1
collections fetched:  0
```

#### Chỉ Awards

```http
GET /api/entity-graph/10-runtime-graph/authors/1?includeBooks=false&includeAwards=true
```

```sql
SELECT a.*, c.*, aw.*
FROM authors a
LEFT JOIN countries c ON c.id = a.country_id
LEFT JOIN awards aw   ON aw.author_id = a.id
WHERE a.id = 1;
```

```text
JDBC statements:      1
entities loaded:      4   = Author + Country + 2 Awards
collections loaded:   1
collections fetched:  0
```

#### Books và Awards cùng lúc

```http
GET /api/entity-graph/10-runtime-graph/authors/1?includeBooks=true&includeAwards=true
```

Trong Hibernate 6.4 và dataset hiện tại, execution plan đã quan sát là:

```sql
-- Query 1: Author + Country + Awards
SELECT a.*, c.*, aw.*
FROM authors a
LEFT JOIN countries c ON c.id = a.country_id
LEFT JOIN awards aw   ON aw.author_id = a.id
WHERE a.id = 1;

-- Query 2: secondary collection fetch cho Books
SELECT b.*
FROM books b
WHERE b.author_id = 1;

-- Query 3: cả ba Books cùng dùng một Publisher
SELECT p.*
FROM publishers p
WHERE p.id = 1;
```

```text
JDBC statements:      3
entities loaded:      8
collections loaded:   2
collections fetched:  1
```

```text
1 Author + 1 Country + 3 Books + 1 Publisher + 2 Awards = 8 entities
```

`collections fetched=1` là Books secondary fetch. Awards đến từ root query nên chỉ được tính loaded, không phải fetched.

EntityGraph yêu cầu association phải được load; nó không bắt buộc Hibernate join tất cả trong một SQL. Fetch song song hai ToMany có thể tạo `3 x 2 = 6` rows, nên provider có thể chọn execution plan khác. SQL strategy này có thể thay đổi theo Hibernate version, mapping và graph shape.

DTO dùng:

```text
null -> client không yêu cầu association
[]   -> client có yêu cầu nhưng không có dữ liệu
```

### 11.11 Hai ToMany collections

Endpoint:

```http
GET /api/entity-graph/11-multiple-collections
```

Named graph chứa Books và Awards, nhưng không có nested Publisher:

```sql
SELECT a.*, b.*, aw.*
FROM authors a
LEFT JOIN awards aw ON aw.author_id = a.id
LEFT JOIN books b   ON b.author_id = a.id
ORDER BY a.id;
```

Metrics:

```text
JDBC statements:      1
entities loaded:      30
collections loaded:   10
collections fetched:  0
```

Entities:

```text
5 Authors + 15 Books + 10 Awards = 30 entities
```

Collections:

```text
5 Books collections + 5 Awards collections = 10
```

SQL rows:

```text
3 Books x 2 Awards x 5 Authors = 30 joined rows
```

Con số SQL rows và entities cùng là 30 chỉ là trùng hợp; chúng đo hai thứ khác nhau. Log mỗi Author cho thấy:

```text
3 distinct Books
2 Awards
6 joined rows
hydrated List<Book> size = 6
```

`Set<Award>` tránh hai Bags và loại Awards lặp, nhưng không tránh row multiplication hay Books references lặp trong List.

### 11.12 EntityGraph và pagination

Endpoint:

```http
GET /api/entity-graph/12-pagination
```

Endpoint chạy ba scenario với:

```java
PageRequest.of(0, 2)
```

```text
page number = 0
page size   = 2
```

#### A. ToOne Country pagination an toàn

Một Author có tối đa một Country nên mỗi Author vẫn tương ứng một SQL row:

```sql
-- Content query
SELECT a.*, c.*
FROM authors a
LEFT JOIN countries c ON c.id = a.country_id
ORDER BY a.id
OFFSET 0 ROWS FETCH FIRST 2 ROWS ONLY;

-- Count query để tạo Page metadata
SELECT COUNT(a.id)
FROM authors a;
```

Hai Authors đầu đều dùng United Kingdom, nên Persistence Context chỉ tạo một Country object.

```text
JDBC statements:      2
entities loaded:      3   = 2 Authors + 1 Country
collections loaded:   0
collections fetched:  0
```

Database paginate đúng hai parent rows.

#### B. ToMany Books pagination không an toàn

Joined rows bắt đầu như sau:

```text
row 1: A1-B1
row 2: A1-B2
row 3: A1-B3
row 4: A2-B4
row 5: A2-B5
row 6: A2-B6
```

Nếu database áp dụng limit 2 trực tiếp, nó chỉ trả `A1-B1`, `A1-B2`: không đủ hai Authors và collection A1 còn thiếu B3.

Với config mặc định hiện tại là `false`, Hibernate bỏ SQL-level limit cho collection fetch, đọc toàn bộ joined result rồi cắt hai Authors trong memory:

```sql
-- Không có OFFSET/FETCH trong content query
SELECT a.*, b.*
FROM authors a
LEFT JOIN books b ON b.author_id = a.id
ORDER BY a.id;

SELECT COUNT(a.id)
FROM authors a;
```

```text
JDBC statements:      2
entities loaded:      20  = 5 Authors + 15 Books
collections loaded:   5
collections fetched:  0
```

Mặc dù page size là 2, Hibernate vẫn load dữ liệu của cả 5 Authors. Warning:

```text
HHH90003004: firstResult/maxResults specified with collection fetch;
applying in memory
```

`firstResult/maxResults` là JPA offset/limit. `Applying in memory` nghĩa là hydrate toàn bộ parent/child result trước, sau đó mới lấy page trong Java memory.

#### C. `fail_on_pagination_over_collection_fetch`

Config hiện tại:

```yaml
hibernate:
  query:
    fail_on_pagination_over_collection_fetch: false
```

```text
false -> log warning, chạy in-memory pagination, rồi tiếp tục two-step demo
true  -> throw exception trước khi gửi unsafe SQL
```

Khi đổi thành `true`, unsafe metric block có thể là `JDBC=0`, endpoint trả HTTP 500 và method không chạy tới two-step scenario vì cả ba scenarios đang nằm trong cùng method. `true` là fail-fast guardrail, không phải thuật toán tự sửa pagination.

#### D. Two-step pagination an toàn

Bước 1 page parent IDs:

```sql
SELECT a.id
FROM authors a
ORDER BY a.id
OFFSET 0 ROWS FETCH FIRST 2 ROWS ONLY;
```

Kết quả:

```text
[1, 2]
```

Bước 2 count tổng Authors:

```sql
SELECT COUNT(a.id)
FROM authors a;
```

Bước 3 fetch đúng Authors của page cùng Books:

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
entities loaded:      8   = 2 Authors + 6 Books
collections loaded:   2
collections fetched:  0
```

ID và count là scalar values, không phải entities, nên không tăng `entities loaded`.

Ba queries an toàn có thể tốt hơn hai queries nếu cách hai queries phải đọc toàn bộ joined dataset. Quy tắc nhớ nhanh:

```text
ToOne:  một parent vẫn gần một row -> paginate trực tiếp thường an toàn
ToMany: một parent thành nhiều rows -> page parent IDs trước, fetch children sau
```

`Page` cần count để trả `totalElements` và `totalPages`. Nếu không cần tổng, `Slice` thường có thể tránh count query.

## 12. EntityGraph và JOIN FETCH

Cả hai thường có thể sinh SQL join tương tự:

```jpql
SELECT DISTINCT a
FROM Author a
LEFT JOIN FETCH a.books
```

so với:

```java
@EntityGraph(attributePaths = "books")
@Query("SELECT a FROM Author a")
```

Khác biệt chính:

| Tiêu chí | `JOIN FETCH` | `EntityGraph` |
|---|---|---|
| Fetch plan nằm ở đâu | Trong JPQL | Metadata/hint tách khỏi query |
| Derived query | Phải viết query nếu cần fetch join | Gắn graph trực tiếp lên derived method |
| Điều khiển join/filter | Rõ và trực tiếp trong JPQL | Provider quyết định SQL shape |
| Tái sử dụng | Thường lặp fetch clause | Named graph có thể tái sử dụng |
| Pagination ToMany | Có rủi ro | Có cùng loại rủi ro |
| Multiple collections | Có multiplication/bag risk | Có cùng loại risk |

EntityGraph không luôn nhanh hơn JOIN FETCH. Lợi ích chính là tách fetch plan khỏi query và chọn association theo use case.

## 13. Đọc Hibernate metrics đúng cách

`DemoMetrics` clear Persistence Context và reset Hibernate Statistics trước mỗi scenario để kết quả trước không làm association đã initialized sẵn.

Ưu tiên đọc:

1. `JDBC statements`.
2. SQL shape: repeated `WHERE`, `LEFT JOIN`, `IN`, count query hay secondary select.
3. `entities loaded`.
4. `collections loaded` và `collections fetched`.
5. `elapsed` chỉ tham khảo trong local demo.

### `collections loaded`

Số collection instances được initialized. Load Books cho 5 Authors có thể là:

```text
collections loaded = 5
```

### `collections fetched`

Số collection fetch events riêng, thường do lazy/batch fetching; không phải số Book rows.

Baseline:

```text
collections loaded  = 5
collections fetched = 5
```

Join graph:

```text
collections loaded  = 5
collections fetched = 0
```

Một batch query có thể initialize nhiều collections:

```text
collections loaded  = 3
collections fetched = 1
```

Đừng kết luận chỉ từ số statements. Một join statement có thể trả rất nhiều rows; một vài batched statements đôi khi hiệu quả hơn.

## 14. Transaction, LAZY và OSIV

Config đặt:

```yaml
spring.jpa.open-in-view: false
```

Các service demo dùng:

```java
@Transactional(readOnly = true)
```

Vì OSIV tắt, việc đọc LAZY association phải xảy ra trong transaction/service hoặc association phải được fetch trước. DTO cũng được map bên trong transaction.

Điều này tránh để web serialization vô tình trigger database queries ngoài service boundary và làm N+1 khó quan sát.

## 15. Chạy module

Khởi động:

```powershell
.\gradlew.bat :02-entity-graph:bootRun
```

Swagger:

```text
http://localhost:8082/swagger-ui/index.html
```

Mỗi endpoint chạy một scenario. Response mô tả kỳ vọng; SQL và `[METRICS]` xuất hiện trong console Gradle.

H2 in-memory database được tạo lại sau mỗi lần restart vì `ddl-auto: create-drop`.

## 16. Thứ tự demo khuyến nghị

### Flow đầy đủ

1. `/01-baseline-n-plus-one` — nhìn thấy N+1.
2. `/02-dynamic-graph` — sửa N+1 bằng graph đơn giản.
3. `/03-named-graph` — so sánh declaration/reuse với dynamic graph.
4. `/04-derived-query` — chứng minh graph hoạt động không cần JPQL.
5. `/05-find-by-id` — detail use case.
6. `/06-nested-graph` — fetch Author → Books → Publisher.
7. `/07-query-plus-graph` — tách row selection và fetch plan.
8. `/08-fetch-vs-load` — semantics nâng cao.
9. `/09-programmatic-graph` — tạo graph runtime.
10. `/10-runtime-graph/authors/1?...` — thay đổi graph bằng request flags.
11. `/11-multiple-collections` — thấy giới hạn Cartesian/bags.
12. `/12-pagination` — kết thúc bằng giới hạn nguy hiểm nhất.

### Flow rút gọn

```text
Baseline
    -> Dynamic graph
    -> Nested graph
    -> JPQL + graph
    -> Runtime flags
    -> Multiple collections
    -> Pagination
```

## 17. `core`, `advanced`, `all` và Swagger

Các mode này chỉ điều khiển `DemoRunner`, không phải khái niệm JPA.

Mặc định:

```yaml
demo:
  mode: core
  auto-run: false
```

Vì `auto-run=false`, không scenario nào tự chạy khi startup. Swagger vẫn có đầy đủ endpoints và đây là cách khuyến nghị để trình bày từng scenario.

Nếu muốn tự chạy flow console ngắn:

```powershell
.\gradlew.bat :02-entity-graph:bootRun --args="--demo.auto-run=true --demo.mode=core"
```

`core` chạy:

```text
Baseline -> Dynamic -> Derived -> Nested -> Multiple collections -> Pagination
```

Chạy toàn bộ:

```powershell
.\gradlew.bat :02-entity-graph:bootRun --args="--demo.auto-run=true --demo.mode=all"
```

`advanced` và `all` hiện là alias, đều chèn thêm Named, findById, JPQL + graph, FETCH/LOAD và Programmatic graph theo đúng thứ tự service.

Runtime flags API chỉ chạy khi được gọi qua HTTP; `DemoRunner` không tự giả lập bốn requests.

## 18. Khi nào nên và không nên dùng EntityGraph?

Nên cân nhắc EntityGraph khi:

- Use case cần managed/full entity.
- Association cần load thay đổi theo repository method.
- Muốn giữ entity mapping mặc định `LAZY`.
- Muốn gắn fetch plan vào derived query hoặc `findById`.
- Graph tương đối nhỏ và lượng dữ liệu join có thể kiểm soát.

Không nên mặc định dùng EntityGraph khi:

- API chỉ cần vài columns: DTO projection thường rõ và nhẹ hơn.
- Fetch nhiều ToMany collections lớn.
- Paginate parent cùng collection fetch.
- Client được phép tùy ý bật graph sâu không giới hạn.
- Query có reporting/aggregation phức tạp, phù hợp hơn với DTO/native query.
- Association access pattern mang tính lazy theo lô: BatchSize có thể phù hợp hơn.

## 19. Kết luận

```text
EntityGraph giải quyết câu hỏi "use case này cần load association nào?"
Nó có thể loại N+1 nhưng không tự bảo đảm result set nhỏ.
Một statement có thể vẫn rất nặng vì row multiplication.
ToMany collection fetch và pagination cần được xử lý riêng.
FETCH/LOAD là semantics của graph, không phải hai loại SQL cố định.
Programmatic graph linh hoạt nhưng cần giới hạn combinations.
Đánh giá fetch strategy bằng SQL shape, rows và metrics, không chỉ số query.
```
