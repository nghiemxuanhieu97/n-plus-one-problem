package com.example.subselectfetch.runner;

import com.example.subselectfetch.service.AuthorService;
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
        log.info("  Module 05: @Fetch(FetchMode.SUBSELECT) Solution");
        log.info("  Author.books annotated with @Fetch(SUBSELECT)");
        log.info("  No changes needed to Repository or Service code!");
        log.info("==================================================");

        authorService.demonstrateSubselectFetch();

        log.info("");
        log.info("==================================================");
        log.info("  Without annotation: 6 queries (1 + 5 individual)");
        log.info("  With @Fetch(SUBSELECT): ALWAYS 2 queries");
        log.info("  Even with 1,000 authors: still just 2 queries");
        log.info("==================================================");
    }
}
