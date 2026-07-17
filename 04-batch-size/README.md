# Module 04: Batch Size

Module này minh hoạ Hibernate gom nhiều lazy load thành các query `IN` mà không đổi association sang `EAGER`.

```text
Không batch: WHERE parent_id = ? lặp N lần.
Batch:       WHERE parent_id IN (?, ?, ...).
```

Tài liệu chuyên sâu về code và toàn bộ runtime flow:

- [Batch Size: full flow từ HTTP đến Hibernate batch fetching](BATCH_SIZE_FULL_FLOW.md)

Trong tài liệu có các chương riêng về:

- Proxy Design Pattern trong Spring transaction, Spring Data repository và Hibernate lazy loading.
- Giới hạn `IN (...)`, tổng bind parameters, Hibernate `Dialect` và cách chọn batch size.

## Domain và dữ liệu

```text
Author    1 --- N Book
Publisher 1 --- N Book
```

- 12 authors
- 24 books, mỗi author 2 books
- 5 publishers
- `@BatchSize(size = 5)` trên `Author` và `Author.books`
- `Publisher.books` không có annotation, dùng làm control association

Dataset 12 authors lớn hơn batch size 5 để demo tạo nhiều batch thật:

```text
1 + ceil(12 / 5) = 4 statements
```

## Chạy demo mặc định

```powershell
.\gradlew.bat :04-batch-size:bootRun
```

Mở Swagger:

```text
http://localhost:8084/swagger-ui.html
```

Mỗi endpoint chỉ chạy một scenario. Đọc response để biết kết quả kỳ vọng, sau đó xem SQL và `[METRICS]` trong console.

Muốn chạy toàn bộ scenario theo đúng thứ tự trong một lần gọi Swagger:

```http
GET /api/batch-size/00-full-flow
```

Muốn chạy toàn bộ flow console cũ khi startup:

```powershell
.\gradlew.bat :04-batch-size:bootRun --args="--demo.auto-run=true"
```

Kết quả chính:

| Scenario | JDBC statements | Ý nghĩa |
|---|---:|---|
| Publisher.books không batch | 6 | 1 parent + 5 lazy queries |
| `@BatchSize` trên Author.books | 4 | 1 Author + 3 Book batches |
| `@BatchSize` trên Author class | 4 | 1 Book + 3 Author proxy batches |
| Chỉ dùng books của Author đầu tiên | 2 | Một fetch event preload tối đa 5 collections |
| Page 3 Authors | 3 | Page + count + một Book batch |

## So sánh global batch profile

Cấu hình global được tách khỏi default run để không ảnh hưởng ngầm tới annotation demo.

```powershell
.\gradlew.bat :04-batch-size:bootRun --args="--spring.profiles.active=global-batch"
```

Sau khi app restart, gọi lại endpoint baseline trong Swagger để thấy cùng association giảm từ 6 xuống 2 statements.

Profile `global-batch` đặt:

```yaml
hibernate:
  default_batch_fetch_size: 7
```

Chạy cùng `Publisher.books` không annotation:

```text
Default profile:      6 statements
Global-batch profile: 2 statements
```

Annotation size là 5, global size là 7. Vì vậy query shape cho phép phân biệt cấu hình cụ thể nào đang có hiệu lực; annotation cụ thể override global fallback cho target đó.

## Ba vị trí cấu hình

| Vị trí | Hibernate batch cái gì? |
|---|---|
| `@BatchSize` trên `Author.books` | Nhiều collection Books của các Author |
| `@BatchSize` trên `Author` class | Nhiều lazy Author entity proxies, ví dụ từ `Book.author` |
| `default_batch_fetch_size` | Fallback cho entity/association không có annotation riêng |

## Pagination

Batch Size page parent trước rồi mới load child:

```sql
select * from authors offset ? rows fetch first ? rows only;
select count(*) from authors;
select * from books where author_id in (?, ?, ?, ?, ?);
```

Page chỉ có 3 IDs nhưng SQL có thể giữ 5 placeholders. Các slot còn lại được bind `NULL` để giữ query shape ổn định; đây không phải lỗi.

## Trade-off preload

Code chỉ gọi:

```java
authors.get(0).getBooks().size();
```

Hibernate vẫn có thể initialize books cho tối đa 5 Authors đang nằm trong persistence context. Điều này:

- tốt nếu code sắp dùng các collection còn lại;
- lãng phí nếu thực sự chỉ cần một Author.

Vì thế không chỉ nhìn query count; cần nhìn thêm `entities loaded` và `collections loaded`.

## Khi nên dùng

- Giữ association `LAZY`.
- Page parent trước, sau đó có khả năng truy cập children của nhiều parent.
- Muốn tránh collection join làm nhân parent rows.
- Có nhiều lazy entity proxy hoặc collection trong cùng persistence context.

Không có batch size tối ưu cho mọi hệ thống. Giá trị quá nhỏ vẫn tạo nhiều round-trip; giá trị quá lớn tạo `IN` list dài và tăng nguy cơ preload thừa.
