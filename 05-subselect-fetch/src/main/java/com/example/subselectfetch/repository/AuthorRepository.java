package com.example.subselectfetch.repository;

import com.example.subselectfetch.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorRepository extends JpaRepository<Author, Long> {
    // No custom queries needed — @Fetch(SUBSELECT) on the entity handles optimization transparently
}
