#### What is a bag?

A bag is a collection that:

- Has no persistent position or index for its elements.
- Does not guarantee a database order.
- Can contain duplicate elements.

A Java `List` does not automatically become an indexed Hibernate list. Without an `@OrderColumn`, Hibernate does not store the position of each element and normally treats the collection as a bag.

The repository attempts to fetch both bags in one query:

```java
select distinct a
from Author a
join fetch a.reviews
join fetch a.awards
order by a.id
```

### How `@OrderColumn` Changes `books` prevents MultipleBagFetchException

In this project, `books` is not a plain bag because it has an index column:

```java
@OneToMany(
        mappedBy = "author",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
)
@OrderColumn(name = "book_order")
@Builder.Default
private List<Book> books = new ArrayList<>();
```

`@OrderColumn` adds a position column to the `books` table:

| author_id | book_id | book_order | title |
| --- | --- | --- | --- |
| 1 | 1 | 0 | Author 1 - Book 1 |
| 1 | 2 | 1 | Author 1 - Book 2 |
| 2 | 21 | 0 | Author 2 - Book 1 |
| 2 | 22 | 1 | Author 2 - Book 2 |

The index starts from `0` for each Author because `book_order` stores the position of a Book inside that Author's `books` list.

### Tag 04 - How `Set` Can Also Prevent `MultipleBagFetchException`

Another common way to avoid `MultipleBagFetchException` is to map one of the collections as a `Set` instead of a plain `List` bag.

A `Set` is not treated as a Hibernate bag because it represents a collection of unique elements, not an unordered list that may contain duplicates. That gives Hibernate a different collection model and avoids the specific rule: **do not fetch multiple bags in one query**.

Example mapping:

```java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
@Builder.Default
private Set<Award> awards = new LinkedHashSet<>();
```

With this shape, a query that fetches `books` and `awards` together is no longer fetching two bag collections if `books` is indexed with `@OrderColumn` or if `awards` is a `Set`:

```jpql
select distinct a
from Author a
join fetch a.books
join fetch a.awards
order by a.id
```

| Collection Shape | Bag? | Effect on `MultipleBagFetchException` |
| --- | --- | --- |
| `List<Book>` without `@OrderColumn` | Yes | Can contribute to `MultipleBagFetchException`. |
| `List<Book>` with `@OrderColumn` | No | Indexed list, so it is not a plain bag. |
| `Set<Award>` | No | Set collection, so it is not a bag. |

Important distinction:

```text
Set can prevent MultipleBagFetchException.
Set does not prevent Cartesian row multiplication.
```

So `Set` helps Hibernate accept the query, but the SQL result can still be large because every fetched child collection is still joined into the same row set. In this benchmark, that means the Cartesian/OOM lesson still matters even when the mapping avoids the multiple-bag exception.
