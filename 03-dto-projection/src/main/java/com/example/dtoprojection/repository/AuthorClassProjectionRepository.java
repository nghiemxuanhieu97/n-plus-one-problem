package com.example.dtoprojection.repository;

import com.example.dtoprojection.dto.classprojection.AuthorBookAwardCountDto;
import com.example.dtoprojection.dto.classprojection.AuthorBookRowDto;
import com.example.dtoprojection.dto.classprojection.AuthorDisplayNameDto;
import com.example.dtoprojection.dto.classprojection.AuthorNameOnlyDto;
import com.example.dtoprojection.dto.classprojection.AuthorRecentBookDto;
import com.example.dtoprojection.dto.classprojection.AuthorWithCountryDto;
import com.example.dtoprojection.entity.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuthorClassProjectionRepository extends JpaRepository<Author, Long> {

    @Query("""
            select new com.example.dtoprojection.dto.classprojection.AuthorNameOnlyDto(a.id, a.name)
            from Author a
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            order by a.id
            """)
    List<AuthorNameOnlyDto> findAuthorNamesByName(@Param("keyword") String keyword);

    @Query(value = """
            select new com.example.dtoprojection.dto.classprojection.AuthorWithCountryDto(
                a.id, a.name, c.id, c.name, c.region
            )
            from Author a
            left join a.country c
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            order by a.id
            """,
            countQuery = """
            select count(a)
            from Author a
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            """)
    Page<AuthorWithCountryDto> findAuthorsWithCountryByName(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select new com.example.dtoprojection.dto.classprojection.AuthorWithCountryDto(
                a.id, a.name, c.id, c.name, c.region
            )
            from Author a
            left join a.country c
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            order by a.id
            """)
    List<AuthorWithCountryDto> findAuthorsWithCountryByName(@Param("keyword") String keyword);

    @Query(value = """
            select new com.example.dtoprojection.dto.classprojection.AuthorBookAwardCountDto(
                a.id, a.name, c.id, c.name, c.region, count(distinct b.id), count(distinct aw.id)
            )
            from Author a
            left join a.country c
            left join a.books b
            left join a.awards aw
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            group by a.id, a.name, c.id, c.name, c.region
            order by a.id
            """,
            countQuery = """
            select count(a)
            from Author a
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            """)
    Page<AuthorBookAwardCountDto> findAuthorBookAndAwardCountsByName(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select new com.example.dtoprojection.dto.classprojection.AuthorBookAwardCountDto(
                a.id, a.name, c.id, c.name, c.region, count(distinct b.id), count(distinct aw.id)
            )
            from Author a
            left join a.country c
            left join a.books b
            left join a.awards aw
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            group by a.id, a.name, c.id, c.name, c.region
            order by a.id
            """)
    List<AuthorBookAwardCountDto> findAuthorBookAndAwardCountsByName(@Param("keyword") String keyword);

    @Query(value = """
            select new com.example.dtoprojection.dto.classprojection.AuthorRecentBookDto(
                a.id, a.name, c.id, c.name, c.region, b.title
            )
            from Author a
            left join a.country c
            left join a.books b
            where lower(a.name) like lower(concat('%', :keyword, '%'))
              and b.id = (
                  select max(b2.id)
                  from Book b2
                  where b2.author = a
              )
            order by a.id
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
    Page<AuthorRecentBookDto> findAuthorsWithMostRecentBookByName(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select new com.example.dtoprojection.dto.classprojection.AuthorRecentBookDto(
                a.id, a.name, c.id, c.name, c.region, b.title
            )
            from Author a
            left join a.country c
            left join a.books b
            where lower(a.name) like lower(concat('%', :keyword, '%'))
              and b.id = (
                  select max(b2.id)
                  from Book b2
                  where b2.author = a
              )
            order by a.id
            """)
    List<AuthorRecentBookDto> findAuthorsWithMostRecentBookByName(@Param("keyword") String keyword);

    @Query(value = """
            select new com.example.dtoprojection.dto.classprojection.AuthorBookRowDto(
                a.id, a.name, b.id, b.title, b.publishYear
            )
            from Author a
            join a.books b
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            order by a.id, b.id
            """,
            countQuery = """
            select count(b)
            from Author a
            join a.books b
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            """)
    Page<AuthorBookRowDto> findAuthorBookRowsByName(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select new com.example.dtoprojection.dto.classprojection.AuthorBookRowDto(
                a.id, a.name, b.id, b.title, b.publishYear
            )
            from Author a
            join a.books b
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            order by a.id, b.id
            """)
    List<AuthorBookRowDto> findAuthorBookRowsByName(@Param("keyword") String keyword);

    @Query("""
            select new com.example.dtoprojection.dto.classprojection.AuthorDisplayNameDto(
                a.id, a.name, concat(a.name, ' ', coalesce(a.lastname, ''))
            )
            from Author a
            where a.id = :authorId
            """)
    AuthorDisplayNameDto findAuthorDisplayNameById(@Param("authorId") Long authorId);
}
