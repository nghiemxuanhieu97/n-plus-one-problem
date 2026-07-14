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
        log.info("  Module 02: @EntityGraph Showcase");
        log.info("  Baseline + repository annotations + EntityManager");
        log.info("==================================================");

        authorService.demonstrateNPlusOneProblem();
        authorService.demonstrateDynamicEntityGraph();
        authorService.demonstrateNamedEntityGraph();
        authorService.demonstrateEntityGraphOnDerivedQuery();
        authorService.demonstrateFindByIdWithEntityGraph();
        authorService.demonstrateFetchAndLoadGraphTypes();
        authorService.demonstrateNestedEntityGraph();
        authorService.demonstrateQueryWithEntityGraph();
        authorService.demonstrateProgrammaticEntityGraph();

        log.info("");
        log.info("==================================================");
        log.info("  KEY POINTS");
        log.info("  - Dynamic graph: write fields directly on method");
        log.info("  - Named graph: reuse a named fetch plan");
        log.info("  - Derived query: no JPQL, graph still fetches books");
        log.info("  - Nested graph: fetch Author -> books -> publisher");
        log.info("  - EntityManager: graphs can be built or passed at runtime");
        log.info("==================================================");
    }
}
