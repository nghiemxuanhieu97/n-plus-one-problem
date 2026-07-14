# Module 04: Batch Size

Module này demo cách Hibernate batch lazy loading để giảm N+1 problem mà vẫn giữ association là `LAZY`.

Thông điệp chính:

```text
EntityGraph / JOIN FETCH:
Load parent và child bằng JOIN trong query chính.

Batch Size:
Vẫn load parent/entity chính trước.
Khi lazy association bị truy cập, Hibernate gom nhiều lazy requests lại thành một câu IN query.
```

Domain demo:

```text
Author 1 -- N Book
Book   N -- 1 Publisher
```

Data demo:

```text
5 authors
15 books
3 publishers
mỗi author có 3 books
```

## Cách Chạy Demo

Từ root project:

```powershell
.\gradlew.bat :04-batch-size:bootRun
```

Khi chạy, đọc log theo thứ tự:

```text
[1] Attribute-level @BatchSize trên Author.books
[2] Class-level @BatchSize trên Author
[3] Application-level default_batch_fetch_size trong application.yml
[4] Batch preloading trade-off
[5] Parent pagination + @BatchSize
```

Mỗi scenario có block `[METRICS]`:

```text
JDBC statements
entities loaded
collections loaded
collections fetched
```

Chỉ số quan trọng nhất khi demo:

```text
JDBC statements: số SQL statement Hibernate chuẩn bị chạy
entities loaded: số entity object được hydrate vào persistence context
collections loaded: số lazy collection được load
collections fetched: số lần Hibernate phải fetch collection lazy
```

## 3 Cấp Batch Size Khác Nhau Như Nào?

Có 3 chỗ hay gặp:

```text
1. application.yml
2. @BatchSize trên entity class
3. @BatchSize trên attribute / association
```

Chúng không hoàn toàn giống nhau. Khác biệt nằm ở câu hỏi:

```text
Hibernate đang batch-load cái gì?
```

## 1. Batch Size Ở application.yml

Code:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 5
```

Ý nghĩa:

```text
Đây là cấu hình mặc định toàn app.
Nếu một lazy entity/association không có @BatchSize riêng,
Hibernate vẫn có thể batch lazy loading theo size này.
```

Trong demo, `Book.publisher` không có `@BatchSize`:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "publisher_id")
private Publisher publisher;
```

Service demo:

```java
List<Book> books = bookRepository.findAll();

for (Book book : books) {
    book.getPublisher().getName();
}
```

Bản chất chạy:

```text
SQL #1: load books trước
SQL #2: khi đụng publisher, Hibernate batch-load Publisher proxies bằng IN
```

SQL shape:

```sql
select b.*
from books b;

select p.*
from publishers p
where p.id in (?, ?, ?, ?, ?);
```

Log thực tế:

```text
[METRICS] Application-level default_batch_fetch_size: findAll books + getPublisher()
JDBC statements:      2
entities loaded:      18
collections loaded:   0
collections fetched:  0
```

Vì sao `entities loaded = 18`?

```text
15 Book + 3 Publisher = 18 entities
```

Vì sao `collections loaded = 0`?

```text
Book.publisher là ManyToOne, không phải collection.
```

Ưu điểm:

- Ít phải annotate từng association.
- Có tác dụng như “lưới an toàn” cho nhiều lazy loading case.
- Hữu ích trong project lớn, nơi không muốn sửa quá nhiều entity ngay từ đầu.

Nhược điểm:

- Dễ bị áp dụng rộng hơn bạn nghĩ.
- Người đọc entity không nhìn thấy batch size ngay tại mapping.
- Có thể load thêm dữ liệu ngoài ý muốn nếu access pattern không rõ.

Khi dùng:

```text
Dùng như default chung.
Sau đó chỗ nào quan trọng thì override bằng @BatchSize cụ thể.
```

## 2. Batch Size Ở Class

Code trong `Author`:

```java
@Entity
@Table(name = "authors")
@BatchSize(size = 5)
public class Author {
    ...
}
```

Ý nghĩa:

```text
@BatchSize trên entity class áp dụng khi Hibernate cần initialize nhiều proxy của chính entity đó.
```

Nói dễ hiểu:

```text
Nếu trong persistence context có nhiều Author proxy chưa load,
khi bạn đụng một Author proxy,
Hibernate có thể gom nhiều author_id lại và load Authors bằng một câu IN.
```

Trong demo, `Book.author` là lazy:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "author_id")
private Author author;
```

Service demo:

```java
List<Book> books = bookRepository.findAll();

for (Book book : books) {
    book.getAuthor().getName();
}
```

Bản chất chạy:

```text
SQL #1: load books trước
SQL #2: khi đụng book.getAuthor(), Hibernate batch-load Author proxies bằng IN
```

SQL shape:

```sql
select b.*
from books b;

select a.*
from authors a
where a.id in (?, ?, ?, ?, ?);
```

Log thực tế:

```text
[METRICS] Class-level @BatchSize: findAll books + getAuthor()
JDBC statements:      2
entities loaded:      20
collections loaded:   0
collections fetched:  0
```

Vì sao `entities loaded = 20`?

```text
15 Book + 5 Author = 20 entities
```

Vì sao vẫn `collections loaded = 0`?

```text
Ở demo này ta load Book.author.
Author là entity proxy, không phải collection.
```

Ưu điểm:

- Rất hợp cho nhiều `ManyToOne` / `OneToOne` lazy proxies trỏ về cùng một entity type.
- Giảm N+1 kiểu `book.getAuthor()`, `order.getCustomer()`, `comment.getUser()`.
- Mapping rõ: ai đọc class `Author` biết Author proxy có batch size riêng.

Nhược điểm:

- Không có nghĩa là mọi collection trong `Author` đều được batch theo class annotation.
- Nếu bạn muốn batch `Author.books`, nên annotate trực tiếp trên `books`.
- Vẫn có thể load nhiều Author hơn số bạn thật sự cần nếu access ít.

Điểm dễ nhầm:

```text
@BatchSize trên class Author:
batch-load Author entities/proxies.

@BatchSize trên field books:
batch-load Author.books collections.
```

## 3. Batch Size Ở Attribute / Association

Code trong `Author.books`:

```java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
@BatchSize(size = 5)
private List<Book> books = new ArrayList<>();
```

Ý nghĩa:

```text
@BatchSize trên association áp dụng trực tiếp cho association đó.
Ở đây là lazy collection Author.books.
```

Service demo:

```java
List<Author> authors = authorRepository.findAll();

for (Author author : authors) {
    author.getBooks().size();
}
```

Bản chất chạy:

```text
SQL #1: load authors trước
SQL #2: khi đụng author.getBooks(), Hibernate batch-load books cho nhiều author_id bằng IN
```

SQL shape:

```sql
select a.*
from authors a;

select b.*
from books b
where b.author_id in (?, ?, ?, ?, ?);
```

Log thực tế:

```text
[METRICS] @BatchSize full list: findAll() + getBooks()
JDBC statements:      2
entities loaded:      20
collections loaded:   5
collections fetched:  1
```

Vì sao `collections loaded = 5`?

```text
Có 5 Author.books collections được initialize.
```

Vì sao `collections fetched = 1`?

```text
Hibernate chỉ cần 1 lần fetch collection bằng IN để load books cho cả 5 authors.
```

Ưu điểm:

- Rõ nhất, vì annotation nằm ngay tại association cần tối ưu.
- Phù hợp để xử lý N+1 của collection như `Author.books`.
- Ít rủi ro ảnh hưởng lan rộng hơn global config.

Nhược điểm:

- Phải annotate từng association quan trọng.
- Hibernate-specific, không phải JPA chuẩn.
- Nếu chỉ access một collection, Hibernate vẫn có thể preload cả batch.

## Thứ Tự Ưu Tiên

Quy tắc thực tế:

```text
@BatchSize cụ thể trên class/attribute sẽ rõ ràng hơn global default.
application.yml là fallback mặc định.
```

Ví dụ:

```text
default_batch_fetch_size = 5
Author.books có @BatchSize(size = 20)

Khi batch Author.books, Hibernate ưu tiên size 20 cho association đó.
Những lazy association khác không có @BatchSize riêng vẫn dùng default 5.
```

## So Sánh Nhanh 3 Cấp

| Vị trí | Batch cái gì? | Demo trong project | SQL IN theo cột nào? |
|--------|---------------|--------------------|----------------------|
| `application.yml` | Default cho lazy entity/association không có annotation riêng | `Book.publisher` | `publishers.id in (...)` |
| Entity class | Proxy của entity đó | `@BatchSize` trên `Author`, access `Book.author` | `authors.id in (...)` |
| Attribute/association | Association cụ thể đó | `@BatchSize` trên `Author.books` | `books.author_id in (...)` |

Một câu nhớ nhanh:

```text
Application: default toàn app.
Class: batch entity proxy theo id của entity đó.
Attribute: batch đúng association đó.
```

## Batch Size Có Khác EntityGraph / JOIN FETCH Không?

Có, khác ở cách lấy dữ liệu.

EntityGraph / JOIN FETCH thường sinh:

```sql
select a.*, b.*
from authors a
left join books b on b.author_id = a.id;
```

Batch Size sinh:

```sql
select a.*
from authors a;

select b.*
from books b
where b.author_id in (?, ?, ?, ?, ?);
```

Khác biệt cốt lõi:

```text
JOIN FETCH:
Lấy parent + child trong query chính.
Parent row có thể bị nhân lên theo số child rows.

Batch Size:
Lấy parent/entity chính trước.
Lazy association vẫn lazy.
Khi bị access, Hibernate load phần lazy bằng query riêng có IN.
```

## LEFT JOIN Khác Gì IN Query Của Batch Size?

Đây là câu hỏi rất hay khi demo:

```text
Nếu LEFT JOIN lấy được parent + child luôn,
tại sao Hibernate còn cần Batch Size dùng IN?
```

Câu trả lời ngắn:

```text
LEFT JOIN tốt khi bạn biết chắc cần dữ liệu liên quan ngay trong query chính.
IN query của Batch Size tốt khi bạn muốn giữ LAZY và chỉ load dữ liệu liên quan nếu code thật sự đụng tới.
```

### LEFT JOIN Là Gì?

SQL:

```sql
select a.*, b.*
from authors a
left join books b on b.author_id = a.id;
```

Bản chất:

```text
Database ghép authors và books trong cùng một result set.
Nếu Author có 3 Books, SQL trả về 3 rows cho Author đó.
```

Ví dụ:

```text
J.R.R. Tolkien có 3 books
```

Result set sau JOIN có thể là:

```text
author_id | author_name     | book_id | book_title
1         | J.R.R. Tolkien  | 1       | The Hobbit
1         | J.R.R. Tolkien  | 2       | The Fellowship of the Ring
1         | J.R.R. Tolkien  | 3       | The Two Towers
```

Trong database result set, Author bị lặp lại 3 lần. Hibernate sau đó phải gom các rows này lại thành 1 object `Author` và 3 object `Book`.

### IN Query Của Batch Size Là Gì?

SQL:

```sql
select a.*
from authors a;

select b.*
from books b
where b.author_id in (?, ?, ?, ?, ?);
```

Bản chất:

```text
Query đầu lấy parent/entity chính.
Query sau lấy child theo danh sách parent ids đã có trong persistence context.
```

Ví dụ:

```text
Đã load authors có id 1, 2, 3, 4, 5.
Khi code gọi author.getBooks(),
Hibernate query books where author_id in (1, 2, 3, 4, 5).
```

Điểm quan trọng:

```text
IN query không ghép parent + child vào cùng result set.
Nó lấy child riêng, nên parent rows không bị nhân lên.
```

## Tại Sao Người Ta Dùng LEFT JOIN Thay Vì IN?

Dùng LEFT JOIN / JOIN FETCH / EntityGraph khi:

- Bạn biết chắc response cần parent và child ngay lập tức.
- Dataset nhỏ hoặc số child mỗi parent ít.
- Không cần pagination phức tạp trên parent collection.
- Muốn giảm round-trip xuống 1 query.
- Muốn query/filter/order theo field của bảng liên quan.

Ví dụ hợp lý:

```text
API detail: GET /authors/1
Cần trả về author + toàn bộ books của author đó.
```

Query:

```sql
select a.*, b.*
from authors a
left join books b on b.author_id = a.id
where a.id = ?;
```

Vì chỉ có một author, row multiplication thường không đáng sợ.

Một ví dụ khác:

```text
Cần tìm authors có book xuất bản sau năm 2000.
```

Lúc này JOIN tự nhiên hơn:

```sql
select distinct a.*
from authors a
join books b on b.author_id = a.id
where b.publish_year > 2000;
```

Ở đây JOIN không chỉ để fetch data, mà còn để filter theo bảng `books`.

Ưu điểm của LEFT JOIN:

- Có thể lấy dữ liệu liên quan trong 1 SQL.
- Rất rõ ràng khi query cần điều kiện từ nhiều bảng.
- Tốt cho màn hình detail hoặc data nhỏ.
- Không phụ thuộc vào lazy access sau đó.

Nhược điểm của LEFT JOIN:

- Collection JOIN làm parent row bị nhân theo số child.
- Fetch nhiều collection có thể tạo cartesian product.
- Pagination trên parent dễ gặp bẫy.
- Result set có thể rất lớn, làm tốn network, JDBC processing và memory.

## Tại Sao Người Ta Dùng IN / Batch Size Thay Vì LEFT JOIN?

Dùng Batch Size khi:

- Association nên giữ `LAZY`.
- Bạn chưa chắc có cần child hay không.
- Có nhiều parent trong cùng transaction và có khả năng đụng lazy child của nhiều parent.
- Cần pagination ổn định trên parent.
- Muốn tránh result set bị nhân row do collection JOIN.
- Có nhiều collection, không muốn JOIN tất cả vào một query khổng lồ.

Ví dụ hợp lý:

```text
API list: GET /authors?page=0&size=20
Trước hết cần page authors.
Sau đó service/UI có thể cần books của một số hoặc nhiều authors.
```

Batch Size flow:

```sql
select a.*
from authors a
offset ? rows fetch first ? rows only;

select b.*
from books b
where b.author_id in (?, ?, ?, ?, ?);
```

Điểm mạnh:

```text
Pagination cắt trên authors trước.
Books được load sau bằng query riêng.
Parent rows không bị nhân bởi books.
```

Ưu điểm của Batch Size / IN:

- Giữ association là `LAZY`.
- Giảm N+1 thành `1 + ceil(N / batch_size)`.
- Thân thiện hơn với pagination parent.
- Tránh cartesian product khi có nhiều collections.
- Chỉ load child khi code thật sự truy cập lazy association.

Nhược điểm của Batch Size / IN:

- Không phải 1 query duy nhất.
- Hibernate-specific nếu dùng `@BatchSize`.
- Có thể preload thừa trong batch.
- Nếu batch size quá lớn, `IN (...)` dài và có thể tạo áp lực memory.
- Không phù hợp nếu query chính cần filter/order theo child fields.

## IN Query Có Limit Hoặc Performance Trap Không?

Có. `IN (...)` giúp giảm N+1, nhưng nếu danh sách trong `IN` quá dài thì nó cũng có mặt trái.

Ví dụ Batch Size sinh:

```sql
select b.*
from books b
where b.author_id in (?, ?, ?, ?, ?);
```

Với batch size nhỏ như 5, 10, 20, 50 thì thường ổn.

Nhưng nếu bạn cấu hình quá lớn:

```text
default_batch_fetch_size = 1000
hoặc
@BatchSize(size = 1000)
```

Hibernate có thể sinh query dạng:

```sql
where author_id in (?, ?, ?, ..., ?)
```

Lúc này có vài vấn đề.

### 1. Database Có Giới Hạn Số Parameter / Số Item

Mỗi database có giới hạn riêng.

Ví dụ thường gặp:

```text
Oracle có giới hạn nổi tiếng: IN list tối đa 1000 expressions.
SQL Server có giới hạn tổng số parameters cho một statement.
PostgreSQL/MySQL thường linh hoạt hơn, nhưng query quá dài vẫn không miễn phí.
```

Không nên học thuộc một con số cho mọi database. Câu trả lời đúng là:

```text
IN list limit phụ thuộc database, driver và cách Hibernate bind parameters.
Batch size quá lớn có thể chạm limit hoặc làm query rất nặng.
```

### 2. Query Parse/Plan Có Thể Nặng Hơn

`IN (?, ?, ?, ..., ?)` càng dài thì database càng phải xử lý statement lớn hơn.

Ảnh hưởng có thể gồm:

```text
SQL text dài hơn
nhiều bind parameters hơn
parse/plan cost cao hơn
network payload lớn hơn
```

Với batch size 20 hoặc 50, chi phí này thường không đáng kể.

Với batch size vài trăm hoặc vài nghìn, nó bắt đầu đáng quan tâm.

### 3. Có Thể Load Thừa Nhiều Hơn

Batch Size càng lớn thì khả năng preload thừa càng lớn.

Ví dụ:

```text
Bạn chỉ cần books của 1 author.
Nhưng persistence context đang có 100 authors.
Batch size = 100.
Hibernate có thể load books cho 100 authors.
```

Kết quả:

```text
Ít query hơn
nhưng nhiều Book entities hơn vào memory
persistence context phình hơn
```

Đây là lý do README có metrics:

```text
entities loaded
collections loaded
```

Không chỉ nhìn `JDBC statements`.

### 4. Optimizer Có Thể Chọn Plan Không Tối Ưu

Với `IN` list rất dài, database optimizer có thể chọn plan khác nhau tùy thống kê bảng, index và số lượng parameter.

Nếu cột trong `IN` có index tốt:

```sql
where author_id in (...)
```

thường ổn hơn.

Nếu thiếu index:

```text
Database có thể phải scan nhiều hơn.
Batch Size không cứu được vấn đề thiếu index.
```

Trong demo này, `books.author_id` là foreign key và thường nên có index trong database thật.

### 5. Hibernate Có Thể Padding IN List

Trong log demo pagination, page chỉ có 2 authors nhưng batch size là 5:

```text
where author_id in (?, ?, ?, ?, ?)

binding 1 <- 1
binding 2 <- 2
binding 3 <- null
binding 4 <- null
binding 5 <- null
```

Điều này làm query shape ổn định hơn, nhưng cũng cho thấy:

```text
Batch size quyết định số placeholder mà Hibernate có thể sinh ra.
Batch size càng lớn, statement càng dài.
```

## Vậy Batch Size Bao Nhiêu Là Hợp Lý?

Không có số đúng tuyệt đối, nhưng rule of thumb:

```text
10, 20, 25, 50 thường là vùng an toàn để bắt đầu.
```

Nếu data nhỏ hoặc association nhẹ:

```text
batch size 20 hoặc 50 có thể ổn.
```

Nếu child collection lớn:

```text
nên cẩn thận hơn, có thể bắt đầu 10 hoặc 20.
```

Nếu định set 100, 500, 1000:

```text
phải đo bằng log, metrics và execution plan.
Không nên set lớn chỉ vì muốn giảm số query.
```

Câu trả lời demo nên nói:

```text
Batch Size dùng IN để giảm N+1, nhưng IN quá lớn có thể chạm database limit,
làm statement dài, tăng parse cost, load thừa và tăng memory.

Vì vậy Batch Size nên là con số vừa phải,
và phải đo cả query count lẫn entities loaded/collections loaded.
```

## Ví Dụ So Sánh Cụ Thể

Giả sử:

```text
5 authors
mỗi author có 3 books
```

### Cách 1: Không tối ưu

Code:

```java
List<Author> authors = authorRepository.findAll();

for (Author author : authors) {
    author.getBooks().size();
}
```

SQL:

```text
1 query authors
5 query books từng author
= 6 queries
```

Vấn đề:

```text
N+1 queries.
```

### Cách 2: LEFT JOIN FETCH

SQL:

```sql
select a.*, b.*
from authors a
left join books b on b.author_id = a.id;
```

Kết quả:

```text
1 SQL query
nhưng result set có 15 rows vì 5 authors x 3 books
```

Tốt khi:

```text
Bạn chắc chắn cần toàn bộ authors + books ngay.
Data nhỏ.
Không paginate phức tạp trên authors.
```

Không tốt khi:

```text
Mỗi author có rất nhiều books.
Bạn paginate authors.
Bạn fetch thêm nhiều collection khác.
```

### Cách 3: Batch Size / IN

SQL:

```sql
select a.*
from authors a;

select b.*
from books b
where b.author_id in (?, ?, ?, ?, ?);
```

Kết quả:

```text
2 SQL queries
parent rows không bị nhân trong query authors
books được lấy riêng bằng IN
```

Tốt khi:

```text
Bạn muốn giữ LAZY.
Bạn đang load nhiều parents.
Bạn muốn tránh N+1 nhưng không muốn JOIN collection.
Bạn cần pagination parent ổn định.
```

Không tốt khi:

```text
Bạn luôn cần child ngay và data nhỏ.
Bạn cần filter/order trực tiếp theo child trong query chính.
```

## So Sánh Với Các Phương Pháp Khác

| Phương pháp | SQL shape | Tốt khi | Rủi ro |
|------------|-----------|---------|--------|
| Lazy mặc định | `select parent`, sau đó nhiều `select child where parent_id = ?` | Không phải lúc nào cũng cần child | Dễ N+1 |
| JOIN FETCH | Một query có `join fetch` | Biết chắc cần child ngay | Nhân rows, pagination collection khó |
| EntityGraph | Thường sinh LEFT JOIN theo graph | Muốn fetch plan theo từng use case mà không viết JPQL join fetch | Vẫn có rủi ro giống JOIN khi fetch collection |
| Batch Size | Parent query trước, lazy child query sau bằng `IN` | Muốn giữ LAZY, giảm N+1, pagination parent | Có thể preload thừa, vẫn nhiều hơn 1 query |
| DTO Projection | Select đúng field cần trả về | API read-only, chỉ cần vài field | Không trả về entity đầy đủ, phải viết DTO/query riêng |
| Subselect Fetch | Load child bằng subselect dựa trên query parent trước đó | Muốn load collections cho toàn bộ parent result hiện tại | Có thể load quá rộng nếu parent result lớn |

## Chọn Cái Nào Khi Demo?

Nếu bị hỏi “LEFT JOIN hay IN cái nào tốt hơn?”, đừng trả lời cái nào luôn tốt hơn. Trả lời theo access pattern:

```text
Nếu tôi biết chắc cần child ngay, data nhỏ, không paginate collection:
LEFT JOIN / JOIN FETCH / EntityGraph đơn giản và nhanh vì thường 1 query.

Nếu tôi cần page parent trước, giữ association LAZY, và chỉ batch-load child khi code truy cập:
Batch Size dùng IN hợp lý hơn.

Nếu API chỉ cần vài field:
DTO Projection thường tốt hơn cả hai vì không cần load entity graph đầy đủ.
```

Một câu chốt dễ nhớ:

```text
LEFT JOIN tối ưu cho "lấy chung ngay từ đầu".
Batch Size tối ưu cho "lazy nhưng đừng query từng cái".
DTO Projection tối ưu cho "chỉ lấy đúng dữ liệu cần trả về".
```

## Demo Trade-Off: Batch Có Thể Load Thừa

Code:

```java
List<Author> authors = authorRepository.findAll();
Author firstAuthor = authors.get(0);

firstAuthor.getBooks().size();
```

Bạn chỉ đụng books của author đầu tiên.

Nhưng vì trong persistence context đang có 5 authors và `Author.books` batch size là 5, Hibernate có thể chạy:

```sql
select b.*
from books b
where b.author_id in (?, ?, ?, ?, ?);
```

Log thực tế:

```text
[METRICS] @BatchSize trade-off: access only first author's books
JDBC statements:      2
entities loaded:      20
collections loaded:   5
collections fetched:  1
```

Điểm đáng chú ý:

```text
Chỉ gọi getBooks() trên 1 author
nhưng Hibernate load books cho 5 authors.
```

Đây là ưu hay nhược tùy tình huống:

```text
Tốt nếu lát nữa bạn cũng cần books của các authors còn lại.
Thừa nếu bạn thật sự chỉ cần đúng 1 author.
```

## Demo Pagination

Code:

```java
Page<Author> page = authorRepository.findAll(PageRequest.of(0, 2));
List<Author> authors = page.getContent();

for (Author author : authors) {
    author.getBooks().size();
}
```

SQL shape:

```sql
select a.*
from authors a
offset ? rows fetch first ? rows only;

select count(a.id)
from authors a;

select b.*
from books b
where b.author_id in (?, ?, ?, ?, ?);
```

Log thực tế:

```text
[METRICS] @BatchSize with parent pagination: page size 2
JDBC statements:      3
entities loaded:      8
collections loaded:   2
collections fetched:  1
```

Vì sao `entities loaded = 8`?

```text
2 Author trong page + 6 Book của 2 authors = 8 entities
```

Điểm mạnh so với collection JOIN FETCH:

```text
Pagination được áp dụng trên bảng authors trước.
Sau đó books được load bằng query riêng.
Vì vậy parent pagination không bị méo do JOIN nhân rows.
```

Bẫy pagination với JOIN FETCH collection:

```text
1 Author có 3 Books.
JOIN Author + Book tạo 3 SQL rows cho cùng 1 Author.
Nếu database cắt LIMIT/OFFSET trên rows đã join,
page có thể thiếu parent hoặc Hibernate phải pagination in-memory.
```

Batch Size tránh bẫy này bằng cách:

```text
Page parent trước.
Batch-load child sau.
```

Trong log page size là 2 nhưng batch size là 5, nên Hibernate vẫn có thể sinh 5 placeholders:

```text
where author_id in (?, ?, ?, ?, ?)

binding 1 <- 1
binding 2 <- 2
binding 3 <- null
binding 4 <- null
binding 5 <- null
```

Đây là bình thường. Hibernate giữ query shape theo batch size 5, nhưng page hiện tại chỉ có 2 author ids.

## Phân Tích Trade-off Về Dữ Liệu Load

Batch Size liên quan trực tiếp đến performance, nhưng không chỉ là số query.

Nó ảnh hưởng:

```text
1. Database round-trip giảm
2. Số entity/collection được load có thể tăng
3. Persistence context có thể giữ nhiều object hơn trong transaction
```

Ví dụ:

```text
Chỉ cần books của 1 author.
Batch Size có thể load books của 5 authors.
```

Vì vậy khi demo, nhìn thêm `entities loaded` và `collections loaded` để thấy
việc preload thừa, thay vì cố kết luận từ một lần chạy local.

Để demo bài học N+1, các chỉ số hiện tại đủ để nói:

```text
Batch Size giảm round-trip.
Đổi lại nó có thể preload thêm entity/collection vào memory.
```

## Khi Nào Nên Dùng Batch Size?

Nên dùng khi:

- Association nên giữ `LAZY`.
- Có nhiều parent/entity proxies trong cùng transaction.
- Code có khả năng access lazy association của nhiều object.
- Muốn giảm N+1 nhưng không muốn JOIN FETCH collection.
- Cần pagination ổn định trên parent.
- Muốn tránh cartesian product khi fetch nhiều collection.

Ví dụ tốt:

```text
Load page 20 authors.
UI/service có khả năng cần books của nhiều authors.
Batch Size giúp từ 1 + 20 queries giảm còn 1 + ceil(20 / batch_size).
```

## Khi Nào Không Nên Kỳ Vọng Batch Size Là Tốt Nhất?

Không nên kỳ vọng Batch Size là tối ưu nhất khi:

- Chỉ cần child của đúng một parent.
- API chỉ cần vài field, DTO projection sẽ nhẹ hơn.
- Data rất nhỏ và luôn cần parent + child ngay, EntityGraph/JOIN FETCH có thể đơn giản hơn.
- Batch size quá lớn làm `IN (...)` dài và load thừa nhiều.
- Transaction giữ quá nhiều entity làm persistence context phình memory.

## Chọn Batch Size Bao Nhiêu?

Không có số thần kỳ.

Các giá trị hay gặp:

```text
10, 20, 25, 50
```

Batch size quá nhỏ:

```text
100 authors, batch size 5
1 + ceil(100 / 5) = 21 queries
```

Batch size quá lớn:

```text
Ít query hơn
nhưng IN list dài hơn
và có thể load thừa nhiều dữ liệu hơn
```

Với demo này:

```text
5 authors, batch size 5
=> 1 query authors + 1 query books = 2 JDBC statements
```

## Script Ngắn Để Thuyết Trình

```text
Batch Size không biến lazy thành eager.
Nó vẫn để association lazy.

Điểm khác nằm ở lúc lazy association bị truy cập.
Bình thường Hibernate có thể query từng cái:
where author_id = ?
where author_id = ?
where author_id = ?

Với Batch Size, Hibernate gom nhiều id lại:
where author_id in (?, ?, ?, ?, ?)

Có 3 cấp cấu hình:
application.yml là default toàn app.
@BatchSize trên class batch entity proxies, ví dụ Author proxies.
@BatchSize trên attribute batch đúng association đó, ví dụ Author.books.

Ưu điểm là giảm N+1 và thân thiện hơn với parent pagination.
Nhược điểm là vẫn có thể load thừa và tăng memory trong persistence context.
```

## Tóm Tắt Cuối

```text
Batch Size = batch lazy loading bằng IN query.

Application-level:
default fallback cho toàn app.

Class-level:
batch entity proxies theo id.

Attribute-level:
batch association cụ thể.

Nó giúp giảm N+1, nhưng không miễn phí:
ít query hơn có thể đổi bằng nhiều entity/collection hơn trong memory.
```
