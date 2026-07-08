package com.example.joinfetch.runner;

import com.example.joinfetch.service.AuthorService;
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
        log.info("  Module 01: JOIN FETCH Solution");
        log.info("  Domain: Author (1) --< Book (N)");
        log.info("  Data: 5 authors x 3 books = 15 books total");
        log.info("==================================================");

        authorService.demonstrateNPlusOneProblem();
        authorService.demonstrateSolution();

        log.info("");
        log.info("==================================================");
        log.info("  Count the 'Hibernate:' lines above to compare:");
        log.info("  PROBLEM:  6 queries (1 authors + 5 books)");
        log.info("  SOLUTION: 1 query  (JOIN FETCH)");
        log.info("==================================================");
    }
}
