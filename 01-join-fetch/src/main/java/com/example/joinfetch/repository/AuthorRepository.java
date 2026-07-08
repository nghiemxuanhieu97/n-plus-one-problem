package com.example.joinfetch.repository;

import com.example.joinfetch.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    @Query("SELECT DISTINCT a FROM Author a JOIN FETCH a.books ORDER BY a.id")
    List<Author> findAllWithBooks();
}
