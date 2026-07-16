package com.example.entitygraph.repository;

import com.example.entitygraph.entity.Author;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    // A minimal FETCH graph suppresses the intentionally EAGER country for a clean books-only N+1 baseline.
    @EntityGraph(attributePaths = "name", type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT a FROM Author a ORDER BY a.id")
    List<Author> findAllForNPlusOneBaseline();

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

    @EntityGraph(attributePaths = "country")
    @Query(value = "SELECT a FROM Author a ORDER BY a.id", countQuery = "SELECT COUNT(a) FROM Author a")
    Page<Author> findPageWithCountryGraph(Pageable pageable);

    // Intentionally unsafe teaching case: collection graph + Pageable can paginate in memory.
    @EntityGraph(attributePaths = "books")
    @Query(value = "SELECT a FROM Author a ORDER BY a.id", countQuery = "SELECT COUNT(a) FROM Author a")
    Page<Author> findPageWithBooksGraph(Pageable pageable);

    @Query("SELECT a.id FROM Author a ORDER BY a.id")
    Page<Long> findAuthorIds(Pageable pageable);

    @EntityGraph(attributePaths = "books")
    List<Author> findByIdInOrderById(List<Long> ids);

    @EntityGraph("Author.withBooksAndAwards")
    @Query("SELECT a FROM Author a ORDER BY a.id")
    List<Author> findAllWithBooksAndAwardsGraph();
}
