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
        log.info("  Module 04: @BatchSize Solution");
        log.info("  Author.books annotated with @BatchSize(size = 5)");
        log.info("  No changes needed to Repository or Service code!");
        log.info("==================================================");

        authorService.demonstrateBatchSize();

        log.info("");
        log.info("==================================================");
        log.info("  Without @BatchSize: 6 queries (1 + 5 individual)");
        log.info("  With @BatchSize(5): 2 queries (1 + 1 IN-batch)");
        log.info("  Formula: 1 + ceil(N / batch_size)");
        log.info("==================================================");
    }
}
