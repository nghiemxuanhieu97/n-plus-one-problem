package com.example.subselectfetch.service;

import com.example.subselectfetch.entity.Author;
import com.example.subselectfetch.repository.AuthorRepository;
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
    public void demonstrateSubselectFetch() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [SOLUTION] @Fetch(FetchMode.SUBSELECT)          |");
        log.info("|                                                  |");
        log.info("|  Author.books is annotated with @Fetch(SUBSELECT)|");
        log.info("|  Same code as N+1 problem — Hibernate handles it|");
        log.info("|                                                  |");
        log.info("|  Without annotation:   6 queries (1 + 5 indiv.) |");
        log.info("|  With @Fetch(SUBSELECT): 2 queries — always!    |");
        log.info("+--------------------------------------------------+");
        log.info("  Executing: authorRepository.findAll() ...");

        List<Author> authors = authorRepository.findAll(); // SQL #1: SELECT authors

        log.info("  Accessing books for each author:");
        log.info("  (First .getBooks() call triggers the subselect for ALL 5 authors at once)");

        for (Author author : authors) {
            // First access fires: SELECT * FROM books WHERE author_id IN (SELECT id FROM authors)
            // Subsequent accesses served from 1st-level cache
            log.info("  [{}] {} -> {} books", author.getId(), author.getName(), author.getBooks().size());
        }

        log.info("");
        log.info("  --> Total SQL executed: 2");
        log.info("      SQL #1: SELECT * FROM authors");
        log.info("      SQL #2: SELECT * FROM books WHERE author_id IN (SELECT a.id FROM authors a)");
        log.info("      This is ALWAYS 2 queries — even with 1,000 authors!");
    }
}
