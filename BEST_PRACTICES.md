# N+1 Problem Best Practices: Chọn Giải Pháp Nào Và Vì Sao

Tài liệu này không chỉ liệt kê các cách xử lý N+1. Mục tiêu chính là đưa ra cách phân tích vấn đề, hiểu context, chọn solution phù hợp, và giải thích vì sao không dùng các approach khác trong từng trường hợp.

Thông điệp quan trọng nhất:

```text
Không có một solution luôn luôn tốt nhất.
Best practice là chọn solution theo access pattern:
- API cần trả dữ liệu gì?
- Có cần full entity không?
- Có pagination không?
- Có bao nhiêu collection?
- Data lớn hay nhỏ?
- Có cần update entity sau khi load không?
```

## Executive Summary

Khuyến nghị thực tế:

| Context | Best practice nên chọn | Vì sao |
|---------|------------------------|--------|
| API read-only, chỉ cần vài field | DTO Projection | Nhẹ nhất, select đúng dữ liệu cần trả về, tránh entity lifecycle |
| Cần full entity + 1 association rõ ràng | EntityGraph hoặc JOIN FETCH | Load đúng use case, thường 1 query, dễ hiểu |
| Cần derived query của Spring Data và muốn đổi fetch plan theo use case | EntityGraph | Không cần viết JPQL, fetch plan nằm ở repository method |
| Có pagination trên parent và cần lazy child sau đó | Batch Size | Page parent trước, batch child sau bằng `IN`, tránh join nhân rows |
| Có nhiều parent trong session và luôn cần collection của tất cả | Subselect Fetch | 2 queries ổn định, nhưng phải cẩn thận load rộng |
| Muốn chữa cháy N+1 toàn app mà vẫn giữ LAZY | `hibernate.default_batch_fetch_size` | Safety net tốt, nhưng không thay thế phân tích use case |
| Mapping entity mặc định | `FetchType.LAZY` | Tránh load dữ liệu ngoài ý muốn |
| Gần như không nên dùng | `FetchType.EAGER` | Global, khó kiểm soát, dễ tạo N+1 hoặc query phình |

Nếu phải chọn một default best practice cho project:

```text
1. Entity mapping để LAZY.
2. API read-only ưu tiên DTO Projection.
3. API cần full entity theo use case ưu tiên EntityGraph.
4. List/pagination hoặc nhiều lazy access dùng Batch Size như optimization/safety net.
5. Tránh EAGER.
```

## Problem Analysis: N+1 Là Vấn Đề Gì?

Domain demo:

```text
Author 1 -- N Book
```

Code dễ gây N+1:

```java
List<Author> authors = authorRepository.findAll();

for (Author author : authors) {
    author.getBooks().size();
}
```

Bản chất:

```text
Query 1: lấy danh sách authors
Query 2..N+1: mỗi lần đụng author.getBooks(), Hibernate query books riêng cho author đó
```

SQL shape:

```sql
select a.*
from authors a;

select b.*
from books b
where b.author_id = ?;

select b.*
from books b
where b.author_id = ?;
```

Với 5 authors:

```text
1 query authors + 5 queries books = 6 queries
```

Với 100 authors:

```text
1 query authors + 100 queries books = 101 queries
```

Đây là N+1 problem.

## Vì Sao Không Chỉ Nhìn Số Query?

Ít query hơn chưa chắc luôn tốt hơn.

Cần nhìn thêm:

```text
JDBC statements: số SQL statements
Rows returned: số rows DB trả về
Entities loaded: số entity Hibernate hydrate vào persistence context
Collections loaded: số collection được initialize
Heap / GC: áp lực memory
Pagination correctness: page có đúng parent không
Maintainability: code có dễ hiểu, dễ đổi theo use case không
```

Ví dụ:

```text
JOIN FETCH có thể chỉ 1 query,
nhưng nếu join collection lớn, result set có thể phình rất mạnh.

Batch Size có thể 2 queries,
nhưng giữ parent query sạch hơn và thân thiện hơn với pagination.
```

## Bước 1: Hỏi Context Trước Khi Chọn Solution

Trước khi chọn kỹ thuật, hãy trả lời các câu hỏi này:

```text
1. API có cần full entity không, hay chỉ cần vài field?
2. Response có phải read-only không?
3. Có pagination trên parent không?
4. Cần load bao nhiêu association?
5. Association là ManyToOne hay OneToMany/ManyToMany?
6. Mỗi parent có trung bình bao nhiêu child?
7. Có cần filter/order theo child table không?
8. Data có thể lớn tới mức nào trong production?
9. Có cần update entity sau khi load không?
10. Team cần solution chuẩn JPA hay chấp nhận Hibernate-specific?
```

Nếu không có context, mọi câu trả lời kiểu “dùng JOIN FETCH là tốt nhất” hoặc “dùng Batch Size là tốt nhất” đều dễ sai.

## Decision Tree

```text
Có cần full entity để update/business logic không?
|
|-- Không, chỉ read-only response/report
|   -> DTO Projection
|
|-- Có
    |
    |-- Chỉ cần 1 association rõ ràng, data nhỏ/vừa, không pagination collection
    |   -> EntityGraph hoặc JOIN FETCH
    |
    |-- Có pagination trên parent
    |   -> Page parent trước + Batch Size hoặc two-step query
    |
    |-- Có nhiều collections
    |   -> Tránh JOIN FETCH nhiều collection
       -> Batch Size / Subselect / DTO tùy response
    |
    |-- Luôn cần collections cho toàn bộ parent result hiện tại
        -> Subselect Fetch có thể phù hợp, nhưng phải đo memory
```

## Solution 1: DTO Projection

DTO Projection không load full entity. Query chỉ lấy đúng field cần trả về.

Ví dụ API chỉ cần:

```text
authorName
bookCount
```

Repository:

```java
public interface AuthorBookCountProjection {
    String getName();
    Long getBookCount();
}

@Query("""
    select a.name as name, count(b) as bookCount
    from Author a
    left join a.books b
    group by a.id, a.name
    order by a.id
""")
List<AuthorBookCountProjection> findAuthorBookCounts();
```

SQL shape:

```sql
select a.name, count(b.id)
from authors a
left join books b on b.author_id = a.id
group by a.id, a.name;
```

Nên dùng khi:

- API read-only.
- Chỉ cần một subset fields.
- Cần aggregation như count, sum, average.
- Endpoint performance-critical.
- Không cần update entity sau khi query.

Ưu điểm:

- Nhẹ nhất về memory.
- Select đúng columns cần dùng.
- Không có lazy loading, nên không có N+1 do entity association.
- Response DTO rõ ràng, ít leak entity ra API.

Nhược điểm:

- Không có entity lifecycle.
- Không dùng để update entity trực tiếp.
- Phải viết DTO/interface/query riêng.
- Nếu response nested phức tạp, cần group rows trong Java hoặc viết query cẩn thận.

Vì sao không dùng approach khác?

```text
Không dùng EntityGraph/JOIN FETCH nếu API chỉ cần vài field,
vì chúng load full entity + association, nhiều dữ liệu hơn cần thiết.

Không dùng Batch Size nếu API chỉ cần count,
vì Batch Size vẫn load Book entities, trong khi COUNT ở database nhẹ hơn.
```

Best practice:

```text
Với read-only API, DTO Projection thường là lựa chọn tốt nhất.
```

### Read-only API nghĩa là gì?

`Read-only` ở đây mô tả mục đích của use case: API chỉ đọc dữ liệu để trả về
response, không thay đổi dữ liệu trong database. Ví dụ:

```text
GET /authors
GET /authors/{id}/summary
GET /reports/book-count
```

Luồng của một read-only API dùng DTO thường là:

```text
HTTP request
  -> service gọi repository
  -> database SELECT đúng các cột cần thiết
  -> Hibernate tạo DTO/projection từ từng row
  -> response JSON
```

Ví dụ endpoint chỉ cần `authorName` và `bookCount` thì không cần load `Author`
và toàn bộ `books`:

```java
@Query("""
    select new com.example.dto.AuthorSummaryDto(a.name, count(b))
    from Author a
    left join a.books b
    group by a.id, a.name
""")
Page<AuthorSummaryDto> findSummaries(Pageable pageable);
```

DTO ở đây là object phục vụ cho dữ liệu trả ra. Nó không phải là `Author`
đang được Hibernate quản lý. Vì vậy việc người dùng gọi HTTP `GET` không tự
động làm transaction thành read-only; `readOnly = true` là một chỉ dẫn cho
transaction/provider tối ưu việc đọc, còn nguyên tắc quan trọng là code không
thực hiện lệnh update/delete.

### Entity lifecycle và update entity trực tiếp

Khi repository trả về `Author`, Hibernate thường đưa object đó vào
`Persistence Context`. Object này gọi là `managed entity`:

```text
new/transient -> persist -> managed -> commit/flush -> database
                                      |
                                      -> detach/close session
```

Ví dụ:

```java
@Transactional
public void renameAuthor(Long id, String newName) {
    Author author = authorRepository.findById(id).orElseThrow(); // managed
    author.setName(newName);                                    // đổi object
    // thường không cần authorRepository.save(author)
} // commit -> dirty checking -> UPDATE authors SET name = ? WHERE id = ?
```

Hibernate theo dõi snapshot của entity. Khi transaction flush/commit, nó so
sánh snapshot với giá trị hiện tại, phát hiện `name` đã đổi và tự sinh `UPDATE`.
Đây là `dirty checking`, một phần của entity lifecycle.

DTO/projection không có cơ chế này:

```java
AuthorSummaryDto dto = repository.findSummary(id);
dto = new AuthorSummaryDto("New name", dto.bookCount());
// chỉ đổi object DTO trong memory, không có UPDATE nào được Hibernate tự sinh
```

Nếu muốn update từ DTO, phải coi DTO là input/output boundary và thực hiện
mapping hoặc chạy câu lệnh update rõ ràng:

```java
@Transactional
public void renameAuthor(Long id, RenameAuthorRequest request) {
    Author author = authorRepository.findById(id).orElseThrow();
    author.setName(request.name());
} // Hibernate dirty checking sinh UPDATE
```

Vì vậy câu “DTO không có entity lifecycle” có nghĩa cụ thể là DTO không có
identity trong persistence context, không có lazy proxy, không được dirty
checking và không tự đồng bộ ngược xuống database. Đây là ưu điểm cho API đọc,
nhưng là nhược điểm nếu service cần sửa entity, cascade, orphan removal hoặc
business logic dựa trên quan hệ đang managed.

## DTO và Pagination: ToOne khác ToMany như thế nào?

Pagination áp dụng lên kết quả SQL sau khi database xử lý `JOIN`, không áp dụng
trực tiếp lên khái niệm object Java. Vì vậy phải hỏi quan hệ được join là
`ToOne` hay `ToMany`.

### ToOne: thường pagination an toàn

`ManyToOne` hoặc `OneToOne` tối đa chỉ thêm một row liên quan cho mỗi parent:

```sql
select b.id, b.title, a.id, a.name
from books b
left join authors a on a.id = b.author_id
order by b.id
limit 20 offset 0;
```

Một `Book` vẫn tương ứng với tối đa một `Author`, nên `limit 20` vẫn gần với
ý nghĩa “20 books”. DTO query, EntityGraph hoặc `JOIN FETCH` với `ToOne` thường
không gặp lỗi nhân parent rows như collection fetch.

Điều cần chú ý vẫn là:

- `ToOne` nên để `LAZY` trong mapping nếu không phải use case nào cũng cần.
- `LEFT JOIN` giữ lại parent không có association; `INNER JOIN` loại parent đó.
- Với `Page<T>`, Spring Data thường chạy thêm count query; nên kiểm tra count
  query nếu JPQL có join/filter phức tạp.

### ToMany: pagination collection dễ sai

Với `Author -> books`, một author có nhiều rows:

```sql
author A + book 1
author A + book 2
author A + book 3
author B + book 4
```

Nếu chạy:

```sql
select a.id, a.name, b.id, b.title
from authors a
left join books b on b.author_id = a.id
order by a.id
limit 2;
```

`limit 2` có thể trả về hai rows của cùng author A, thay vì hai authors. Khi
đó Hibernate phải loại duplicate, có thể trả ít parent hơn mong muốn, hoặc
phải lấy dữ liệu lên memory để pagination. Đây là lý do collection
`JOIN FETCH` và collection `EntityGraph` không phải lựa chọn mặc định cho
`Page<Author>`.

DTO cũng không tự giải quyết được vấn đề này. DTO phẳng với `Author` và `Book`
vẫn bị phân trang theo child rows:

```text
Page<AuthorBookDto> -> page đang phân trang các dòng author-book,
                       không phải page các author.
```

Các cách phù hợp hơn:

```text
1. Page parent trước: Page<Author> chỉ lấy author ids.
2. Query child bằng IN (author_id in (...)) hoặc Batch Size.
3. Ghép dữ liệu vào response DTO.
```

Hoặc nếu response chỉ cần tổng hợp, query trực tiếp `count(b)` và paginate
parent-level DTO. Với collection lớn, cách này thường kiểm soát row count,
memory và semantics tốt hơn collection fetch.

### Bảng quyết định nhanh cho pagination

| Use case | Lựa chọn ưu tiên | Lý do |
|---|---|---|
| Page `Book`, cần tên `Author` | DTO hoặc ToOne fetch | Mỗi book có tối đa một author; không nhân parent |
| Page `Author`, chỉ cần `bookCount` | DTO + `count` | Pagination ở author-level, không load books |
| Page `Author`, cần danh sách books | Page author trước + Batch Size/two-step | Không để collection join phá page |
| Detail một `Author`, cần books | DTO nested, EntityGraph hoặc JOIN FETCH | Không có parent pagination; có thể fetch theo use case |
| Nhiều `ToMany` cùng lúc | DTO nhiều query hoặc tách endpoint | Tránh cartesian product và heap tăng mạnh |

Kết luận: DTO là lựa chọn tốt cho read-only vì chỉ lấy đúng dữ liệu cần trả,
nhưng DTO không làm cho mọi phép pagination tự động đúng. `ToOne` thường an
toàn khi join; `ToMany` cần page parent trước hoặc dùng aggregation/two-step.

## Persistence Context, nhiều List và pagination

### Persistence Context là gì?

Persistence Context là vùng quản lý entity của một `EntityManager` trong một
transaction/session. Có thể hình dung nó như một identity map và change tracker
của Hibernate:

```text
Database row (id = 1)
        |
findById(1)
        v
Persistence Context giữ Author#1
        |
findById(1) lần nữa -> trả lại cùng managed object
        |
setName(...) -> dirty checking -> UPDATE khi flush/commit
```

Nó chịu trách nhiệm chính cho:

- Không tạo hai object Java khác nhau cho cùng một entity identity trong cùng
  context.
- Theo dõi entity nào đã load và association nào đã được lazy load.
- Dirty checking, cascade và flush thay đổi xuống database.

Persistence Context thường sống trong transaction của service. Khi transaction
kết thúc, entity có thể bị detach; lúc đó lazy association không nên được truy
cập tiếp nếu session đã đóng.

### Có được dùng hai `List` trong một entity không?

Được. Java/JPA không cấm:

```java
@OneToMany(mappedBy = "author")
private List<Book> books = new ArrayList<>();

@OneToMany(mappedBy = "author")
private List<Award> awards = new ArrayList<>();
```

Vấn đề là khi cả hai collection đều là `List` không có `@OrderColumn`. Với
Hibernate, kiểu này thường được xử lý như `bag`: một collection có thể có phần
tử trùng lặp và database không có cột vị trí để phân biệt thứ tự.

Nếu cố fetch cả hai bag trong một query:

```java
select distinct a
from Author a
left join fetch a.books
left join fetch a.awards
```

có thể gặp:

```text
MultipleBagFetchException: cannot simultaneously fetch multiple bags
```

Ngay cả khi Hibernate cho chạy, SQL vẫn có nguy cơ nhân rows theo tích:

```text
Author A có 3 books và 2 awards
3 x 2 = 6 SQL rows cho cùng Author A
```

Đổi một `List` thành `Set` có thể tránh riêng exception “multiple bags”, nhưng
không loại bỏ chi phí cartesian product. `Set` cũng thay đổi semantics: không
giữ thứ tự và phụ thuộc vào `equals/hashCode`. Thêm `@OrderColumn` biến List thành
ordered list, nhưng phải quản lý thêm cột vị trí và chi phí update thứ tự. Vì vậy
“một Set + một List” chỉ là một cách tránh một loại lỗi, không phải best practice
tổng quát.

Cách an toàn hơn khi cần cả hai collection là tách việc load:

```text
query 1: load Author + books
query 2: load awards cho các author ids
hoặc dùng DTO/two-step query/batch loading
```

### Pagination với hai collection

`Page<Author>` với một collection `ToMany` đã cần cẩn thận. Với hai collection,
fetch join còn nguy hiểm hơn vì vừa làm sai giới hạn parent vừa tạo tích số
rows. Ví dụ 20 authors, mỗi author 10 books và 5 awards có thể tạo khoảng
`20 x 10 x 5 = 1.000` rows trung gian trước khi Hibernate dựng lại object graph.

Do đó không nên dùng một query kiểu:

```java
Page<Author> findAllWithBooksAndAwards(Pageable pageable);
```

nếu implementation đồng thời fetch hai collection. Flow phù hợp là:

```text
1. Page chỉ Author, không fetch collection.
2. Lấy danh sách author ids trong page.
3. Load books bằng IN/Batched query.
4. Load awards bằng query riêng.
5. Ghép kết quả vào DTO hoặc entity graph đã kiểm soát.
```

Với `ToOne`, ví dụ `Book -> Author`, join fetch thường không tạo row
multiplication vì một book có tối đa một author. Với `ToMany`, pagination phải
được hiểu là pagination parent trước, rồi mới load children.

### Quy tắc nhớ nhanh

| Trường hợp | Kết luận |
|---|---|
| Nhiều List trong entity | Hoàn toàn hợp lệ |
| Fetch đồng thời hai List bag | Có thể `MultipleBagFetchException` |
| Đổi một List thành Set | Có thể tránh exception, nhưng không xóa cartesian product |
| Page + ToOne | Thường an toàn hơn |
| Page + một ToMany fetch join | Dễ sai hoặc bị pagination in-memory |
| Page + hai ToMany fetch join | Tránh; dùng split query/DTO/batch |

## Solution 2: EntityGraph

EntityGraph định nghĩa fetch plan cho từng repository method.

Entity vẫn để `LAZY` trong mapping, nhưng method nào cần books thì khai báo:

```java
@EntityGraph(attributePaths = {"books"})
List<Author> findAll();
```

SQL thường là `LEFT JOIN`:

```sql
select a.*, b.*
from authors a
left join books b on b.author_id = a.id;
```

Nên dùng khi:

- Cần full entity.
- Biết rõ use case này cần association nào.
- Muốn dùng derived query của Spring Data như `findByNameContaining`.
- Muốn tách fetch plan khỏi JPQL query string.
- Muốn JPA-standard approach.

Ưu điểm:

- JPA standard.
- Rõ ràng ở repository method.
- Dùng tốt với derived query.
- Không phải viết `JOIN FETCH` thủ công cho mọi method.
- Entity mapping vẫn giữ `LAZY` mặc định.

Nhược điểm:

- Khi fetch collection, vẫn có row multiplication giống LEFT JOIN.
- Pagination với collection graph có thể gặp vấn đề.
- Fetch graph quá rộng có thể load thừa.
- Named EntityGraph tái sử dụng tốt, nhưng nếu graph thay đổi liên tục thì entity class có thể nặng.

Vì sao không dùng approach khác?

```text
Không dùng JOIN FETCH nếu method là derived query,
vì EntityGraph gắn trực tiếp lên derived method, không cần viết JPQL.

Không dùng Batch Size nếu response chắc chắn cần books ngay,
vì EntityGraph lấy luôn trong một query, dễ hiểu hơn cho detail/simple list.

Không dùng DTO Projection nếu service cần entity lifecycle,
vì DTO không phải managed entity.
```

Best practice:

```text
Nếu cần full entity theo từng use case, EntityGraph là lựa chọn rất tốt.
Đặc biệt tốt khi muốn giữ mapping LAZY nhưng method cụ thể cần eager load.
```

## Solution 3: JOIN FETCH

JOIN FETCH là JPQL chỉ rõ association nào cần fetch trong query.

```java
@Query("""
    select distinct a
    from Author a
    left join fetch a.books
    order by a.id
""")
List<Author> findAllWithBooks();
```

SQL:

```sql
select a.*, b.*
from authors a
left join books b on b.author_id = a.id;
```

Nên dùng khi:

- Query custom rõ ràng.
- Cần full entity + association ngay.
- Data nhỏ/vừa.
- Không pagination trên collection fetch.
- Chỉ fetch một collection.
- Cần filter/order bằng JPQL rõ ràng.

Ưu điểm:

- Dễ nhìn thấy SQL intention trong query.
- Thường 1 query.
- JPQL standard.
- Tốt cho màn hình detail hoặc simple list nhỏ.

Nhược điểm:

- Parent rows bị nhân theo child rows.
- Fetch nhiều collections dễ cartesian product.
- Pagination với collection fetch nguy hiểm.
- Phải viết query riêng cho từng use case.

Vì sao không dùng approach khác?

```text
Không dùng EntityGraph nếu query đã custom phức tạp,
vì JOIN FETCH trong JPQL có thể rõ hơn, mọi thứ nằm trong một query.

Không dùng Batch Size nếu chắc chắn luôn cần child ngay,
vì JOIN FETCH có thể xong trong 1 query, không cần chờ lazy access.

Không dùng DTO nếu cần managed entity,
vì JOIN FETCH trả entity đầy đủ.
```

Best practice:

```text
JOIN FETCH tốt cho use case cụ thể, data nhỏ/vừa, fetch 1 association rõ ràng.
Không nên dùng bừa cho nhiều collections hoặc pagination.
```

## Solution 4: Batch Size

Batch Size vẫn giữ association `LAZY`.

Khi lazy association bị access, Hibernate gom nhiều lazy loads lại thành một query `IN`.

```java
@OneToMany(mappedBy = "author", fetch = FetchType.LAZY)
@BatchSize(size = 20)
private List<Book> books = new ArrayList<>();
```

SQL:

```sql
select a.*
from authors a;

select b.*
from books b
where b.author_id in (?, ?, ?, ...);
```

Nên dùng khi:

- Có pagination trên parent.
- Có nhiều parent trong cùng transaction.
- Có khả năng access lazy child của nhiều parent.
- Không muốn JOIN collection vào query chính.
- Muốn tránh N+1 nhưng vẫn giữ LAZY.
- Muốn safety net ở mức app bằng `hibernate.default_batch_fetch_size`.

Ưu điểm:

- Giảm N+1 thành `1 + ceil(N / batch_size)`.
- Giữ `LAZY`.
- Thân thiện hơn với parent pagination.
- Tránh parent row multiplication trong query chính.
- Tốt khi có nhiều collections, tránh cartesian product.

Nhược điểm:

- Hibernate-specific nếu dùng `@BatchSize`.
- Không phải 1 query.
- Có thể preload thừa trong batch.
- `IN` quá lớn có thể ảnh hưởng performance hoặc chạm database limit.
- Phụ thuộc vào persistence context: các entity/proxy phải ở cùng session để batch.

Vì sao không dùng approach khác?

```text
Không dùng JOIN FETCH nếu có pagination parent,
vì JOIN collection làm parent bị nhân rows, pagination dễ sai hoặc phải in-memory.

Không dùng EntityGraph nếu graph fetch collection lớn,
vì EntityGraph thường vẫn dùng LEFT JOIN, nên vẫn có row multiplication.

Không dùng DTO nếu service thật sự cần managed entity,
vì Batch Size vẫn làm việc với entity.
```

Best practice:

```text
Batch Size là best practice tốt cho list/pagination và lazy access pattern.
Không nên set quá lớn. Thường bắt đầu với 10, 20, 25 hoặc 50 rồi đo.
```

## Solution 5: Subselect Fetch

Subselect Fetch load collection cho tất cả parent entities trong persistence context bằng một subquery.

```java
@OneToMany(mappedBy = "author", fetch = FetchType.LAZY)
@Fetch(FetchMode.SUBSELECT)
private List<Book> books = new ArrayList<>();
```

SQL:

```sql
select a.*
from authors a;

select b.*
from books b
where b.author_id in (
    select a2.id
    from authors a2
);
```

Nên dùng khi:

- Bạn load một tập parent và gần như chắc chắn cần collection của toàn bộ tập đó.
- Muốn số query ổn định là 2.
- Parent result không quá rộng hoặc đã được kiểm soát.
- Chấp nhận Hibernate-specific.

Ưu điểm:

- Thường chỉ 2 queries.
- Không có `IN` list dài theo batch size.
- Tránh collection JOIN row multiplication.
- Transparent ở mapping.

Nhược điểm:

- Hibernate-specific.
- Có thể load quá rộng: đụng một collection nhưng load collection cho toàn bộ parent result.
- Subquery có thể nặng tùy database optimizer.
- Khó kiểm soát hơn theo từng use case.

Vì sao không dùng approach khác?

```text
Không dùng Batch Size nếu N rất lớn và bạn luôn cần tất cả collections,
vì Batch Size có thể thành nhiều query theo ceil(N / batch_size), Subselect có thể chỉ 2.

Không dùng JOIN FETCH nếu collection lớn hoặc nhiều collection,
vì JOIN có thể phình rows mạnh.
```

Best practice:

```text
Subselect Fetch là option tốt cho case khá đặc thù.
Không nên xem nó là default toàn project nếu chưa đo.
```

## Những Approach Nên Tránh Hoặc Dùng Rất Cẩn Thận

### FetchType.EAGER

Không nên dùng như cách xử lý N+1 mặc định.

Vì:

```text
EAGER là global mapping.
Use case nào load Author cũng có nguy cơ kéo Books theo.
Khó kiểm soát query.
Có thể vẫn sinh N+1 ở nhiều tình huống.
Tăng memory vì load dữ liệu không cần thiết.
```

Best practice:

```text
Mapping để LAZY.
Fetch cái gì thì quyết định tại query/use case.
```

### OSIV Để Controller Tự Lazy Load

Nếu để Open Session In View và serialize entity trực tiếp:

```text
Controller/JSON serialization có thể vô tình trigger lazy loading.
N+1 xuất hiện ngoài service layer.
Khó đo, khó debug, response có thể kéo dữ liệu ngoài ý muốn.
```

Best practice:

```text
Service layer quyết định fetch plan.
API trả DTO/response rõ ràng.
```

### JOIN FETCH Nhiều Collections

Ví dụ:

```java
select a
from Author a
join fetch a.books
join fetch a.awards
```

Nếu:

```text
1 author có 10 books
1 author có 5 awards
```

JOIN result có thể thành:

```text
10 x 5 = 50 rows cho 1 author
```

Best practice:

```text
Tránh fetch join nhiều collections.
Dùng DTO, Batch Size, Subselect, hoặc tách query.
```

## Scenario-Based Recommendation

### Scenario A: Author Detail Page

Requirement:

```text
GET /authors/{id}
Trả về author + books.
Không pagination.
Một author, số books vừa phải.
```

Recommended:

```text
EntityGraph hoặc JOIN FETCH.
```

Vì sao:

```text
Cần dữ liệu ngay.
Chỉ một parent.
Row multiplication nhỏ.
1 query dễ hiểu.
```

Không chọn Batch Size:

```text
Vì chỉ có một parent, batch không có nhiều giá trị.
```

Không chọn DTO nếu:

```text
Service cần managed entity hoặc domain logic trên entity.
```

### Scenario B: Author List Có Pagination

Requirement:

```text
GET /authors?page=0&size=20
Trả page authors.
Có thể cần hiển thị số lượng hoặc preview books.
```

Recommended:

```text
DTO Projection nếu response read-only và biết rõ field cần.
Batch Size nếu cần entity + lazy books sau đó.
```

Vì sao không chọn JOIN FETCH collection:

```text
JOIN books làm author rows bị nhân.
Pagination có thể sai hoặc Hibernate phải xử lý in-memory.
```

### Scenario C: Report Author Name + Book Count

Requirement:

```text
Danh sách authorName + bookCount.
Không cần Book entity.
```

Recommended:

```text
DTO Projection.
```

Vì sao không chọn EntityGraph/JOIN FETCH:

```text
Load toàn bộ books chỉ để count là lãng phí.
Database count/group by tốt hơn.
```

### Scenario D: Service Load Nhiều Books Rồi Cần Author

Requirement:

```text
Load 100 books.
Trong loop cần book.getAuthor().getName().
```

Recommended:

```text
@BatchSize trên Author class hoặc default_batch_fetch_size.
```

Vì sao:

```text
Đây là ManyToOne lazy proxy case.
Batch entity proxies theo authors.id in (...).
```

Không nhất thiết JOIN FETCH:

```text
Nếu không phải use case nào load Book cũng cần Author,
Batch Size giữ lazy và chỉ load khi cần.
```

### Scenario E: Luôn Cần Collection Cho Toàn Bộ Parent Result

Requirement:

```text
Load một tập authors và luôn tính toán trên books của tất cả authors.
Parent result đã được kiểm soát.
```

Recommended:

```text
Batch Size hoặc Subselect Fetch.
```

Nếu N vừa:

```text
Batch Size dễ kiểm soát hơn.
```

Nếu N lớn và chắc chắn cần toàn bộ collection:

```text
Subselect Fetch có thể hợp lý.
```

## Comparison Table

| Tiêu chí | DTO Projection | EntityGraph | JOIN FETCH | Batch Size | Subselect Fetch |
|----------|----------------|-------------|------------|------------|-----------------|
| Query shape | Select fields/DTO | Thường LEFT JOIN | LEFT JOIN FETCH | Parent + IN | Parent + subselect |
| Trả full entity | Không | Có | Có | Có | Có |
| Read-only API | Rất tốt | Được, nhưng có thể nặng | Được, nhưng có thể nặng | Được, nếu cần entity | Được, nếu cần entity |
| Pagination parent | Tốt nếu query đúng | Cẩn thận với collection | Rủi ro cao với collection | Tốt | Cẩn thận nếu result lớn |
| Nhiều collections | DTO tùy query | Rủi ro row multiplication | Rủi ro cartesian product | Tốt hơn | Có thể load rộng |
| Memory | Thấp nhất | Vừa/cao tùy graph | Vừa/cao tùy join | Vừa, có thể preload | Có thể cao |
| Chuẩn JPA | Có | Có | Có | Không, Hibernate-specific | Không, Hibernate-specific |
| Dễ maintain | Tốt cho API rõ contract | Tốt theo use case | Tốt với query nhỏ | Tốt như safety net | Cần hiểu kỹ session scope |

## Final Best Practice Recommendation

Nếu viết guideline cho team, có thể chốt như sau:

```text
1. Entity associations mặc định để LAZY.
2. Không dùng EAGER để xử lý N+1.
3. Với API read-only, ưu tiên DTO Projection.
4. Với use case cần full entity + association rõ ràng, dùng EntityGraph.
5. JOIN FETCH dùng cho query custom nhỏ, rõ, không pagination collection.
6. Với list/pagination và lazy access pattern, dùng Batch Size.
7. Subselect Fetch chỉ dùng khi chắc chắn cần collection cho toàn bộ parent result và đã đo.
8. Luôn đo bằng SQL log, query count, entities loaded và collections loaded, không chỉ nhìn số query.
```

Một câu ngắn để trình bày:

```text
Best practice không phải chọn kỹ thuật ít query nhất.
Best practice là chọn kỹ thuật load đúng dữ liệu cần dùng, đúng thời điểm, với ít rủi ro nhất cho pagination, memory và maintainability.
```
