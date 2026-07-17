# Batch Size: full flow từ HTTP đến Hibernate batch fetching

Tài liệu này giải thích sâu module `04-batch-size`: code đang làm gì, Hibernate thực sự batch cái gì, query `IN (...)` được sinh lúc nào, database trả về gì, Hibernate ghép kết quả ra sao, metrics có ý nghĩa gì và nên demo theo thứ tự nào.

Mục tiêu quan trọng nhất là hiểu đúng bản chất:

```text
Batch Size không JOIN sẵn dữ liệu.
Batch Size không biến LAZY thành EAGER.
Batch Size không phải JDBC insert/update batching.

Nó tối ưu thời điểm Hibernate buộc phải lazy-load:
thay vì load từng entity/collection bằng từng SQL,
Hibernate gom nhiều khóa đang chờ và load chúng bằng một SQL có IN (...).
```

---

## 1. Bài toán mà Batch Size giải quyết

Giả sử đã load 12 Authors:

```java
List<Author> authors = authorRepository.findAll();
```

`Author.books` là `LAZY`, nên SQL đầu tiên chỉ lấy Authors:

```sql
select
    a.id,
    a.name
from authors a;
```

Sau đó code truy cập:

```java
for (Author author : authors) {
    author.getBooks().size();
}
```

Nếu không batch, lần đầu chạm collection của mỗi Author có thể tạo một query:

```sql
select b.* from books b where b.author_id = 1;
select b.* from books b where b.author_id = 2;
select b.* from books b where b.author_id = 3;
-- ...
select b.* from books b where b.author_id = 12;
```

Tổng cộng:

```text
1 query lấy Authors
+ 12 query lấy Books
= 13 JDBC statements
```

Đây là N+1:

```text
1 = query lấy danh sách parent
N = query lazy association của từng parent
```

Với:

```java
@BatchSize(size = 5)
private List<Book> books;
```

Hibernate có thể thay 12 query riêng bằng 3 query:

```sql
select b.*
from books b
where b.author_id in (?, ?, ?, ?, ?);
```

Các batch tương ứng:

```text
Batch 1: author IDs 1, 2, 3, 4, 5
Batch 2: author IDs 6, 7, 8, 9, 10
Batch 3: author IDs 11, 12, null, null, null
```

Tổng:

```text
1 query Authors
+ ceil(12 / 5) query Books
= 1 + 3
= 4 JDBC statements
```

Batch Size không xóa hoàn toàn query phụ. Nó biến:

```text
query từng cái
```

thành:

```text
query theo từng nhóm
```

---

## 2. Domain model và database relationship

Module có ba entity:

```text
Author    1 -------- N Book
Publisher 1 -------- N Book
```

Database tables:

```text
authors
-------
id PK
name

publishers
----------
id PK
name

books
-----
id PK
title
publish_year
author_id FK    -> authors.id
publisher_id FK -> publishers.id
```

`Book` là owning side vì bảng `books` giữ hai foreign keys:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "author_id")
private Author author;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "publisher_id")
private Publisher publisher;
```

`Author.books` là inverse side:

```java
@OneToMany(
    mappedBy = "author",
    cascade = CascadeType.ALL,
    orphanRemoval = true,
    fetch = FetchType.LAZY
)
@BatchSize(size = 5)
private List<Book> books = new ArrayList<>();
```

`mappedBy = "author"` có nghĩa:

```text
Quan hệ này được quyết định bởi field Book.author.
Foreign key thật nằm ở books.author_id.
Author.books không tạo thêm join table.
```

`Publisher.books` cố ý không có `@BatchSize`:

```java
@OneToMany(mappedBy = "publisher", fetch = FetchType.LAZY)
private List<Book> books = new ArrayList<>();
```

Nó là control group để chứng minh:

```text
Default profile:
không batch -> N+1.

global-batch profile:
cùng code và mapping đó -> được batch nhờ global fallback.
```

---

## 3. Dữ liệu mẫu và lý do chọn các con số

`DataInitializer` tạo:

```text
12 Authors
24 Books
2 Books cho mỗi Author
5 Publishers
```

`Author.books` có batch size 5:

```java
@BatchSize(size = 5)
```

`Author` entity cũng có batch size 5:

```java
@Entity
@BatchSize(size = 5)
public class Author {
}
```

12 lớn hơn 5 nên demo nhìn thấy nhiều batch thật:

```text
ceil(12 / 5) = 3 batches
```

Nếu chỉ có 5 Authors và batch size 5, kết quả chỉ có một query `IN`, người xem có thể hiểu nhầm Batch Size luôn biến mọi trường hợp thành đúng hai SQL.

Dataset 12 Authors cho thấy rõ:

```text
Batch Size giảm số query theo nhóm,
nhưng số query vẫn tăng khi số key vượt quá batch size.
```

---

## 4. Ba loại batch configuration

### 4.1 `@BatchSize` trên collection

Code:

```java
@OneToMany(mappedBy = "author", fetch = FetchType.LAZY)
@BatchSize(size = 5)
private List<Book> books;
```

Nó batch:

```text
nhiều Author.books collections chưa được initialize
```

SQL dùng foreign key của collection:

```sql
select b.*
from books b
where b.author_id in (?, ?, ?, ?, ?);
```

Danh sách trong `IN` là:

```text
owner keys của collection
= Author IDs
```

Nó không batch `Book` tùy ý trên toàn hệ thống. Nó đang load các rows cần để hoàn thiện nhiều collection có cùng role:

```text
com.example.batchsize.entity.Author.books
```

### 4.2 `@BatchSize` trên entity class

Code:

```java
@Entity
@BatchSize(size = 5)
public class Author {
}
```

Nó batch:

```text
nhiều Author entity proxies/references chưa được initialize
```

Ví dụ, query Books trước:

```java
List<Book> books = bookRepository.findAll();
```

Mỗi Book chứa một lazy reference đến Author:

```java
book.getAuthor()
```

Hibernate biết `author_id` của từng Book và có thể đặt các Author references chưa load vào nhóm chờ. Khi code cần state thật của một Author:

```java
book.getAuthor().getName();
```

Hibernate batch-load Author entities:

```sql
select a.*
from authors a
where a.id in (?, ?, ?, ?, ?);
```

Khác collection batching:

```text
Collection batch:
books.author_id IN (owner IDs)

Entity batch:
authors.id IN (entity IDs)
```

### 4.3 `default_batch_fetch_size`

Profile `global-batch` có:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 7
```

Đây là global fallback:

```text
Nếu lazy entity/association không có @BatchSize cụ thể,
Hibernate có thể dùng size 7.
```

Ví dụ `Book.publisher` không có annotation riêng:

```java
@ManyToOne(fetch = FetchType.LAZY)
private Publisher publisher;
```

Khi chạy global profile:

```java
List<Book> books = bookRepository.findAll();

for (Book book : books) {
    book.getPublisher().getName();
}
```

Hibernate có thể load các Publisher bằng:

```sql
select p.*
from publishers p
where p.id in (?, ?, ?, ?, ?, ?, ?);
```

Dataset chỉ có 5 Publisher IDs khác nhau. Các placeholder còn lại có thể nhận `NULL`.

### 4.4 Cách nhớ ba vị trí

| Vị trí | Target được batch | SQL lọc theo |
|---|---|---|
| `@BatchSize` trên `Author.books` | Collection role `Author.books` | `books.author_id` |
| `@BatchSize` trên class `Author` | Author proxies/entities | `authors.id` |
| `default_batch_fetch_size` | Global fallback | Tùy target lazy được kích hoạt |

Một câu nhớ nhanh:

```text
Field collection -> batch nhiều collection owners.
Entity class      -> batch nhiều entity proxies.
Global config     -> fallback cho target không cấu hình riêng.
```

---

## 5. Batch fetching không phải JDBC batching

Hai khái niệm đều có chữ “batch” nhưng giải quyết hai bài toán khác nhau.

Hibernate Batch Size trong module này:

```text
Tối ưu SELECT khi lazy loading.
Gom nhiều IDs vào WHERE ... IN (...).
```

JDBC batching:

```text
Tối ưu nhiều INSERT/UPDATE/DELETE.
Gom nhiều parameter sets để gửi xuống database hiệu quả hơn.
```

Ví dụ JDBC write batching:

```sql
insert into books (...) values (...);
insert into books (...) values (...);
insert into books (...) values (...);
```

Cấu hình thường liên quan:

```yaml
hibernate:
  jdbc:
    batch_size: 50
```

Đó không phải `default_batch_fetch_size`.

| Tên | Loại SQL | Mục tiêu |
|---|---|---|
| `@BatchSize`, `default_batch_fetch_size` | `SELECT ... IN (...)` | Giảm lazy-loading N+1 |
| `hibernate.jdbc.batch_size` | INSERT/UPDATE/DELETE | Tối ưu ghi dữ liệu |

---

## 6. Full flow của association batch

Swagger:

```http
GET /api/batch-size/02-association-batch
```

Flow tổng thể:

```text
HTTP request
  -> BatchSizeDemoController.associationBatch()
  -> Spring transactional proxy
  -> AuthorService.demonstrateBatchSize()
  -> DemoMetrics.clear()
  -> AuthorRepository.findAll()
  -> Hibernate query Authors
  -> Persistence Context có 12 Author entities
  -> mỗi Author.books là lazy PersistentCollection chưa initialized
  -> code gọi author.getBooks().size()
  -> Hibernate nhận lazy initialization event
  -> BatchFetchQueue chọn tối đa 5 collection owner keys
  -> Hibernate tạo SQL books.author_id IN (?, ?, ?, ?, ?)
  -> JDBC bind Author IDs
  -> database trả Book rows
  -> Hibernate hydrate Book entities
  -> Hibernate chia Books theo author_id
  -> initialize 5 Author.books collections
  -> lặp lại cho các collection còn lại
  -> service kết thúc
  -> transaction commit
  -> controller trả DemoResponse
```

### 6.1 Controller

```java
@GetMapping("/02-association-batch")
public DemoResponse associationBatch() {
    authorService.demonstrateBatchSize();
    return response(...);
}
```

Controller không trực tiếp query database. Nó:

```text
chọn scenario
-> gọi service
-> trả hướng dẫn đọc console
```

SQL không nằm trong HTTP response vì mục tiêu của lab là quan sát:

```text
Hibernate SQL log
+ bind log
+ [METRICS]
```

### 6.2 Transactional service

```java
@Transactional(readOnly = true)
public void demonstrateBatchSize() {
    ...
}
```

Spring tạo proxy quanh service method:

```text
1. Mở transaction.
2. Gắn một EntityManager/Persistence Context vào thread.
3. Chạy method.
4. Commit hoặc rollback.
5. Đóng Persistence Context.
```

Transaction rất quan trọng vì lazy collection cần còn gắn với một Hibernate Session đang mở.

Module đặt:

```yaml
spring:
  jpa:
    open-in-view: false
```

Do đó lazy loading phải xảy ra trong service transaction, không được đẩy ra lúc Jackson serialize response.

### 6.3 Reset metrics và Persistence Context

```java
entityManager.clear();
statistics.clear();
```

`entityManager.clear()` tránh entity của scenario trước còn nằm trong first-level cache.

Nếu không clear:

```text
Hibernate có thể tái sử dụng entity đã load.
Một số SQL không chạy.
Metrics của scenario sau bị sai lệch.
```

`statistics.clear()` reset bộ đếm:

```text
JDBC statements
entities loaded
collections loaded
collections fetched
```

### 6.4 Repository query

```java
List<Author> authors = authorRepository.findAll();
```

Repository không có custom query:

```java
public interface AuthorRepository extends JpaRepository<Author, Long> {
}
```

Spring Data tạo repository proxy và dựng query tương đương:

```jpql
select a from Author a
```

Hibernate chuyển query entity này thành SQL:

```sql
select
    a1_0.id,
    a1_0.name
from authors a1_0;
```

SQL không JOIN books. Lý do:

```text
Author.books vẫn là LAZY.
@BatchSize không phải fetch plan cho query chính.
```

### 6.5 Hibernate tạo lazy collection wrappers

Sau SQL đầu:

```text
Database trả 12 Author rows.
Hibernate hydrate 12 Author objects.
```

Mỗi `Author.books` chưa phải `ArrayList` đã chứa Books. Hibernate thay collection bằng wrapper quản lý lazy state, thường là một `PersistentBag` vì mapping dùng `List` không có order column.

Trạng thái khái niệm:

```text
Author 1 -> books wrapper, initialized = false
Author 2 -> books wrapper, initialized = false
Author 3 -> books wrapper, initialized = false
...
Author 12 -> books wrapper, initialized = false
```

Hibernate biết:

```text
collection role = Author.books
owner key = Author.id
batch size = 5
```

Nhưng Hibernate chưa query Books.

### 6.6 Lazy trigger

Code:

```java
author.getBooks().size();
```

Gọi getter một mình chưa nhất thiết load collection:

```java
List<Book> books = author.getBooks();
```

Getter có thể chỉ trả wrapper.

Operation cần dữ liệu thật mới trigger initialization, ví dụ:

```java
books.size();
books.iterator();
books.get(0);
books.stream();
```

Khi `size()` cần biết số phần tử, wrapper yêu cầu Hibernate initialize collection.

### 6.7 Batch fetch queue

Không có batch, Hibernate chỉ cần owner key hiện tại:

```sql
where author_id = ?
```

Có batch size 5, Hibernate tìm các collection cùng role đang:

```text
- nằm trong Persistence Context;
- chưa initialized;
- đủ điều kiện batch;
- có owner keys đã biết.
```

Khái niệm bên trong Hibernate thường được mô tả bằng batch fetch queue. Nó chọn:

```text
key hiện tại + các key ứng viên khác
```

Ví dụ lần đầu:

```text
Author.books#1
Author.books#2
Author.books#3
Author.books#4
Author.books#5
```

Điểm quan trọng:

```text
Code chỉ chạm Author 1,
nhưng Hibernate có thể initialize collections của Authors 1..5.
```

Đó vừa là sức mạnh, vừa là trade-off preload.

### 6.8 SQL được tạo

Hibernate render:

```sql
select
    b1_0.author_id,
    b1_0.id,
    b1_0.publish_year,
    b1_0.publisher_id,
    b1_0.title
from books b1_0
where b1_0.author_id in (?, ?, ?, ?, ?);
```

Tại sao select có `author_id`?

```text
Hibernate cần biết mỗi Book row thuộc collection owner nào.
```

Bind lần đầu:

```text
parameter 1 <- 1
parameter 2 <- 2
parameter 3 <- 3
parameter 4 <- 4
parameter 5 <- 5
```

### 6.9 Database làm gì?

Database nhận SQL và bind values qua JDBC:

```text
SQL template:
WHERE author_id IN (?, ?, ?, ?, ?)

Values:
1, 2, 3, 4, 5
```

Database:

```text
1. Parse SQL.
2. Resolve bảng và cột.
3. Lập execution plan.
4. Tìm Book rows có author_id thuộc tập IDs.
5. Trả result rows qua JDBC ResultSet.
```

Nếu có index trên `books.author_id`, database có thể dùng nó để tìm rows hiệu quả hơn. Foreign key không phải database nào cũng tự tạo index, nên production schema cần kiểm tra index thực tế.

### 6.10 ResultSet và hydration

Với 5 Authors, mỗi Author có 2 Books, database trả 10 rows:

```text
author_id | book_id | title
----------+---------+--------
1         | 1       | Book 1A
1         | 2       | Book 1B
2         | 3       | Book 2A
2         | 4       | Book 2B
...
5         | 10      | Book 5B
```

Hibernate không tạo lại Author từ các rows này. Author objects đã tồn tại từ SQL đầu.

Hibernate:

```text
1. Hydrate 10 Book entities.
2. Đọc author_id trên từng row.
3. Đặt Books có author_id=1 vào Author#1.books.
4. Đặt Books có author_id=2 vào Author#2.books.
5. ...
6. Đánh dấu 5 collection wrappers initialized.
```

Kết quả:

```text
12 Author objects đang managed
10 Book objects mới được managed sau batch đầu
5 Author.books collections initialized
7 Author.books collections vẫn uninitialized
```

Khi loop đến Author 2..5:

```text
collection đã initialized
-> không có SQL mới
```

Khi loop đến Author 6:

```text
Hibernate lấy batch tiếp theo: 6..10
-> chạy SQL thứ hai cho Books
```

Khi loop đến Author 11:

```text
Hibernate lấy 11, 12 và các slot trống
-> chạy SQL thứ ba cho Books
```

### 6.11 Vì sao batch cuối có `NULL`?

Hibernate 6 có thể giữ query shape cố định:

```sql
where author_id in (?, ?, ?, ?, ?)
```

Batch cuối chỉ có hai IDs:

```text
11, 12
```

Bind có thể là:

```text
11, 12, null, null, null
```

Điều đó không có nghĩa Hibernate tìm record có `author_id = null`.

Trong SQL:

```text
author_id IN (11, 12, NULL, NULL, NULL)
```

vẫn match rows có ID 11 hoặc 12. Các `NULL` chỉ lấp slot.

Giữ query shape ổn định có thể giúp:

```text
- giảm số dạng SQL khác nhau;
- tăng khả năng reuse prepared statement/query plan;
- đơn giản hóa batch loader.
```

---

## 7. Scenario 01: baseline không batch

Swagger:

```http
GET /api/batch-size/01-baseline-no-batch
```

Code:

```java
var publishers = publisherRepository.findAll();

for (var publisher : publishers) {
    publisher.getBooks().size();
}
```

`Publisher.books`:

```java
@OneToMany(mappedBy = "publisher", fetch = FetchType.LAZY)
private List<Book> books;
```

Default profile không có annotation và không có global fallback.

SQL:

```sql
-- SQL 1
select p.id, p.name
from publishers p;

-- SQL 2..6
select b.*
from books b
where b.publisher_id = ?;
```

Metrics đã chạy thực tế:

```text
JDBC statements:      6
entities loaded:      29
collections loaded:   5
collections fetched:  5
```

Giải thích:

```text
entities loaded:
5 Publisher + 24 Book = 29

collections loaded:
5 Publisher.books collections được initialized

collections fetched:
5 lazy fetch events, mỗi event tạo một SQL riêng
```

Đây là control case. Không có baseline, ta chỉ thấy `IN (...)` nhanh hơn nhưng không thấy nó đã thay đổi hành vi gì.

---

## 8. Scenario 02: batch collection `Author.books`

Swagger:

```http
GET /api/batch-size/02-association-batch
```

SQL:

```sql
-- SQL 1
select a.id, a.name
from authors a;

-- SQL 2
select b.*
from books b
where b.author_id in (1, 2, 3, 4, 5);

-- SQL 3
select b.*
from books b
where b.author_id in (6, 7, 8, 9, 10);

-- SQL 4
select b.*
from books b
where b.author_id in (11, 12, null, null, null);
```

Metrics thực tế:

```text
JDBC statements:      4
entities loaded:      36
collections loaded:   12
collections fetched:  3
```

Giải thích:

```text
entities loaded:
12 Author + 24 Book = 36

collections loaded:
12 Author.books collections hoàn tất initialization

collections fetched:
3 batch fetch events

JDBC statements:
1 Author query + 3 Book queries = 4
```

So với không batch lý thuyết:

```text
Không batch: 1 + 12 = 13 statements
Batch size 5: 1 + ceil(12/5) = 4 statements
```

---

## 9. Scenario 03: batch lazy entity proxies

Swagger:

```http
GET /api/batch-size/03-entity-proxy-batch
```

Code:

```java
List<Book> books = bookRepository.findAll();

for (Book book : books) {
    book.getAuthor().getName();
}
```

SQL đầu:

```sql
select
    b.id,
    b.author_id,
    b.publisher_id,
    b.title,
    b.publish_year
from books b;
```

`Book.author` là LAZY:

```java
@ManyToOne(fetch = FetchType.LAZY)
private Author author;
```

Hibernate đã có `author_id`, nhưng chưa cần lấy `authors.name`. Nó có thể giữ lazy references:

```text
Book 1  -> Author reference ID 1, chưa initialized
Book 2  -> Author reference ID 1, chưa initialized
Book 3  -> Author reference ID 2, chưa initialized
...
```

Hai Books của cùng Author không tạo hai Author objects khác nhau. Persistence Context giữ identity:

```text
EntityKey(Author, 1) -> một managed Author/proxy duy nhất
```

Khi `getName()` cần state thật, Hibernate batch IDs:

```sql
select a.*
from authors a
where a.id in (?, ?, ?, ?, ?);
```

Metrics:

```text
JDBC statements:      4
entities loaded:      36
collections loaded:   0
collections fetched:  0
```

Giải thích:

```text
24 Book + 12 Author = 36 entities

Không load collection nào.
Book.author là một ToOne entity reference, không phải collection.
Vì vậy collection metrics đều bằng 0.
```

Đây là điểm rất dễ nhầm:

```text
@BatchSize trên Author class
không tự động batch Author.books.

Nó có tác dụng khi Hibernate cần load nhiều Author entities/proxies.
```

---

## 10. Scenario 04: global fallback

Swagger:

```http
GET /api/batch-size/04-global-batch
```

Default run:

```text
Scenario được SKIP vì global config không active.
```

Chạy app với:

```powershell
.\gradlew.bat :04-batch-size:bootRun --args="--spring.profiles.active=global-batch"
```

Sau đó gọi endpoint.

Code:

```java
List<Book> books = bookRepository.findAll();

for (Book book : books) {
    book.getPublisher().getName();
}
```

`Book.publisher` không có `@BatchSize`, nhưng global size là 7.

SQL:

```sql
select b.*
from books b;

select p.*
from publishers p
where p.id in (?, ?, ?, ?, ?, ?, ?);
```

Chỉ có 5 publisher IDs khác nhau, nên một entity batch đủ.

Metrics:

```text
JDBC statements:      2
entities loaded:      29
collections loaded:   0
collections fetched:  0
```

Giải thích:

```text
24 Book + 5 Publisher = 29 entities

Book.publisher là ManyToOne reference.
Không collection nào được load.
```

Global profile còn biến baseline `Publisher.books`:

```text
Default profile:
6 statements, 5 collection fetches

Global profile:
2 statements, 1 collection fetch
```

Cùng code, cùng entity mapping; khác biệt đến từ global fallback.

---

## 11. Scenario 05: preload trade-off

Swagger:

```http
GET /api/batch-size/05-preload-trade-off
```

Code chỉ access một collection:

```java
List<Author> authors = authorRepository.findAll();
Author firstAuthor = authors.get(0);

firstAuthor.getBooks().size();
```

Nhưng Persistence Context đang có 12 Authors và 12 lazy `Author.books` wrappers.

Hibernate chọn batch đầu:

```text
Author IDs 1, 2, 3, 4, 5
```

SQL:

```sql
select b.*
from books b
where b.author_id in (1, 2, 3, 4, 5);
```

Metrics:

```text
JDBC statements:      2
entities loaded:      22
collections loaded:   5
collections fetched:  1
```

Giải thích:

```text
12 Authors được load bởi query đầu.
10 Books của 5 Authors được preload.
12 + 10 = 22 entities.

Code trực tiếp access 1 collection.
Hibernate initialize 5 collections.
```

Đây là lý do không nên đánh giá chỉ bằng query count:

```text
2 statements trông rất tốt,
nhưng 4 collections và 8 Books được load trước khi code thực sự yêu cầu chúng.
```

Nếu ngay sau đó code dùng Authors 2..5:

```text
preload là có lợi vì không phát sinh SQL.
```

Nếu request chỉ cần Author 1:

```text
preload là dữ liệu thừa.
```

---

## 12. Scenario 06: parent pagination

Swagger:

```http
GET /api/batch-size/06-pagination
```

Code:

```java
Page<Author> page = authorRepository.findAll(PageRequest.of(0, 3));
List<Author> authors = page.getContent();

for (Author author : authors) {
    author.getBooks().size();
}
```

SQL 1 page Authors:

```sql
select
    a.id,
    a.name
from authors a
offset ? rows
fetch first ? rows only;
```

Bindings:

```text
offset = 0
size = 3
```

SQL 2 count:

```sql
select count(a.id)
from authors a;
```

Spring Data cần count để tạo metadata:

```text
totalElements = 12
totalPages = 4
```

SQL 3 batch Books:

```sql
select b.*
from books b
where b.author_id in (?, ?, ?, ?, ?);
```

Bindings:

```text
1, 2, 3, null, null
```

Metrics:

```text
JDBC statements:      3
entities loaded:      9
collections loaded:   3
collections fetched:  1
```

Giải thích:

```text
3 Authors + 6 Books = 9 entities
3 Author.books collections initialized
1 batch fetch event
3 SQL = page + count + books batch
```

### Vì sao pagination này an toàn hơn collection JOIN FETCH?

Batch Size làm:

```text
Bước 1: database paginate bảng authors.
Bước 2: chỉ với Author IDs trong page, query Books riêng.
```

Parent page được cắt trước khi child rows xuất hiện.

Collection JOIN FETCH có thể tạo:

```text
Author 1 + Book 1
Author 1 + Book 2
Author 2 + Book 3
Author 2 + Book 4
```

Nếu limit/offset áp vào joined rows, số distinct Authors trong page có thể sai. Hibernate đôi khi phải bỏ SQL-level pagination và cắt parent trong memory.

Batch Size tránh row multiplication trong parent query:

```text
Authors query chỉ có Author rows.
Books query là phase riêng.
```

### Batch Size có phải two-step pagination không?

Nó có cấu trúc hai phase:

```text
page parents
-> lazy batch children
```

Nhưng nó khác “two-step pagination” thủ công của EntityGraph:

```text
EntityGraph two-step:
1. Page parent IDs.
2. Query parent entities + children theo IDs.

Batch Size:
1. Page parent entities trực tiếp.
2. Khi code chạm lazy collection, query children theo parent IDs.
```

Batch Size không cần query lại Authors ở bước hai.

---

## 13. Full-flow Swagger endpoint

Swagger:

```http
GET /api/batch-size/00-full-flow
```

Endpoint chạy:

```text
01. Baseline không batch
02. Collection batching
03. Entity proxy batching
04. Global fallback
05. Preload trade-off
06. Parent pagination
```

Ở default profile:

```text
Scenario 04 được log là SKIPPED.
```

Ở global profile:

```text
Scenario 04 chạy thật.
Baseline scenario 01 cũng được global batch,
nên dùng nó để so sánh cùng code giữa hai lần restart.
```

Nên dùng endpoint full flow khi:

```text
- muốn chạy toàn bộ rồi đọc log từ đầu đến cuối;
- quay video demo;
- kiểm tra nhanh module sau khi sửa code.
```

Nên dùng endpoint lẻ khi:

```text
- đang thuyết trình;
- muốn console chỉ có SQL của một case;
- muốn người nghe đoán metrics trước khi xem kết quả;
- cần so sánh default và global profile.
```

---

## 14. Hiểu đúng bốn metrics

### `JDBC statements`

Số statement Hibernate chuẩn bị thực thi trong scope đo.

Nó cho biết số round-trip/query ở mức demo, nhưng không phản ánh:

```text
- số rows;
- kích thước mỗi row;
- execution plan;
- network bytes;
- database CPU;
- dữ liệu preload thừa.
```

### `entities loaded`

Số entity instances Hibernate hydrate từ database:

```text
Author
Book
Publisher
```

Nó không phải số SQL rows trong mọi trường hợp và không đếm collection wrapper như entity.

### `collections loaded`

Số persistent collections được initialize.

Ví dụ batch đầu cho 5 Authors:

```text
collections loaded = 5
```

### `collections fetched`

Số lần Hibernate fetch collection từ database.

Không batch:

```text
5 collections
5 fetch events
```

Batch:

```text
5 collections
1 fetch event
```

Đây là cặp số thể hiện batch rõ nhất:

```text
collections loaded = nhiều
collections fetched = ít hơn
```

Đối với ToOne proxy batching:

```text
Book.author
Book.publisher
```

đây không phải collection, nên cả hai collection metrics bằng 0.

---

## 15. Persistence Context là điều kiện quan trọng

Batch fetching chỉ gom được những candidates Hibernate biết trong cùng Session/Persistence Context.

Ví dụ tốt:

```java
@Transactional
public void demo() {
    List<Author> authors = authorRepository.findAll();
    authors.forEach(a -> a.getBooks().size());
}
```

Tất cả Authors cùng tồn tại trong một Persistence Context, nên Hibernate thấy nhiều uninitialized collections.

Nếu mỗi Author bị xử lý trong transaction tách biệt:

```text
Transaction 1 chỉ biết Author 1
Transaction 2 chỉ biết Author 2
Transaction 3 chỉ biết Author 3
```

Hibernate không thể gom IDs xuyên qua các Session độc lập.

Batch Size không phải một global queue gom request của nhiều users. Nó hoạt động trong loading context/session phù hợp, không đợi các HTTP request khác để đủ batch.

---

## 16. `LAZY` vẫn là `LAZY`

Sau:

```java
authorRepository.findAll();
```

nếu code không chạm `Author.books`:

```text
chỉ query Authors
không query Books
```

`@BatchSize` chỉ ảnh hưởng cách load khi lazy load đã được trigger.

So sánh:

```text
EAGER:
mapping yêu cầu association được load trong fetch plan mặc định.

EntityGraph/JOIN FETCH:
query/use case hiện tại yêu cầu fetch association ngay.

Batch Size:
association vẫn lazy; khi cần thì load theo nhóm.
```

Batch Size trả lời:

```text
Nếu phải lazy-load, tải bao nhiêu targets cùng lúc?
```

Nó không trả lời:

```text
Request này có chắc chắn cần association hay không?
```

---

## 17. Batch Size so với EntityGraph và JOIN FETCH

EntityGraph/JOIN FETCH:

```sql
select a.*, b.*
from authors a
left join books b on b.author_id = a.id;
```

Batch Size:

```sql
select a.*
from authors a;

select b.*
from books b
where b.author_id in (?, ?, ?, ?, ?);
```

| Tiêu chí | EntityGraph/JOIN FETCH | Batch Size |
|---|---|---|
| Thời điểm lấy child | Query chính | Khi lazy access |
| SQL shape | JOIN | Query riêng với IN |
| Số round-trip | Thường ít hơn | Thường từ 2 trở lên |
| Parent row multiplication | Có với ToMany JOIN | Không ở parent query |
| Parent pagination | Có thể khó với ToMany | Tự nhiên hơn |
| Load nếu không access child | Graph vẫn fetch | Không |
| Filter theo child trong query | Thuận tiện với JOIN/JPQL | Batch không thay query chính |
| Nhiều collections | Có cartesian/multi-bag risk | Có thể load riêng từng collection role |

Câu nhớ nhanh:

```text
EntityGraph/JOIN FETCH:
"Tôi biết query này cần dữ liệu liên quan ngay."

Batch Size:
"Tôi muốn giữ lazy, nhưng nếu lazy load xảy ra thì đừng query từng cái."
```

---

## 18. Batch Size so với `FetchMode.SUBSELECT`

Batch:

```sql
where author_id in (?, ?, ?, ?, ?)
```

Subselect:

```sql
where author_id in (
    select a.id
    from authors a
    where ...
)
```

Batch xử lý theo từng nhóm cố định.

Subselect thường dùng query/result parent trước làm phạm vi để load collections.

Trade-off:

```text
Batch:
kiểm soát kích thước nhóm, có thể cần nhiều query.

Subselect:
có thể chỉ cần một collection query,
nhưng có thể load child cho toàn bộ parent result rộng hơn nhu cầu.
```

---

## 19. Ưu điểm

- Giảm N+1 mà vẫn giữ association `LAZY`.
- Không nhân parent rows như collection JOIN.
- Hợp với parent pagination.
- Hữu ích cho cả ToMany collections và ToOne entity proxies.
- Có thể dùng global fallback rồi override target quan trọng bằng annotation.
- Không cần sửa repository query cho mỗi access pattern.
- Có thể tránh cartesian product khi nhiều collections được load bằng các SQL riêng.
- Query `IN` dễ quan sát và giải thích trong log.

---

## 20. Nhược điểm

- Hibernate-specific; `@BatchSize` không phải JPA chuẩn.
- Không đảm bảo chỉ một SQL.
- Có thể preload entities/collections chưa thực sự cần.
- Batch quá nhỏ vẫn còn nhiều round-trips.
- Batch quá lớn tạo `IN` list dài, nhiều parameters và tải nhiều data.
- Hiệu quả phụ thuộc candidates trong Persistence Context.
- Không thay thế query rõ ràng khi cần filter/order theo child.
- Không tự giải quyết serialization/lazy loading ngoài transaction.
- Query count thấp không đồng nghĩa response nhanh hơn nếu collections rất lớn.
- Global setting có thể tác động rộng và làm behavior khó nhìn từ entity code.

---

## 21. Chọn batch size như thế nào?

Không có một con số đúng cho mọi hệ thống.

Ví dụ 100 parent candidates:

```text
size 5   -> tối đa 20 batch queries
size 20  -> tối đa 5 batch queries
size 50  -> tối đa 2 batch queries
size 100 -> tối đa 1 batch query
```

Nhưng size lớn hơn không tự động tốt hơn:

```text
- IN list dài hơn;
- nhiều bind parameters hơn;
- có thể chạm database/driver limits;
- preload nhiều data hơn;
- Persistence Context giữ nhiều objects hơn;
- collection child có thể rất lớn.
```

Cách chọn thực tế:

```text
1. Đo page/result size thông thường.
2. Đo số target lazy thường được access cùng nhau.
3. Đo số rows trung bình và p95/p99 của collection.
4. Bắt đầu bằng giá trị vừa phải như 16, 25 hoặc 32.
5. Kiểm tra SQL count, rows, latency và memory.
6. Kiểm tra execution plan/index.
7. Tune theo production workload.
```

Các con số trên là điểm bắt đầu, không phải chuẩn bắt buộc.

---

## 22. Index và database performance

Collection batch query:

```sql
where books.author_id in (...)
```

Index hữu ích:

```sql
create index idx_books_author_id on books(author_id);
```

Publisher collection:

```sql
where books.publisher_id in (...)
```

Index:

```sql
create index idx_books_publisher_id on books(publisher_id);
```

Entity batch:

```sql
where authors.id in (...)
```

Primary key đã có index theo cơ chế database thông thường.

Batch Size giảm round-trip, nhưng nếu foreign-key column không có index và bảng child rất lớn, database vẫn có thể scan nhiều dữ liệu. ORM optimization không thay thế schema/index optimization.

---

## 23. Cách đọc log đúng thứ tự

### Bước 1: tìm parent query

```sql
from authors
```

Xác nhận chưa có JOIN Books.

### Bước 2: tìm lazy trigger trong service

```java
author.getBooks().size()
```

SQL Books phải xuất hiện sau trigger này, không phải ngay sau `findAll()` vì mapping là LAZY.

### Bước 3: tìm `IN`

```sql
where b1_0.author_id in (?, ?, ?, ?, ?)
```

Xác định:

```text
cột trong IN là gì?
entity ID hay collection owner foreign key?
```

### Bước 4: đọc binding

```text
binding parameter (1:BIGINT) <- [1]
...
```

Bindings cho biết batch đang gom IDs nào.

### Bước 5: đọc metrics

```text
JDBC statements
entities loaded
collections loaded
collections fetched
```

Đối chiếu với dataset, không chỉ nhìn query count.

---

## 24. Các hiểu lầm thường gặp

### “Batch size 5 nghĩa là luôn lấy đúng 5 rows”

Sai.

```text
5 là số entity keys hoặc collection owner keys tối đa trong batch,
không phải số Book rows tối đa.
```

Nếu mỗi Author có 100 Books:

```text
5 owner IDs có thể trả 500 Book rows.
```

### “Có `@BatchSize` thì `findAll()` tự load Books”

Sai.

```text
Books vẫn LAZY.
Phải có operation trigger collection initialization.
```

### “Một Book tương ứng một Author query candidate”

Không hoàn toàn.

Hai Books có cùng `author_id` dùng chung Author identity trong Persistence Context. Entity batch dựa trên distinct entity keys cần load.

### “Global size 7 sẽ override mọi annotation size 5”

Không nên hiểu như vậy.

Global config là fallback. Mapping cụ thể trên entity/collection định nghĩa batch size cho target đó.

### “`collections fetched = 1` nghĩa là chỉ một collection được load”

Sai.

Một batch fetch event có thể initialize nhiều collections:

```text
collections fetched = 1
collections loaded = 5
```

### “Batch Size luôn nhanh hơn JOIN FETCH”

Sai.

Nếu chắc chắn cần một Author và Books của nó, một JOIN fetch có thể đơn giản và hiệu quả hơn. Chọn theo access pattern và data cardinality.

---

## 25. Thứ tự demo khuyến nghị

### Demo đầy đủ

1. Gọi `01-baseline-no-batch`.
2. Chỉ ra 1 Publisher query + 5 Book queries.
3. Gọi `02-association-batch`.
4. Chỉ ra cùng lazy access pattern nhưng Books được gom theo `author_id IN (...)`.
5. Giải thích `12 / size 5 -> 3 batch fetches`.
6. Gọi `03-entity-proxy-batch`.
7. Nhấn mạnh SQL đổi từ `books.author_id IN` sang `authors.id IN`.
8. Gọi `05-preload-trade-off`.
9. Chỉ ra code access 1 nhưng `collections loaded = 5`.
10. Gọi `06-pagination`.
11. Chỉ ra page parent trước, Books sau, không có joined-row pagination.
12. Restart với profile `global-batch`.
13. Gọi lại baseline: `6 -> 2 statements`.
14. Gọi `04-global-batch` để thấy unannotated `Book.publisher` được batch.

### Demo nhanh

1. Baseline.
2. Association batch.
3. Preload trade-off.
4. Pagination.
5. Chốt ba cấp configuration.

### Một câu nói ở từng endpoint

```text
01:
Đây là N+1 thật: một fetch event cho mỗi Publisher.books.

02:
Association vẫn lazy, nhưng một fetch event initialize tối đa 5 collections.

03:
Annotation trên class batch entity proxies, không phải collections.

04:
Global config là fallback cho target không có annotation riêng.

05:
Giảm query đổi lại nguy cơ preload thừa.

06:
Parent được paginate trước; child được batch-load sau.
```

---

## 26. Cách chạy

Default profile:

```powershell
.\gradlew.bat :04-batch-size:bootRun
```

Swagger:

```text
http://localhost:8084/swagger-ui.html
```

Full flow:

```http
GET /api/batch-size/00-full-flow
```

Global profile:

```powershell
.\gradlew.bat :04-batch-size:bootRun --args="--spring.profiles.active=global-batch"
```

Console auto-run không cần Swagger:

```powershell
.\gradlew.bat :04-batch-size:bootRun --args="--demo.auto-run=true"
```

---

## 27. Proxy Design Pattern trong module

### 27.1 Proxy Design Pattern là gì?

Proxy là một object đại diện đứng trước object thật.

Flow tổng quát:

```text
Caller
  -> Proxy
       -> thêm hành vi trước lời gọi
       -> gọi Real Subject
       -> thêm hành vi sau lời gọi
  <- kết quả
```

Caller tưởng rằng nó đang làm việc với object thật vì Proxy cung cấp cùng contract hoặc có thể thay thế object thật tại vị trí sử dụng.

Pseudo-code:

```java
interface AuthorOperations {
    Author findById(Long id);
}

class RealAuthorOperations implements AuthorOperations {
    @Override
    public Author findById(Long id) {
        return queryDatabase(id);
    }
}

class AuthorOperationsProxy implements AuthorOperations {
    private final RealAuthorOperations target;

    @Override
    public Author findById(Long id) {
        openTransaction();
        try {
            Author result = target.findById(id);
            commitTransaction();
            return result;
        } catch (RuntimeException exception) {
            rollbackTransaction();
            throw exception;
        }
    }
}
```

Business method không cần tự viết transaction boilerplate. Proxy intercept method call và bổ sung behavior.

Ba động từ cần nhớ:

```text
Intercept:
Proxy chặn lời gọi trước khi target nhận được.

Delegate:
Proxy chuyển lời gọi cho target thật.

Enhance:
Proxy bổ sung transaction, query generation, lazy loading, logging,
security, caching hoặc behavior khác.
```

Proxy không nhất thiết là một source file do lập trình viên viết. Framework có thể tạo implementation hoặc subclass lúc runtime bằng reflection, JDK dynamic proxy hay bytecode generation.

### 27.2 Module có ba loại proxy khác nhau

```text
HTTP
  -> Spring transactional proxy
       -> AuthorService thật
            -> Spring Data repository proxy
                 -> EntityManager/Hibernate
                      -> Hibernate lazy entity proxy
                           -> Author entity thật
```

| Proxy | Đại diện cho | Khi intercept | Behavior thêm vào |
|---|---|---|---|
| Spring transaction proxy | Service bean | Khi caller bên ngoài gọi service method | Mở, commit hoặc rollback transaction |
| Spring Data repository proxy | Repository interface | Khi service gọi repository method | Chọn query strategy và gọi JPA/Hibernate |
| Hibernate lazy proxy | Entity chưa được load đầy đủ | Khi code truy cập state cần dữ liệu thật | Chạy lazy SELECT và delegate tới entity đã load |

Ba proxy cùng dùng một pattern tổng quát, nhưng không phải cùng một object và không giải quyết cùng một bài toán.

### 27.3 Transaction proxy quanh `AuthorService`

Code:

```java
@Transactional(readOnly = true)
public void demonstrateClassLevelBatchSize() {
    ...
}
```

Spring không sửa source code của method thành:

```java
begin();
try {
    demonstrateClassLevelBatchSize();
    commit();
} catch (...) {
    rollback();
}
```

Thay vào đó, bean mà Controller nhận về là một proxy có behavior tương đương:

```text
Controller
  -> AuthorService proxy
       -> đọc metadata @Transactional
       -> mở transaction
       -> gọi AuthorService target
       -> commit hoặc rollback
```

Spring xác nhận declarative transaction hoạt động bằng AOP proxy kết hợp `TransactionInterceptor` và `TransactionManager`. Xem [Spring declarative transaction implementation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html).

Trong endpoint full flow, Controller gọi từng service method:

```java
authorService.demonstrateBaselineWithoutBatch();
authorService.demonstrateBatchSize();
authorService.demonstrateClassLevelBatchSize();
```

Các lời gọi đi từ Controller qua Spring bean proxy, nên mỗi `@Transactional` method được intercept đúng cách.

#### Self-invocation trap

Nếu một method trong chính `AuthorService` gọi method khác bằng `this`:

```java
public void fullFlowInsideService() {
    this.demonstrateBatchSize();
}
```

Flow thực tế:

```text
AuthorService target
  -> this.demonstrateBatchSize()
```

Lời gọi không quay ra ngoài rồi đi qua proxy:

```text
AuthorService target
  -X-> transaction proxy
```

Với Spring proxy mode mặc định, self-invocation không được intercept lại. Vì vậy annotation trên method được gọi nội bộ có thể không tạo transaction boundary mới như người viết kỳ vọng. Spring ghi rõ proxy mode chỉ intercept calls đi qua proxy; local calls trong cùng class không được intercept. Xem [Spring `@Transactional` proxy semantics](https://docs.spring.io/spring/reference/6.2/data-access/transaction/declarative/annotations.html).

Đây là lý do endpoint `00-full-flow` gọi lần lượt các public service methods từ Controller thay vì tạo một service method rồi self-invoke tất cả methods còn lại.

### 27.4 JDK dynamic proxy và class-based proxy

Spring AOP có hai cơ chế phổ biến:

```text
JDK dynamic proxy:
proxy các interfaces.

Class-based proxy:
tạo subclass runtime của target class.
```

Repository:

```java
public interface AuthorRepository extends JpaRepository<Author, Long> {
}
```

phù hợp tự nhiên với interface-based proxy.

`AuthorService` không implement interface, nên Spring có thể dùng class-based proxy tùy cấu hình runtime.

Giới hạn quan trọng của class-based proxy:

```text
final class:
không thể subclass.

final method:
không thể override để intercept.

private method:
không thể override để intercept.
```

Spring mô tả JDK dynamic proxy, class-based proxy và các giới hạn này trong [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).

### 27.5 Spring Data repository proxy

Ta chỉ khai báo interface:

```java
public interface AuthorRepository extends JpaRepository<Author, Long> {
}
```

Không có source code:

```java
class AuthorRepositoryImpl {
    List<Author> findAll() {
        ...
    }
}
```

Spring Data tạo repository proxy lúc startup.

Khi service gọi:

```java
authorRepository.findAll();
```

proxy:

```text
1. Nhận Method object và arguments.
2. Xác định đây là method do JpaRepository cung cấp.
3. Delegate tới repository implementation phù hợp.
4. Repository dùng EntityManager.
5. Hibernate tạo và execute query.
6. Proxy trả List<Author> cho service.
```

Nếu là derived query:

```java
findByNameContaining(String keyword)
```

repository infrastructure còn parse method name để tạo query model phù hợp.

Proxy giúp code phía service chỉ phụ thuộc contract:

```java
authorRepository.findAll()
```

thay vì tự quản lý `EntityManager`, JPQL, exception translation và boilerplate.

### 27.6 Hibernate lazy entity proxy

Scenario:

```http
GET /api/batch-size/03-entity-proxy-batch
```

Query đầu load Books:

```java
List<Book> books = bookRepository.findAll();
```

SQL Book đã chứa:

```text
books.author_id
```

Hibernate vì vậy biết:

```text
Book#1 trỏ tới Author ID 1
```

nhưng chưa biết:

```text
Author#1.name
```

Vì `Book.author` là LAZY:

```java
@ManyToOne(fetch = FetchType.LAZY)
private Author author;
```

Hibernate có thể đặt một lazy proxy vào field:

```text
Book#1.author
  -> Author proxy
       id = 1
       initialized = false
       delegate = chưa có
```

Hibernate documentation mô tả proxy chưa fetch giữ identifier/foreign key. Đọc identifier thường không cần fetch; gọi method cần state khác sẽ fetch delegate rồi chuyển tiếp lời gọi. Xem [Hibernate proxy and lazy association behavior](https://docs.jboss.org/hibernate/orm/current/javadocs/org/hibernate/Hibernate.html).

#### Gọi `getAuthor()` chưa chắc chạy SQL

```java
Author reference = book.getAuthor();
```

Operation này có thể chỉ trả proxy.

Đọc identifier:

```java
reference.getId();
```

thường có thể dùng ID đã nằm trong proxy mà không initialize entity.

Nhưng:

```java
reference.getName();
```

cần state chưa có. Proxy yêu cầu Hibernate initialize Author.

Không Batch Size:

```sql
select a.*
from authors a
where a.id = ?;
```

Có `@BatchSize(size = 5)` trên `Author`:

```sql
select a.*
from authors a
where a.id in (?, ?, ?, ?, ?);
```

Flow:

```text
book.getAuthor().getName()
  -> Author proxy intercept getName()
  -> proxy phát hiện initialized = false
  -> Hibernate Session tìm các Author proxies chưa initialized
  -> batch tối đa 5 IDs
  -> SQL authors.id IN (...)
  -> hydrate Author entities
  -> gắn delegate/state cho proxies
  -> proxy delegate getName() tới Author đã load
  -> trả String
```

Proxy là cơ chế giúp lazy trigger trong code trông gần như một method call Java bình thường, dù phía sau có thể phát sinh database I/O.

### 27.7 Lazy collection có phải entity proxy không?

Không hoàn toàn.

`Book.author`:

```text
single entity reference
-> Hibernate entity proxy hoặc enhanced unloaded entity
```

`Author.books`:

```text
collection
-> Hibernate PersistentCollection wrapper, trong mapping này thường là PersistentBag
```

`PersistentBag` có behavior “proxy-like” vì nó intercept:

```java
size()
iterator()
get(0)
```

để trigger collection initialization. Nhưng về type và cơ chế Hibernate, nên gọi nó là persistent collection wrapper thay vì entity proxy.

```text
ToOne LAZY:
proxy một entity target.

ToMany LAZY:
wrapper một collection.
```

Batch Size hoạt động với cả hai:

```text
entity keys
hoặc
collection owner keys
```

### 27.8 Bytecode enhancement là cơ chế khác

Không phải mọi lazy association luôn được biểu diễn bằng một subclass proxy.

Hibernate có thể dùng build-time bytecode enhancement:

```text
entity instance thật ban đầu ở unloaded state;
field access được instrument;
khi đọc state chưa load, interceptor trong enhanced entity kích hoạt fetch.
```

Khi không dùng bytecode enhancement, Hibernate thường dùng proxy object cho lazy ToOne. Hibernate documentation phân biệt hai cơ chế này trong [Hibernate utility and proxy documentation](https://docs.jboss.org/hibernate/orm/current/javadocs/org/hibernate/Hibernate.html).

Mental model Proxy Design Pattern vẫn hữu ích:

```text
một lớp trung gian chặn access
-> trì hoãn việc lấy object state thật
```

nhưng implementation runtime có thể là:

```text
- proxy object riêng;
- bytecode-enhanced entity;
- persistent collection wrapper.
```

### 27.9 Proxy và Persistence Context

Persistence Context giữ identity map:

```text
EntityKey(Author, 1) -> một managed identity
```

Hai Books cùng `author_id = 1` không nên tạo hai Author managed objects độc lập.

Hibernate phối hợp:

```text
proxy identity
+ Persistence Context
+ BatchFetchQueue
```

để:

```text
- reuse cùng Author identity;
- biết proxy nào chưa initialized;
- gom các IDs phù hợp cho batch fetch;
- gắn hydrated state trở lại đúng managed identity.
```

### 27.10 Các bẫy của Hibernate proxy

#### Session đã đóng

Nếu proxy chưa initialized nhưng transaction/Session đã kết thúc:

```java
author.getName();
```

có thể ném `LazyInitializationException`.

Proxy biết ID nhưng không còn Session/Connection context để load state.

Module tránh điều này bằng:

```yaml
spring.jpa.open-in-view: false
```

và access lazy associations bên trong transactional service.

#### `getClass()` và type checks

Proxy runtime class có thể không giống concrete entity class theo cách code naïve kỳ vọng.

Code generic cần cẩn thận với:

```java
object.getClass()
instanceof
type casting
```

đặc biệt khi entity có inheritance/polymorphism. Hibernate cung cấp helpers như `Hibernate.getClass(...)` và `Hibernate.unproxy(...)`, nhưng một số helpers có thể initialize proxy.

#### `equals()` và `hashCode()`

Không nên dựa vào exact runtime proxy class trong equality:

```java
getClass() == other.getClass()
```

nếu entity có thể xuất hiện dưới dạng proxy.

Equality của entity còn có vấn đề riêng về generated ID trước/sau persist, nên phải thiết kế nhất quán.

#### Serialization

Nếu trả entity trực tiếp cho Jackson:

```text
Jackson gọi getters
-> getters có thể initialize proxy
-> phát sinh SQL ngoài dự kiến
```

Hoặc Session đã đóng:

```text
-> LazyInitializationException
```

DTO giúp response boundary rõ hơn:

```text
Service chủ động load/map dữ liệu cần thiết trong transaction
-> Controller trả DTO
```

#### `final`

Hibernate User Guide lưu ý class `final` không thể được subclass để tạo proxy theo cơ chế subclass proxy thông thường; Hibernate có các lựa chọn khác như interface proxy hoặc bytecode enhancement tùy mapping/build. Xem [Hibernate custom entity proxy guidance](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html).

### 27.11 Proxy khác Decorator và Adapter như thế nào?

| Pattern | Mục tiêu chính |
|---|---|
| Proxy | Kiểm soát/trì hoãn/quản lý access tới object thật |
| Decorator | Bổ sung responsibility theo cách compose nhiều lớp behavior |
| Adapter | Chuyển một interface sang interface caller mong đợi |

Trong thực tế framework, ranh giới implementation có thể trông giống nhau vì đều wrap/delegate. Điểm phân biệt nằm ở intent.

Trong module:

```text
Transaction proxy:
kiểm soát access tới service call bằng transaction boundary.

Repository proxy:
cung cấp object đại diện cho repository contract.

Lazy proxy:
kiểm soát thời điểm entity state thật được load.
```

### 27.12 Câu chốt Proxy Design Pattern

```text
Proxy cho phép caller gọi một object đại diện như object thật.
Object đại diện intercept lời gọi, thực hiện behavior bổ sung,
rồi mới delegate tới target thật.

Spring dùng proxy để thêm transaction và repository behavior.
Hibernate dùng proxy/wrapper/interceptor để trì hoãn lazy loading.

Batch Size không tạo ra proxy.
Batch Size tận dụng tập lazy proxies/collections Hibernate đang quản lý
để khi một target bị initialize, nhiều target khác được fetch cùng batch.
```

---

## 28. Giới hạn của `IN (...)` và batch size

### 28.1 Không có một giới hạn chung cho mọi database

Câu hỏi:

```text
IN tối đa bao nhiêu giá trị?
```

không có một đáp án duy nhất.

Phải tách ít nhất bốn giới hạn:

```text
1. Số expressions tối đa trong một IN predicate.
2. Tổng số bind parameters tối đa trong một PreparedStatement.
3. Kích thước tối đa của SQL/message/network packet.
4. Ngưỡng performance hợp lý trước khi chạm hard limit.
```

Một query có thể chưa chạm hard limit nhưng đã chậm vì:

```text
- SQL quá dài;
- parse/plan cost tăng;
- cardinality estimate kém;
- index access không còn tối ưu;
- trả quá nhiều rows;
- hydrate quá nhiều entities;
- Persistence Context phình lớn.
```

### 28.2 IN expressions khác total parameters

Query đơn giản:

```sql
select b.*
from books b
where b.author_id in (?, ?, ?, ?, ?);
```

có:

```text
5 IN expressions
5 bind parameters
```

Nhưng query thực tế có thể là:

```sql
select b.*
from books b
where b.author_id in (?, ?, ?, ?, ?)
  and b.publish_year >= ?
  and b.title like ?;
```

có:

```text
5 parameters cho IN
+ 2 parameters khác
= 7 total parameters
```

Nếu database/driver giới hạn tổng parameters là `L`, không được dùng toàn bộ `L` slots cho IN.

Composite key còn nhân parameter count:

```text
number of JDBC parameters
= number of keys × number of key columns
```

Ví dụ 500 entity keys, mỗi ID gồm 3 columns:

```text
500 × 3 = 1,500 parameters
```

Hibernate `MultiKeyLoadSizingStrategy` cũng mô tả chính phép nhân này khi xác định kích thước load và có thể trả size nhỏ hơn tổng keys để chia thành nhiều SQL. Xem [Hibernate MultiKeyLoadSizingStrategy](https://docs.hibernate.org/orm/7.0/javadocs/org/hibernate/loader/ast/spi/MultiKeyLoadSizingStrategy.html).

### 28.3 Ví dụ giới hạn theo database

Các con số phụ thuộc version và query shape. Luôn kiểm tra đúng database version đang deploy.

| Database | Điều cần chú ý | Ý nghĩa thực tế |
|---|---|---|
| Oracle 19c/21c | Error documentation của `ORA-01795` nêu tối đa 1,000 expressions trong list | Batch/key list không được giả định vượt 1,000 |
| Oracle 23ai SQL Reference | Simple comma-delimited expression list được tài liệu hóa tối đa 65,535; tuple/set list vẫn có rule riêng | Oracle version thay đổi rule, không dùng một con số cho mọi release |
| SQL Server | Microsoft công bố tối đa 2,100 parameters cho stored procedure/function; parameterized commands còn phải tính toàn bộ statement/driver behavior | Không dành toàn bộ 2,100 slots cho một IN list |
| PostgreSQL | Extended query protocol mã hóa số parameter values bằng trường `Int16` | Protocol/driver có ceiling; đây không phải batch size khuyến nghị |
| H2 trong demo | Batch size chỉ là 5 | Lab không tiến gần hard limit; mục tiêu là nhìn rõ SQL shape |

Nguồn chính thức:

- [Oracle ORA-01795](https://docs.oracle.com/en/error-help/db/ora-01795/?r=21c).
- [Oracle 23ai Expression Lists](https://docs.oracle.com/en/database/oracle/oracle-database/23/sqlrf/Expression-Lists.html).
- [SQL Server maximum capacity specifications](https://learn.microsoft.com/en-us/sql/sql-server/maximum-capacity-specifications-for-sql-server?view=sql-server-ver17).
- [PostgreSQL protocol message formats](https://www.postgresql.org/docs/current/protocol-message-formats.html).

Các con số trên là hard/protocol constraints được tài liệu hóa cho các context cụ thể, không phải recommendation rằng nên dùng batch size gần bằng chúng.

### 28.4 Hibernate biết giới hạn bằng cách nào?

Hibernate `Dialect` đại diện khả năng và cú pháp của database.

Hai methods quan trọng:

```java
dialect.getInExpressionCountLimit();
dialect.getParameterCountLimit();
```

Ý nghĩa:

```text
getInExpressionCountLimit():
giới hạn số elements trong một IN predicate.

getParameterCountLimit():
giới hạn parameters trong PreparedStatement.
```

Hibernate Javadocs quy định non-positive value biểu thị Dialect không khai báo limit; mặc định parameter limit có thể theo IN-expression limit. Xem [Hibernate Dialect limits](https://docs.jboss.org/hibernate/orm/6.6/javadocs/org/hibernate/dialect/Dialect.html).

Hibernate còn có multi-key sizing strategy:

```text
total keys = 12
optimal batch size = 5

-> query 1: 5 keys
-> query 2: 5 keys
-> query 3: 2 keys
```

Strategy có thể chọn size nhỏ hơn tổng keys để tránh database/driver parameter limits.

### 28.5 Hibernate có luôn tự cứu nếu cấu hình quá lớn không?

Không nên dựa vào giả định:

```text
Cứ đặt @BatchSize(size = 10000),
Hibernate chắc chắn tự sửa mọi query cho mọi database.
```

Lý do:

```text
- Query paths và Hibernate versions có thể dùng sizing mechanisms khác nhau.
- Dialect phải báo limit đúng.
- Native SQL hoặc application-built IN query có thể không đi qua cùng loader.
- Query còn có parameters khác.
- Database hard limit không phải vấn đề duy nhất; performance có thể tệ trước đó.
- Explicit batch size vẫn thể hiện intent của application và cần phù hợp workload.
```

Hibernate có infrastructure để nhận biết Dialect limits và chia multi-key load, nhưng application vẫn phải:

```text
- dùng đúng Dialect;
- test SQL thật trên database thật;
- chọn batch size hợp lý;
- không xem auto-splitting là lý do cấu hình cực lớn.
```

### 28.6 Batch size không giới hạn số rows trả về

Đây là nhầm lẫn quan trọng nhất.

```java
@BatchSize(size = 5)
private List<Book> books;
```

`5` nghĩa là:

```text
tối đa khoảng 5 collection owner keys trong batch này
```

Nó không có nghĩa:

```text
tối đa 5 Book rows
```

Nếu:

```text
Author 1 có 100 Books
Author 2 có 100 Books
Author 3 có 100 Books
Author 4 có 100 Books
Author 5 có 100 Books
```

thì:

```sql
where author_id in (1, 2, 3, 4, 5)
```

có thể trả:

```text
500 Book rows
```

Do đó cần đo hai dimensions:

```text
key batch size
× child cardinality mỗi key
= potential result rows
```

Batch size 25 có thể nhẹ với ToOne entity proxy:

```text
tối đa khoảng 25 entity rows
```

nhưng rất nặng với collection trung bình 1,000 rows/owner:

```text
25 × 1,000 = 25,000 child rows
```

### 28.7 Parameter padding và giới hạn

Module thấy:

```sql
where author_id in (?, ?, ?, ?, ?)
```

Batch cuối:

```text
11, 12, null, null, null
```

Padding giữ SQL shape ổn định, nhưng padded slots vẫn là placeholders trong statement.

Khi đánh giá parameter limit, phải tính:

```text
số placeholders được render
```

không chỉ số non-null IDs.

Ví dụ:

```text
2 real IDs
3 padded NULLs
= 5 JDBC placeholders
```

### 28.8 Ví dụ khi cấu hình quá lớn

```java
@BatchSize(size = 2000)
private List<Book> books;
```

Rủi ro:

```text
1. Oracle version cũ có thể chạm IN-expression limit.
2. SQL Server query có thể tiến gần/exceed total parameter capacity.
3. Composite key làm parameter count nhân lên.
4. Collection có thể trả hàng trăm nghìn rows.
5. Persistence Context giữ quá nhiều Book objects.
6. Garbage collection và response latency tăng.
7. Long IN list có thể cho execution plan kém.
```

Giảm từ 100 lazy queries xuống 1 không đảm bảo nhanh hơn nếu query duy nhất tải lượng dữ liệu khổng lồ.

### 28.9 Cách chọn batch size an toàn

Không bắt đầu từ database hard limit.

Hãy bắt đầu từ access pattern:

```text
1. Một page thường có bao nhiêu parents?
2. Bao nhiêu lazy targets thực sự được access?
3. Mỗi collection trung bình/p95 có bao nhiêu children?
4. Entity ID có mấy columns?
5. Query có thêm bao nhiêu parameters?
6. Database production và version nào?
```

Ví dụ page 20 Authors:

```text
batch size 20 hoặc 25
```

có thể là điểm bắt đầu tự nhiên hơn 1,000.

Sau đó đo:

```text
- JDBC statement count;
- bind parameter count;
- rows returned;
- query duration;
- execution plan;
- entities/collections loaded;
- heap/GC nếu workload lớn;
- database CPU và network.
```

Guideline thực dụng:

```text
Batch size nên gần với số targets thường được access cùng nhau,
không gần với hard limit lớn nhất database cho phép.
```

### 28.10 Khi cần xử lý rất nhiều IDs

Nếu use case thật sự có hàng nghìn/hàng chục nghìn IDs, Batch Size có thể không còn là abstraction tốt nhất.

Cân nhắc:

```text
- chia application request thành chunks rõ ràng;
- temporary table;
- table-valued parameter trên SQL Server;
- SQL array parameter nếu database/driver hỗ trợ;
- join với staging table;
- bulk/read model query chuyên dụng;
- streaming hoặc keyset pagination.
```

Hibernate multi-key loading documentation cũng lưu ý SQL array parameter có thể phù hợp hơn IN-list nếu database/driver hỗ trợ.

### 28.11 Checklist khi thấy IN list lớn

```text
[ ] Đây là Batch Size, derived query hay native query?
[ ] Có bao nhiêu placeholders thật trong SQL?
[ ] Có composite key không?
[ ] Query còn parameters khác không?
[ ] Dialect hiện tại có đúng database/version không?
[ ] Database quy định IN-expression limit bao nhiêu?
[ ] Database/driver quy định total parameter limit bao nhiêu?
[ ] Query trả bao nhiêu rows?
[ ] Collection cardinality trung bình và p95/p99 là bao nhiêu?
[ ] Có index trên foreign-key/filter column không?
[ ] Execution plan dùng index hay full scan?
[ ] Có preload nhiều targets không được sử dụng không?
[ ] Có nên dùng chunk/temp table/array/TVP thay vì IN không?
```

### 28.12 Câu chốt về giới hạn IN

```text
Batch size là số keys Hibernate cố gom cho một fetch,
không phải số rows tối đa.

IN-expression limit và total PreparedStatement parameter limit
là hai giới hạn khác nhau, phụ thuộc database và version.

Hibernate Dialect có metadata và sizing strategy để tôn trọng limits,
nhưng application vẫn phải chọn batch size theo access pattern,
child cardinality và database production thực tế.
```

---

## 29. Mental model cuối cùng

Hãy hình dung Hibernate có một danh sách các “lazy targets” mà Session đã biết:

```text
Collection targets:
Author.books#1
Author.books#2
Author.books#3
...

Entity targets:
Author#1 proxy
Author#2 proxy
Author#3 proxy
...
```

Không batch:

```text
Chạm target nào -> query target đó.
```

Có batch:

```text
Chạm target nào
-> lấy target đó cùng một số target tương thích đang chờ
-> gom keys
-> SELECT ... WHERE key IN (...)
-> hydrate rows
-> initialize nhiều targets cùng lúc.
```

Câu kết luận:

```text
Batch Size là chiến lược giảm lazy-loading N+1 bằng cách gom entity keys
hoặc collection owner keys thành các nhóm cho câu SQL IN (...).

Nó giữ LAZY, tránh parent row multiplication và hợp với pagination,
nhưng không miễn phí: vẫn có nhiều query và có thể preload thừa.
```
