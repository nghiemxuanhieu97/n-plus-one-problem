package com.example.entitygraph.runner;

import com.example.entitygraph.service.AuthorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "demo.auto-run", havingValue = "true")
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DemoRunner implements CommandLineRunner {

    private final AuthorService authorService;

    @Value("${demo.mode:core}")
    private String demoMode;

    @Override
    public void run(String... args) {
        log.info("");
        log.info("==================================================");
        log.info("  Module 02: @EntityGraph Showcase");
        log.info("  Mode: {}", demoMode);
        log.info("==================================================");

        authorService.demonstrateNPlusOneProblem();
        authorService.demonstrateDynamicEntityGraph();

        if (isAdvancedMode()) {
            authorService.demonstrateNamedEntityGraph();
        }

        authorService.demonstrateEntityGraphOnDerivedQuery();

        if (isAdvancedMode()) {
            authorService.demonstrateFindByIdWithEntityGraph();
        }

        authorService.demonstrateNestedEntityGraph();

        if (isAdvancedMode()) {
            authorService.demonstrateQueryWithEntityGraph();
            authorService.demonstrateFetchAndLoadGraphTypes();
            authorService.demonstrateProgrammaticEntityGraph();
        }

        authorService.demonstrateMultipleCollectionsTradeoff();
        authorService.demonstratePaginationBoundaries();

        log.info("");
        log.info("==================================================");
        log.info("  KEY POINTS");
        log.info("  - Dynamic graph: write fields directly on method");
        log.info("  - Named graph: reuse a named fetch plan");
        log.info("  - Derived query: no JPQL, graph still fetches books");
        log.info("  - Nested graph: fetch Author -> books -> publisher");
        log.info("  - Pagination: ToOne is safe; ToMany graph can paginate in memory");
        log.info("  - Multiple collections: one SQL can still multiply result rows");
        log.info("  - EntityManager: graphs can be built or passed at runtime");
        log.info("  - Run with --demo.mode=all to include advanced API variants");
        log.info("==================================================");
    }

    private boolean isAdvancedMode() {
        return "all".equalsIgnoreCase(demoMode) || "advanced".equalsIgnoreCase(demoMode);
    }
}
