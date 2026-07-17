package com.example.batchsize.service;

import com.example.batchsize.entity.Author;
import com.example.batchsize.entity.Book;
import com.example.batchsize.repository.AuthorRepository;
import com.example.batchsize.repository.BookRepository;
import com.example.batchsize.repository.PublisherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.env.Environment;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorService {

    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;
    private final PublisherRepository publisherRepository;
    private final DemoMetrics demoMetrics;
    private final Environment environment;

    @Transactional(readOnly = true)
    public void demonstrateBaselineWithoutBatch() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [PROBLEM] Baseline without association batching |");
        log.info("|  Publisher.books has no @BatchSize               |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("baseline: publishers + lazy books", () -> {
            var publishers = publisherRepository.findAll();
            for (var publisher : publishers) {
                log.info("  [{}] {} -> {} books", publisher.getId(), publisher.getName(),
                        publisher.getBooks().size());
            }

            if (globalBatchSize() > 0) {
                log.info("  --> global-batch profile is active: the same unannotated association is batched");
            } else {
                log.info("  --> Expected: 1 publisher query + {} individual collection queries",
                        publishers.size());
            }
        });
    }

    @Transactional(readOnly = true)
    public void demonstrateBatchSize() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [1] @BatchSize(size = 5): multiple real batches|");
        log.info("|  Same access code as N+1, but fewer queries      |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("@BatchSize full list: findAll() + getBooks()", () -> {
            log.info("  Executing: authorRepository.findAll() ...");
            List<Author> authors = authorRepository.findAll();

            log.info("  Accessing books for each author:");
            log.info("  Each fetch handles up to 5 of the 12 Author collections");

            for (Author author : authors) {
                log.info("  [{}] {} -> {} books", author.getId(), author.getName(), author.getBooks().size());
            }

            log.info("");
            log.info("  --> Expected SQL shape:");
            log.info("      SQL #1: SELECT * FROM authors");
            log.info("      SQL #2-4: SELECT * FROM books WHERE author_id IN (?, ?, ?, ?, ?)");
            log.info("      Formula: 1 + ceil(12 / 5) = 4 queries");
        });
    }

    @Transactional(readOnly = true)
    public void demonstrateClassLevelBatchSize() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [2] Class-level @BatchSize on Author            |");
        log.info("|  Batch-load lazy Book.author entity proxies      |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("Class-level @BatchSize: findAll books + getAuthor()", () -> {
            List<Book> books = bookRepository.findAll();

            log.info("  Loaded {} books first. Book.author is still LAZY.", books.size());
            log.info("  First .getAuthor() initializes Author proxies in batches of 5.");

            for (Book book : books) {
                log.info("  [{}] {} -> author: {}", book.getId(), book.getTitle(), book.getAuthor().getName());
            }

            log.info("");
            log.info("  --> Expected SQL shape:");
            log.info("      SQL #1: SELECT * FROM books");
            log.info("      SQL #2-4: SELECT * FROM authors WHERE id IN (?, ?, ?, ?, ?)");
            log.info("      @BatchSize on Author applies when Hibernate initializes Author proxies.");
        });
    }

    @Transactional(readOnly = true)
    public void demonstrateApplicationLevelBatchSize() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [3] application.yml default_batch_fetch_size    |");
        log.info("|  Global fallback for lazy associations/entities  |");
        log.info("+--------------------------------------------------+");

        if (globalBatchSize() <= 0) {
            log.info("  SKIPPED: run with --spring.profiles.active=global-batch");
            log.info("  The profile sets default_batch_fetch_size=7 without changing entity annotations.");
            return;
        }

        demoMetrics.measure("Application-level default_batch_fetch_size: findAll books + getPublisher()", () -> {
            List<Book> books = bookRepository.findAll();

            log.info("  Loaded {} books first. Book.publisher has no @BatchSize annotation.", books.size());
            log.info("  Hibernate batches Publisher proxies because global-batch sets default_batch_fetch_size={}",
                    globalBatchSize());

            for (Book book : books) {
                log.info("  [{}] {} -> publisher: {}", book.getId(), book.getTitle(), book.getPublisher().getName());
            }

            log.info("");
            log.info("  --> Expected SQL shape:");
            log.info("      SQL #1: SELECT * FROM books");
            log.info("      SQL #2: SELECT * FROM publishers WHERE id IN (...) using the global size");
            log.info("      This proves the application-level setting works even without @BatchSize on Book.publisher.");
        });
    }

    @Transactional(readOnly = true)
    public void demonstrateBatchPreloadTradeoff() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [4] Batch preloading trade-off                  |");
        log.info("|  Access one collection; Hibernate may load more  |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("@BatchSize trade-off: access only first author's books", () -> {
            List<Author> authors = authorRepository.findAll();
            Author firstAuthor = authors.get(0);

            log.info("  Loaded {} authors, but only access books of the first author", authors.size());
            log.info("  [{}] {} -> {} books", firstAuthor.getId(), firstAuthor.getName(), firstAuthor.getBooks().size());

            log.info("");
            log.info("  --> Important trade-off:");
            log.info("      BatchSize optimizes lazy loading by preloading nearby uninitialized collections.");
            log.info("      That avoids N+1 if you later access more authors, but can load extra books if you only needed one author.");
        });
    }

    @Transactional(readOnly = true)
    public void demonstratePaginationFriendlyAccess() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [5] Parent pagination + @BatchSize              |");
        log.info("|  Page authors first, then batch-load books       |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("@BatchSize with parent pagination: page size 3", () -> {
            Page<Author> page = authorRepository.findAll(PageRequest.of(0, 3));
            List<Author> authors = page.getContent();

            log.info("  Page size: {}, total elements: {}", authors.size(), page.getTotalElements());
            for (Author author : authors) {
                log.info("  [{}] {} -> {} books", author.getId(), author.getName(), author.getBooks().size());
            }

            log.info("");
            log.info("  --> Expected SQL shape:");
            log.info("      SQL #1: SELECT page of authors LIMIT/OFFSET");
            log.info("      SQL #2: SELECT COUNT(*) for page metadata");
            log.info("      SQL #3: SELECT books WHERE author_id IN (?, ?, ?, ?, ?)");
            log.info("      Only 3 IDs are real; remaining slots may be bound as NULL to keep SQL shape stable.");
            log.info("      Unlike JOIN FETCH on a collection, parent pagination is not distorted by multiplied join rows.");
        });
    }

    private int globalBatchSize() {
        return environment.getProperty("spring.jpa.properties.hibernate.default_batch_fetch_size", Integer.class, 0);
    }
}
