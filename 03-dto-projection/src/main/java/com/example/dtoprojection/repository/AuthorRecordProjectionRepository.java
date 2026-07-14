package com.example.dtoprojection.repository;

import com.example.dtoprojection.dto.recordprojection.AuthorBookAwardCountRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorBookRowRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorDisplayNameRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorNameOnlyRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorRecentBookRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorWithCountryRecord;
import com.example.dtoprojection.entity.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuthorRecordProjectionRepository extends JpaRepository<Author, Long> {

    @Query("""
            select new com.example.dtoprojection.dto.recordprojection.AuthorNameOnlyRecord(a.id, a.name)
            from Author a
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            order by a.id
            """)
    List<AuthorNameOnlyRecord> findAuthorNamesByName(@Param("keyword") String keyword);

    @Query(value = """
            select new com.example.dtoprojection.dto.recordprojection.AuthorWithCountryRecord(
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
    Page<AuthorWithCountryRecord> findAuthorsWithCountryByName(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select new com.example.dtoprojection.dto.recordprojection.AuthorWithCountryRecord(
                a.id, a.name, c.id, c.name, c.region
            )
            from Author a
            left join a.country c
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            order by a.id
            """)
    List<AuthorWithCountryRecord> findAuthorsWithCountryByName(@Param("keyword") String keyword);

    @Query(value = """
            select new com.example.dtoprojection.dto.recordprojection.AuthorBookAwardCountRecord(
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
    Page<AuthorBookAwardCountRecord> findAuthorBookAndAwardCountsByName(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select new com.example.dtoprojection.dto.recordprojection.AuthorBookAwardCountRecord(
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
    List<AuthorBookAwardCountRecord> findAuthorBookAndAwardCountsByName(@Param("keyword") String keyword);

    @Query(value = """
            select new com.example.dtoprojection.dto.recordprojection.AuthorRecentBookRecord(
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
    Page<AuthorRecentBookRecord> findAuthorsWithMostRecentBookByName(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select new com.example.dtoprojection.dto.recordprojection.AuthorRecentBookRecord(
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
    List<AuthorRecentBookRecord> findAuthorsWithMostRecentBookByName(@Param("keyword") String keyword);

    @Query(value = """
            select new com.example.dtoprojection.dto.recordprojection.AuthorBookRowRecord(
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
    Page<AuthorBookRowRecord> findAuthorBookRowsByName(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select new com.example.dtoprojection.dto.recordprojection.AuthorBookRowRecord(
                a.id, a.name, b.id, b.title, b.publishYear
            )
            from Author a
            join a.books b
            where lower(a.name) like lower(concat('%', :keyword, '%'))
            order by a.id, b.id
            """)
    List<AuthorBookRowRecord> findAuthorBookRowsByName(@Param("keyword") String keyword);

    @Query("""
            select new com.example.dtoprojection.dto.recordprojection.AuthorDisplayNameRecord(
                a.id, a.name, concat(a.name, ' ', coalesce(a.lastname, ''))
            )
            from Author a
            where a.id = :authorId
            """)
    AuthorDisplayNameRecord findAuthorDisplayNameById(@Param("authorId") Long authorId);
}
