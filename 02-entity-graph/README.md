# Module 02: JPA EntityGraph

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

## 8. PersistentBag là gì?

Trong source code:

```java
private List<Book> books = new ArrayList<>();
```

Khi Author được Hibernate quản lý, collection thường được thay bằng wrapper `PersistentBag`. Wrapper này theo dõi:

- Collection đã initialized chưa.
- Collection thuộc entity owner nào.
- Hibernate Session nào đang quản lý.
- Snapshot ban đầu để dirty checking.
- Phần tử nào được thêm hoặc xóa.
- Có cần phát sinh SQL khi flush không.

Với Books `LAZY`:

```text
find Author -> PersistentBag(uninitialized)
getBooks().size() -> chạy SELECT books WHERE author_id = ?
                 -> PersistentBag(initialized)
```

Một `List` không có `@OrderColumn` thường có bag semantics trong Hibernate: cho phép duplicate và không có column lưu index/vị trí. Đây là nền tảng để hiểu `MultipleBagFetchException`.

## 9. MultipleBagFetchException và row multiplication

Giả sử Author có hai `List` bags:

```java
List<Book> books;    // 3 phần tử
List<Award> awards;  // 2 phần tử
```

Join cả hai sinh Cartesian combination:

```text
B1-W1  B1-W2
B2-W1  B2-W2
B3-W1  B3-W2
```

Tổng cộng:

```text
3 Books x 2 Awards = 6 SQL rows cho một Author
```

Vì bag cho phép duplicate và không có index, Hibernate không thể phân biệt một Book xuất hiện hai lần thật hay chỉ lặp do join với hai Awards. Hibernate vì vậy từ chối fetch đồng thời nhiều bags và có thể ném `MultipleBagFetchException`.

Module dùng:

```java
List<Book> books;
Set<Award> awards;
```

`Set<Award>` giúp tránh trường hợp hai bags, nhưng không xóa Cartesian multiplication. Với 5 Authors, dữ liệu demo vẫn tạo khoảng:

```text
5 x 3 books x 2 awards = 30 joined rows
```

Không nên đổi `List` thành `Set` chỉ để né exception nếu domain thực sự cần thứ tự hoặc duplicate. Các lựa chọn khác là tách query, dùng batch fetching, DTO hoặc thiết kế fetch plan khác.

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

## 11. Các phương pháp trong module

### 11.1 Baseline N+1

Endpoint:

```http
GET /api/entity-graph/01-baseline-n-plus-one
```

Flow:

```java
List<Author> authors = repository.findAllForNPlusOneBaseline();
for (Author author : authors) {
    author.getBooks().size();
}
```

SQL:

```text
1 query Authors
+ 5 lazy Book collection queries
= 6 JDBC statements
```

`findAll()` thông thường vẫn có thể tạo Books N+1. Module dùng method riêng vì `Author.country` cố tình là `EAGER`; minimal `FETCH` graph suppress Country để baseline chỉ đo Books N+1, không bị Country queries làm nhiễu.

Trong một model production có các associations mặc định `LAZY`, `findAll()` cộng với vòng lặp `getBooks()` đã đủ tạo baseline N+1.

### 11.2 Dynamic EntityGraph

Endpoint:

```http
GET /api/entity-graph/02-dynamic-graph
```

```java
@EntityGraph(attributePaths = "books")
List<Author> findAllWithDynamicGraph();
```

Kỳ vọng trong dataset này:

```text
1 joined statement
5 Authors
15 Books
5 initialized Books collections
0 lazy collection fetches
```

Ưu điểm: khai báo ngắn, fetch plan nằm ngay tại repository method.

Nhược điểm: attribute path là string và graph dài/nested có thể khó đọc hoặc lặp lại.

### 11.3 Named EntityGraph

Endpoint:

```http
GET /api/entity-graph/07-named-graph
```

Khai báo trên entity:

```java
@NamedEntityGraph(
    name = "Author.withBooks",
    attributeNodes = @NamedAttributeNode("books")
)
```

Dùng tại repository:

```java
@EntityGraph("Author.withBooks")
List<Author> findAllWithNamedGraph();
```

Với graph đơn giản chỉ có Books, Dynamic và Named graph thường tạo SQL tương tự và không có khác biệt hiệu năng cố hữu.

Giá trị của Named graph rõ hơn khi graph được dùng lại nhiều nơi, có nhiều attributes hoặc có subgraphs. Nhược điểm là entity class chứa thêm fetch declarations và có thể trở nên nặng.

### 11.4 Derived query + EntityGraph

Endpoint:

```http
GET /api/entity-graph/03-derived-query
```

```java
@EntityGraph("Author.withBooks")
List<Author> findByNameContainingIgnoreCaseOrderById(String name);
```

Spring Data đọc tên method và tạo filter tương đương `LOWER(name) LIKE ...`. EntityGraph vẫn bổ sung Books fetch plan.

Điểm cần chứng minh:

> Không cần viết JPQL chỉ để dùng EntityGraph. Cách tạo điều kiện query và cách fetch association là hai concerns riêng.

### 11.5 EntityGraph trên `findById`

Endpoint:

```http
GET /api/entity-graph/08-find-by-id
```

```java
@Override
@EntityGraph("Author.withBooks")
Optional<Author> findById(Long id);
```

Phù hợp với detail use case cần một Author và Books ngay lập tức. Cần chú ý việc override ảnh hưởng mọi caller dùng chính repository method này; nếu có use case không cần Books, nên tạo method tên riêng.

### 11.6 Nested EntityGraph

Endpoint:

```http
GET /api/entity-graph/04-nested-graph
```

Fetch plan:

```text
Author
    -> Books
        -> Publisher
```

Dynamic form:

```java
@EntityGraph(attributePaths = {"books", "books.publisher"})
```

Named form dùng `@NamedSubgraph`. Cả hai minh họa graph có thể fetch nhiều tầng và tránh query Publisher khi service đọc `book.getPublisher().getName()`.

Nested graph càng sâu càng cần cân nhắc row width, số joins và lượng dữ liệu thực sự cần trả về.

### 11.7 JPQL + EntityGraph

Endpoint:

```http
GET /api/entity-graph/10-query-plus-graph
```

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

Phân chia trách nhiệm:

```text
JPQL       -> WHERE, ORDER BY, Authors nào được chọn
EntityGraph -> Books association phải được initialized
Hibernate  -> kết hợp cả hai thành SQL execution plan
```

### 11.8 `FETCH` graph và `LOAD` graph

Endpoint:

```http
GET /api/entity-graph/09-fetch-vs-load
```

Graph chỉ chứa Books, trong khi `Author.country` được mapping `EAGER`.

```text
FETCH graph:
    association trong graph -> fetched
    association ngoài graph  -> được xem như LAZY cho operation này

LOAD graph:
    association trong graph -> fetched
    association ngoài graph  -> giữ fetch type từ entity mapping
```

Trong demo:

- `FETCH` load Books nhưng để Country uninitialized.
- `LOAD` load Books và vẫn tôn trọng Country `EAGER`.

Code dùng `Hibernate.isInitialized(country)` để quan sát trạng thái mà không vô tình trigger lazy load.

### 11.9 Programmatic EntityGraph

Endpoint:

```http
GET /api/entity-graph/11-programmatic-graph
```

```java
EntityGraph<Author> graph = entityManager.createEntityGraph(Author.class);
graph.addAttributeNodes("books");

Author author = entityManager.find(
    Author.class,
    id,
    Map.of("jakarta.persistence.fetchgraph", graph)
);
```

Đây là runtime version của fetch graph. Nó hữu ích khi fetch plan phụ thuộc request flags, permissions hoặc use case được quyết định trong lúc chạy.

Nhược điểm: code verbose hơn annotation, dùng string attributes và dễ tạo quá nhiều graph combinations nếu API cho phép client tùy ý chọn mọi association.

### 11.10 Runtime graph theo request flags

Endpoint:

```http
GET /api/entity-graph/authors/{id}?includeBooks=true&includeAwards=false
```

Thử bốn tổ hợp:

```http
GET /api/entity-graph/authors/1?includeBooks=false&includeAwards=false
GET /api/entity-graph/authors/1?includeBooks=true&includeAwards=false
GET /api/entity-graph/authors/1?includeBooks=false&includeAwards=true
GET /api/entity-graph/authors/1?includeBooks=true&includeAwards=true
```

Graph được xây theo flags:

```text
false, false -> Country
true,  false -> Country + Books + nested Publisher
false, true  -> Country + Awards
true,  true  -> Country + Books + Publisher + Awards
```

Kết quả runtime đã quan sát:

| Books | Awards | Book DTOs | Award DTOs | JDBC statements |
|---|---|---:|---:|---:|
| false | false | not requested | not requested | 1 |
| true | false | 3 | not requested | 1 |
| false | true | not requested | 2 | 1 |
| true | true | 3 | 2 | 3 trong lần chạy kiểm chứng |

Trường hợp cả hai `true` cho thấy EntityGraph không đồng nghĩa với một SQL join cố định. Hibernate có thể load một collection bằng root query và collection khác bằng secondary select để hoàn thành fetch plan.

DTO dùng `null` cho association không được request, giúp phân biệt:

```text
null -> client không yêu cầu field này
[]   -> client có yêu cầu nhưng association không có dữ liệu
```

### 11.11 Hai ToMany collections

Endpoint:

```http
GET /api/entity-graph/06-multiple-collections
```

Scenario fetch Books và Awards cùng lúc để quan sát:

- `Set<Award>` tránh hai bags.
- Một statement vẫn có thể trả khoảng 30 rows.
- Số statements thấp không tự động có nghĩa query nhẹ.
- Phải quan sát cả SQL shape, joined-row count và collection semantics.

### 11.12 EntityGraph và pagination

Endpoint:

```http
GET /api/entity-graph/05-pagination
```

Endpoint chạy ba scenario cố định với:

```java
PageRequest.of(0, 2)
```

nghĩa là page đầu tiên, size 2.

#### ToOne pagination an toàn

Fetch `Author.country` không nhân số rows vì mỗi Author chỉ có một Country:

```text
page content query + count query = 2 statements
```

Database có thể áp dụng `LIMIT/OFFSET` đúng theo Author rows.

#### ToMany pagination nguy hiểm

Một Author có 3 Books nên joined result là:

```text
A1-B1
A1-B2
A1-B3
A2-B4
A2-B5
A2-B6
```

`LIMIT 2` trên SQL rows chỉ lấy hai rows của A1, không phải hai Authors với collections đầy đủ.

Hibernate vì vậy phát warning:

```text
HHH90003004: firstResult/maxResults specified with collection fetch;
applying in memory
```

`firstResult/maxResults` là JPA representation của offset/limit. `Applying in memory` nghĩa là Hibernate có thể đọc toàn bộ joined result, hydrate entities rồi mới cắt page trong memory.

Query vẫn có thể chỉ báo hai JDBC statements, nhưng statement đầu có thể đọc lượng rows rất lớn.

#### `fail_on_pagination_over_collection_fetch`

Config demo:

```yaml
hibernate:
  query:
    fail_on_pagination_over_collection_fetch: false
```

```text
false -> cho query chạy, log warning và có thể paginate trong memory
true  -> fail fast bằng exception để developer sửa query
```

Option `true` không tự tối ưu query; nó là production guardrail ngăn query nguy hiểm chạy âm thầm.

#### Two-step pagination

Giải pháp trong module:

```text
1. Page Author IDs ổn định bằng LIMIT/OFFSET.
2. Count tổng Authors để tạo Page metadata.
3. Fetch Authors + Books chỉ cho danh sách IDs của page.
```

Kỳ vọng:

```text
ID page query + count query + association query = 3 statements
```

Ba statements có thể scale tốt hơn hai statements nếu cách hai statements đọc toàn bộ Cartesian result vào memory.

`Page` chứa `content`, `number`, `size`, `totalElements`, `totalPages`, `first`, `last`. Nếu không cần tổng số phần tử, `Slice` có thể tránh count query trong nhiều use case.

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
3. `/07-named-graph` — so sánh declaration/reuse với dynamic graph.
4. `/03-derived-query` — chứng minh graph hoạt động không cần JPQL.
5. `/08-find-by-id` — detail use case.
6. `/04-nested-graph` — fetch Author → Books → Publisher.
7. `/10-query-plus-graph` — tách row selection và fetch plan.
8. `/09-fetch-vs-load` — semantics nâng cao.
9. `/11-programmatic-graph` — tạo graph runtime.
10. `/authors/1?...` — thay đổi graph bằng request flags.
11. `/06-multiple-collections` — thấy giới hạn Cartesian/bags.
12. `/05-pagination` — kết thúc bằng giới hạn nguy hiểm nhất.

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
