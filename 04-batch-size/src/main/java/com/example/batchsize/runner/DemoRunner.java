package com.example.batchsize.runner;

import com.example.batchsize.service.AuthorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "demo.auto-run", havingValue = "true")
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DemoRunner implements CommandLineRunner {

    private final AuthorService authorService;

    @Override
    public void run(String... args) {
        log.info("");
        log.info("==================================================");
        log.info("  Module 04: @BatchSize Showcase");
        log.info("  Demo 3 levels: application, class, attribute");
        log.info("  Lazy loading stays lazy, but Hibernate batches IN queries");
        log.info("==================================================");

        authorService.demonstrateBaselineWithoutBatch();
        authorService.demonstrateBatchSize();
        authorService.demonstrateClassLevelBatchSize();
        authorService.demonstrateApplicationLevelBatchSize();
        authorService.demonstrateBatchPreloadTradeoff();
        authorService.demonstratePaginationFriendlyAccess();

        log.info("");
        log.info("==================================================");
        log.info("  KEY POINTS");
        log.info("  - Default run: unannotated Publisher.books proves the N+1 baseline");
        log.info("  - global-batch profile: global fallback size 7 for unannotated associations");
        log.info("  - @BatchSize on entity class: batch entity proxies by id");
        log.info("  - @BatchSize on association field: batch that lazy collection/association");
        log.info("  - More specific @BatchSize overrides the global default for that target");
        log.info("  - More pagination-friendly than collection JOIN FETCH");
        log.info("  - Can preload extra data if you only need one collection");
        log.info("  - Run again with --spring.profiles.active=global-batch to compare the same baseline");
        log.info("==================================================");
    }
}
