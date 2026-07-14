package com.example.entitygraph.repository;

import com.example.entitygraph.entity.Author;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    @Override
    @EntityGraph("Author.withBooks")
    Optional<Author> findById(Long id);

    @EntityGraph(attributePaths = {"books"})
    @Query("SELECT a FROM Author a ORDER BY a.id")
    List<Author> findAllWithDynamicGraph();

    @EntityGraph("Author.withBooks")
    @Query("SELECT a FROM Author a ORDER BY a.id")
    List<Author> findAllWithNamedGraph();

    @EntityGraph("Author.withBooks")
    List<Author> findByNameContainingIgnoreCaseOrderById(String name);

    @EntityGraph(attributePaths = {"books"}, type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT a FROM Author a ORDER BY a.id")
    List<Author> findAllWithFetchGraphType();

    @EntityGraph(attributePaths = {"books"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT a FROM Author a ORDER BY a.id")
    List<Author> findAllWithLoadGraphType();

    @EntityGraph(attributePaths = {"books", "books.publisher"})
    @Query("SELECT a FROM Author a ORDER BY a.id")
    List<Author> findAllWithBooksAndPublisherDynamicGraph();

    @EntityGraph("Author.withBooksAndPublisher")
    @Query("SELECT a FROM Author a ORDER BY a.id")
    List<Author> findAllWithBooksAndPublisherNamedGraph();

    @EntityGraph("Author.withBooks")
    @Query("""
            SELECT a
            FROM Author a
            WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR a.id > :minId
            ORDER BY a.id
            """)
    List<Author> searchWithQueryAndEntityGraph(@Param("keyword") String keyword, @Param("minId") Long minId);
}
