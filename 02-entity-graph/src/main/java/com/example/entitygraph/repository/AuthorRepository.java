package com.example.entitygraph.repository;

import com.example.entitygraph.entity.Author;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    // Dynamic EntityGraph — defined at call site, no annotation on entity needed
    @EntityGraph(attributePaths = {"books"})
    @Query("SELECT a FROM Author a ORDER BY a.id")
    List<Author> findAllWithDynamicGraph();

    // Named EntityGraph — references the @NamedEntityGraph on Author class
    @EntityGraph("Author.withBooks")
    @Query("SELECT a FROM Author a ORDER BY a.id")
    List<Author> findAllWithNamedGraph();
}
