package com.example.joinfetch.repository;

import com.example.joinfetch.entity.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    // SAFE: to-one JOIN FETCH keeps one SQL row per Author, so pagination and row counts remain stable.
    @Query("""
            select a
            from Author a
            join fetch a.country
            order by a.id
            """)
    List<Author> findAllWithCountryJoinFetch();

    // SAFE: to-one JOIN FETCH can be paginated because it does not multiply parent rows.
    @Query(
            value = """
                    select a
                    from Author a
                    join fetch a.country
                    order by a.id
                    """,
            countQuery = """
                    select count(a)
                    from Author a
                    """
    )
    Page<Author> findPageWithCountryJoinFetch(Pageable pageable);

    // USUALLY GOOD: one collection JOIN FETCH removes N+1, but the SQL row count grows with books per author.
    @Query("""
            select distinct a
            from Author a
            join fetch a.books
            order by a.id
            """)
    List<Author> findAllWithBooksJoinFetch();

    // GOOD IN SMALL/MEDIUM RESULT SETS: to-one + one collection avoids extra country queries and book N+1.
    @Query("""
            select distinct a
            from Author a
            join fetch a.country
            join fetch a.books
            where a.id in :ids
            order by a.id
            """)
    List<Author> findAuthorsWithCountryAndBooksByIds(@Param("ids") List<Long> ids);

    // UNSAFE: multiple to-many JOIN FETCH can create Cartesian product style row explosion.
    // Production recommendation: split into separate queries, use DTO projection, or fetch only one collection.
    @Query("""
            select distinct a
            from Author a
            join fetch a.books
            join fetch a.awards
            order by a.id
            """)
    List<Author> findAllWithBooksAndAwardsJoinFetch();

    // BAD: collection JOIN FETCH with Pageable triggers in-memory pagination in Hibernate.
    // Production recommendation: page parent IDs first, then fetch associations by those IDs.
    @Query(
            value = """
                    select distinct a
                    from Author a
                    join fetch a.books
                    order by a.id
                    """
    )
    Page<Author> findPageWithBooksJoinFetch(Pageable pageable);

    // STEP 1 for safe pagination: page stable parent IDs using SQL limit/offset.
    @Query("""
            select a.id
            from Author a
            order by a.id
            """)
    Page<Long> findAuthorIds(Pageable pageable);

    // STEP 2 for safe pagination: fetch the one requested page of Authors with required associations.
    @Query("""
            select distinct a
            from Author a
            join fetch a.books
            where a.id in :ids
            order by a.id
            """)
    List<Author> findAuthorsWithBooksByIds(@Param("ids") List<Long> ids);

    @Query("select count(a) from Author a")
    long countAuthors();

    @Query("select count(b) from Book b")
    long countBooks();

    @Query("select count(aw) from Award aw")
    long countAwards();

    @Query("""
            select count(a)
            from Author a
            join a.books b
            """)
    long countRowsWhenJoiningBooks();

    @Query("""
            select count(a)
            from Author a
            join a.books b
            join a.awards aw
            """)
    long countRowsWhenJoiningBooksAndAwards();
}

