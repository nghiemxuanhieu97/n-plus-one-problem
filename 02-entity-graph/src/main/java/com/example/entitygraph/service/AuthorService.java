package com.example.entitygraph.service;

import com.example.entitygraph.entity.Author;
import com.example.entitygraph.repository.AuthorRepository;
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
        log.info("|  findAll() — default LAZY loading on books       |");
        log.info("+--------------------------------------------------+");

        List<Author> authors = authorRepository.findAll(); // SQL #1
        for (Author author : authors) {
            log.info("  [{}] {} -> {} books", author.getId(), author.getName(), author.getBooks().size());
        }

        log.info("  --> Total SQL executed: 1 + {} = {} queries", authors.size(), 1 + authors.size());
    }

    @Transactional(readOnly = true)
    public void demonstrateDynamicEntityGraph() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [SOLUTION A] Dynamic @EntityGraph               |");
        log.info("|  @EntityGraph(attributePaths = {\"books\"})        |");
        log.info("+--------------------------------------------------+");

        List<Author> authors = authorRepository.findAllWithDynamicGraph(); // SQL #1: LEFT JOIN
        for (Author author : authors) {
            log.info("  [{}] {} -> {} books", author.getId(), author.getName(), author.getBooks().size());
        }

        log.info("  --> Total SQL executed: 1 (LEFT JOIN via dynamic EntityGraph)");
    }

    @Transactional(readOnly = true)
    public void demonstrateNamedEntityGraph() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [SOLUTION B] Named @EntityGraph                 |");
        log.info("|  @EntityGraph(\"Author.withBooks\")                |");
        log.info("|  Defined via @NamedEntityGraph on Author class   |");
        log.info("+--------------------------------------------------+");

        List<Author> authors = authorRepository.findAllWithNamedGraph(); // SQL #1: LEFT JOIN
        for (Author author : authors) {
            log.info("  [{}] {} -> {} books", author.getId(), author.getName(), author.getBooks().size());
        }

        log.info("  --> Total SQL executed: 1 (LEFT JOIN via named EntityGraph)");
    }
}
