package com.example.dtoprojection.runner;

import com.example.dtoprojection.service.AuthorService;
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
        log.info("  Module 03: DTO Projection Solution");
        log.info("  Interface projection + Class projection (record)");
        log.info("==================================================");

        authorService.demonstrateNPlusOneProblem();
        authorService.demonstrateInterfaceProjection();
        authorService.demonstrateClassProjection();

        log.info("");
        log.info("==================================================");
        log.info("  PROBLEM:    6 queries (1 authors + 5 books)");
        log.info("  SOLUTION A: 1 query  (interface projection)");
        log.info("  SOLUTION B: 1 query  (class/record projection)");
        log.info("  No entity lifecycle => no lazy loading possible");
        log.info("==================================================");
    }
}
