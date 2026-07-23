# Luồng xử lý dữ liệu từ JPQL đến Entity
Khi ứng dụng thực thi một JPQL query, dữ liệu đi qua các bước sau:

```text
JPQL
→ Hiberna
te chuyển thành SQL
→ Database thực thi SQL
→ ResultSet gồm các dòng dữ liệu
→ Hibernate đọc ResultSet
→ Dựng lại Entity và Association
```


Ví dụ:

```java
select distinct a
from Author a
join fetch a.books
```

Database không trả trực tiếp một danh sách object `Author`. Nó trả về các dòng dữ liệu tương ứng với từng cặp Author–Book:

| author_id | author_name | book_id | book_title |
| --------- | ----------- | ------- | ---------- |
| 1         | Author 1    | 1       | Book 1     |
| 1         | Author 1    | 2       | Book 2     |
| 1         | Author 1    | 3       | Book 3     |
| 2         | Author 2    | 4       | Book 4     |

Hibernate đọc từng dòng, nhận diện các entity theo ID và dựng lại object graph:

```text
3 dòng của Author 1
→ 1 Author entity
→ Collection chứa 3 Book entities
```
Với dataset demo:

```text
500 Authors × 20 Books
= 10.000 ResultSet rows
```

Sau khi xử lý, Hibernate dựng lại:

```text
500 Author entities
10.000 Book entities
```
