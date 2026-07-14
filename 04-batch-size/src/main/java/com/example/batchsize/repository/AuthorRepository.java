package com.example.batchsize.repository;

import com.example.batchsize.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorRepository extends JpaRepository<Author, Long> {
    // No custom queries needed — @BatchSize on the entity handles optimization transparently
}
