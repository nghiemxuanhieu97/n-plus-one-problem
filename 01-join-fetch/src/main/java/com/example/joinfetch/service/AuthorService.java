package com.example.joinfetch.service;

import com.example.joinfetch.entity.Author;
import com.example.joinfetch.repository.AuthorRepository;
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
    public void demonstrateNPlusOneProblem() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [PROBLEM] N+1 Queries                           |");
        log.info("|  findAll() returns 5 authors (1 query)           |");
        log.info("|  getBooks() for each author triggers N queries   |");
        log.info("+--------------------------------------------------+");

        List<Author> authors = authorRepository.findAll(); // SQL #1: SELECT * FROM authors
        for (Author author : authors) {
            // Each call triggers: SELECT * FROM books WHERE author_id = ?
            log.info("  [{}] {} -> {} books", author.getId(), author.getName(), author.getBooks().size());
        }

        log.info("  --> Total SQL executed: 1 (authors) + {} (books) = {} queries",
                authors.size(), 1 + authors.size());
    }

    @Transactional(readOnly = true)
    public void demonstrateSolution() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [SOLUTION] JOIN FETCH                           |");
        log.info("|  SELECT DISTINCT a FROM Author a                 |");
        log.info("|  JOIN FETCH a.books ORDER BY a.id                |");
        log.info("+--------------------------------------------------+");

        List<Author> authors = authorRepository.findAllWithBooks(); // SQL #1: JOIN FETCH
        for (Author author : authors) {
            // No extra SQL — books already loaded
            log.info("  [{}] {} -> {} books", author.getId(), author.getName(), author.getBooks().size());
        }

        log.info("  --> Total SQL executed: 1 (JOIN FETCH - authors + books together)");
    }
}
