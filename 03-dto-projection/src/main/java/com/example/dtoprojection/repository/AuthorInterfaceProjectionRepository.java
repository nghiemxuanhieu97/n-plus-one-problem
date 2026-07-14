package com.example.dtoprojection.repository;

import com.example.dtoprojection.dto.interfaceprojection.AuthorBookAwardCountProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorNameOnlyProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorRecentBookProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorWithBooksProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorWithCountryProjection;
import com.example.dtoprojection.entity.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuthorInterfaceProjectionRepository extends JpaRepository<Author, Long> {

    List<AuthorWithCountryProjection> findAuthorsWithCountryByNameContainingIgnoreCase(String keyword);

    Page<AuthorWithCountryProjection> findAuthorsWithCountryByNameContainingIgnoreCase(String keyword, Pageable pageable);

    List<AuthorNameOnlyProjection> findAuthorNamesByNameContainingIgnoreCase(String keyword);

    List<AuthorWithBooksProjection> findAuthorsWithBooksByNameContainingIgnoreCase(String keyword);

    Page<AuthorWithBooksProjection> findAuthorsWithBooksByNameContainingIgnoreCase(String keyword, Pageable pageable);

    <T> Optional<T> findProjectedById(Long id, Class<T> projectionType);

    @Query(value = """
            select
                a.id as id,
                a.name as name,
                c as country,
                count(distinct b.id) as bookCount,
                count(distinct aw.id) as awardCount
            from Author a
            left join a.country c
            left join a.books b
            left join a.awards aw
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            group by a.id, a.name, c
            """,
            countQuery = """
            select count(a)
            from Author a
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            """)
    Page<AuthorBookAwardCountProjection> findAuthorBookAndAwardCountsByName(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            select
                a.id as id,
                a.name as name,
                c as country,
                count(distinct b.id) as bookCount,
                count(distinct aw.id) as awardCount
            from Author a
            left join a.country c
            left join a.books b
            left join a.awards aw
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            group by a.id, a.name, c
            """)
    List<AuthorBookAwardCountProjection> findAuthorBookAndAwardCountsByName(@Param("keyword") String keyword);

    @Query(value = """
            select
                a.id as id,
                a.name as name,
                c as country,
                b.title as recentBookName
            from Author a
            left join a.country c
            left join a.books b
            where lower(a.name) like lower(concat('%', :keyword, '%'))
              and b.id = (
                  select max(b2.id)
                  from Book b2
                  where b2.author = a
              )
            """,
            countQuery = """
            select count(a)
            from Author a
            where lower(a.name) like lower(concat('%', :keyword, '%'))
              and exists (
                  select 1
                  from Book b2
                  where b2.author = a
              )
            """)
    Page<AuthorRecentBookProjection> findAuthorsWithMostRecentBookByName(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            select
                a.id as id,
                a.name as name,
                c as country,
                b.title as recentBookName
            from Author a
            left join a.country c
            left join a.books b
            where lower(a.name) like lower(concat('%', :keyword, '%'))
              and b.id = (
                  select max(b2.id)
                  from Book b2
                  where b2.author = a
              )
            """)
    List<AuthorRecentBookProjection> findAuthorsWithMostRecentBookByName(@Param("keyword") String keyword);
}
