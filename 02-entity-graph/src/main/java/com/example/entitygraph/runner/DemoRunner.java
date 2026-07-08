package com.example.entitygraph.runner;

import com.example.entitygraph.service.AuthorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DemoRunner implements CommandLineRunner {

    private final AuthorService authorService;

    @Override
    public void run(String... args) {
        log.info("");
        log.info("==================================================");
        log.info("  Module 02: @EntityGraph Solution");
        log.info("  Two variants: Dynamic graph & Named graph");
        log.info("==================================================");

        authorService.demonstrateNPlusOneProblem();
        authorService.demonstrateDynamicEntityGraph();
        authorService.demonstrateNamedEntityGraph();

        log.info("");
        log.info("==================================================");
        log.info("  PROBLEM:    6 queries (1 authors + 5 books)");
        log.info("  SOLUTION A: 1 query  (dynamic EntityGraph)");
        log.info("  SOLUTION B: 1 query  (named EntityGraph)");
        log.info("==================================================");
    }
}
