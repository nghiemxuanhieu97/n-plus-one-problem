package com.example.batchsize.runner;

import com.example.batchsize.service.AuthorService;
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
        log.info("  Module 04: @BatchSize Showcase");
        log.info("  Demo 3 levels: application, class, attribute");
        log.info("  Lazy loading stays lazy, but Hibernate batches IN queries");
        log.info("==================================================");

        authorService.demonstrateBatchSize();
        authorService.demonstrateClassLevelBatchSize();
        authorService.demonstrateApplicationLevelBatchSize();
        authorService.demonstrateBatchPreloadTradeoff();
        authorService.demonstratePaginationFriendlyAccess();

        log.info("");
        log.info("==================================================");
        log.info("  KEY POINTS");
        log.info("  - application.yml: global fallback batch size");
        log.info("  - @BatchSize on entity class: batch entity proxies by id");
        log.info("  - @BatchSize on association field: batch that lazy collection/association");
        log.info("  - More specific @BatchSize overrides the global default for that target");
        log.info("  - More pagination-friendly than collection JOIN FETCH");
        log.info("  - Can preload extra data if you only need one collection");
        log.info("==================================================");
    }
}
