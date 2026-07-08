package com.example.dtoprojection.repository;

import com.example.dtoprojection.dto.AuthorBookCountProjection;
import com.example.dtoprojection.dto.AuthorBooksDto;
import com.example.dtoprojection.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    // Interface projection: aggregation — author name + book count
    @Query("SELECT a.name as name, COUNT(b) as bookCount " +
           "FROM Author a LEFT JOIN a.books b GROUP BY a.id, a.name ORDER BY a.id")
    List<AuthorBookCountProjection> findAuthorBookCounts();

    // Class projection (record): flat list — one row per book
    @Query("SELECT new com.example.dtoprojection.dto.AuthorBooksDto(a.name, b.title, b.publishYear) " +
           "FROM Author a JOIN a.books b ORDER BY a.name, b.title")
    List<AuthorBooksDto> findAllAuthorBooks();
}
