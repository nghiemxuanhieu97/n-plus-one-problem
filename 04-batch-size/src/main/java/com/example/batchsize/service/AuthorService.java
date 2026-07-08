package com.example.batchsize.service;

import com.example.batchsize.entity.Author;
import com.example.batchsize.repository.AuthorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorService {

    private final AuthorRepository authorRepository;

    @Transactional(readOnly = true)
    public void demonstrateBatchSize() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [SOLUTION] @BatchSize(size = 5)                 |");
        log.info("|                                                  |");
        log.info("|  Author.books is annotated with @BatchSize(5)   |");
        log.info("|  Same code as N+1 problem — Hibernate handles it|");
        log.info("|                                                  |");
        log.info("|  Without @BatchSize: 6 queries (1 + 5 individual)|");
        log.info("|  With    @BatchSize: 2 queries (1 + 1 IN-batch) |");
        log.info("+--------------------------------------------------+");
        log.info("  Executing: authorRepository.findAll() ...");

        List<Author> authors = authorRepository.findAll(); // SQL #1: SELECT authors

        log.info("  Accessing books for each author:");
        log.info("  (First .getBooks() call triggers the batch IN-clause for ALL 5 authors)");

        for (Author author : authors) {
            // First access: Hibernate fires batch SQL for all 5 authors at once
            // Subsequent accesses: served from 1st-level cache
            log.info("  [{}] {} -> {} books", author.getId(), author.getName(), author.getBooks().size());
        }

        log.info("");
        log.info("  --> Total SQL executed: 2");
        log.info("      SQL #1: SELECT * FROM authors");
        log.info("      SQL #2: SELECT * FROM books WHERE author_id IN (1, 2, 3, 4, 5)");
        log.info("      Formula: 1 + ceil(5 / 5) = 2 queries");
    }
}
