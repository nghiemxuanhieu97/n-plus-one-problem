package com.example.entitygraph.service;

import com.example.entitygraph.entity.Author;
import com.example.entitygraph.entity.Book;
import com.example.entitygraph.dto.AuthorDetailResponse;
import com.example.entitygraph.repository.AuthorRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.hibernate.Hibernate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorService {

    private final AuthorRepository authorRepository;
    private final EntityManager entityManager;
    private final DemoMetrics demoMetrics;

    @Transactional(readOnly = true)
    public void demonstrateNPlusOneProblem() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [PROBLEM] N+1 Queries                           |");
        log.info("|  findAll() - default LAZY loading on books       |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("baseline N+1: findAll() + lazy getBooks()", () -> {
            List<Author> authors = authorRepository.findAll();
            for (Author author : authors) {
                log.info("  [{}] {} -> {} books", author.getId(), author.getName(), author.getBooks().size());
            }

            log.info("  --> Total SQL executed: 1 + {} = {} queries", authors.size(), 1 + authors.size());
        });
    }

    @Transactional(readOnly = true)
    public void demonstrateDynamicEntityGraph() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [1] Dynamic @EntityGraph                        |");
        log.info("|  @EntityGraph(attributePaths = {\"books\"})        |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("dynamic EntityGraph: authors + books", () -> {
            List<Author> authors = authorRepository.findAllWithDynamicGraph();
            logAuthorBookCounts(authors);

            log.info("  --> One SQL query: authors LEFT JOIN books");
        });
    }

    @Transactional(readOnly = true)
    public void demonstrateNamedEntityGraph() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [2] Named @EntityGraph                          |");
        log.info("|  @EntityGraph(\"Author.withBooks\")                |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("named EntityGraph: Author.withBooks", () -> {
            List<Author> authors = authorRepository.findAllWithNamedGraph();
            logAuthorBookCounts(authors);

            log.info("  --> Same SQL shape as dynamic graph; the fetch plan is reused by name");
        });
    }

    @Transactional(readOnly = true)
    public void demonstrateEntityGraphOnDerivedQuery() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [3] EntityGraph on a derived query              |");
        log.info("|  findByNameContainingIgnoreCaseOrderById(...)    |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("derived query + named EntityGraph", () -> {
            List<Author> authors = authorRepository.findByNameContainingIgnoreCaseOrderById("George");
            logAuthorBookCounts(authors);

            log.info("  --> Spring Data creates WHERE name LIKE '%George%'");
            log.info("  --> EntityGraph adds the eager loading of books");
        });
    }

    @Transactional(readOnly = true)
    public void demonstrateFindByIdWithEntityGraph() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [4] EntityGraph on findById                     |");
        log.info("|  Override JpaRepository.findById(...)            |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("findById override + named EntityGraph", () -> {
            Author author = authorRepository.findById(1L).orElseThrow();
            log.info("  [{}] {} -> {} books", author.getId(), author.getName(), author.getBooks().size());

            log.info("  --> Useful for detail screens: find one Author and load books immediately");
        });
    }

    @Transactional(readOnly = true)
    public void demonstrateNestedEntityGraph() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [5] Nested EntityGraph                          |");
        log.info("|  Author -> books -> publisher                    |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("dynamic nested graph: books + publisher", () -> {
            List<Author> authors = authorRepository.findAllWithBooksAndPublisherDynamicGraph();
            logAuthorsBooksAndPublishers(authors);

            log.info("  --> Dynamic nested graph uses attributePaths = {\"books\", \"books.publisher\"}");
        });

        demoMetrics.measure("named nested graph: Author.withBooksAndPublisher", () -> {
            List<Author> namedAuthors = authorRepository.findAllWithBooksAndPublisherNamedGraph();
            log.info("  Named nested graph result: {} authors; first book publisher = {}",
                    namedAuthors.size(),
                    namedAuthors.get(0).getBooks().get(0).getPublisher().getName());

            log.info("  --> Named nested graph reuses Author.withBooksAndPublisher");
        });
    }

    @Transactional(readOnly = true)
    public void demonstrateQueryWithEntityGraph() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [6] JPQL @Query + EntityGraph                   |");
        log.info("|  Query decides rows; graph decides associations  |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("JPQL @Query + named EntityGraph", () -> {
            List<Author> authors = authorRepository.searchWithQueryAndEntityGraph("George", 4L);
            logAuthorBookCounts(authors);

            log.info("  --> JPQL handles the OR condition");
            log.info("  --> EntityGraph still keeps fetch plan outside the JPQL string");
        });
    }

    @Transactional(readOnly = true)
    public void demonstrateFetchAndLoadGraphTypes() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [7] EntityGraph type: FETCH vs LOAD             |");
        log.info("|  FETCH is stricter; LOAD respects entity defaults|");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("FETCH graph type", () -> {
            List<Author> fetchGraphAuthors = authorRepository.findAllWithFetchGraphType();
            fetchGraphAuthors.forEach(author -> log.info("  FETCH [{}] {} / country initialized: {} / books: {}",
                    author.getId(), author.getName(), Hibernate.isInitialized(author.getCountry()),
                    author.getBooks().size()));
            log.info("  --> country is outside the FETCH graph and remains uninitialized");
        });

        demoMetrics.measure("LOAD graph type", () -> {
            List<Author> loadGraphAuthors = authorRepository.findAllWithLoadGraphType();
            loadGraphAuthors.forEach(author -> log.info("  LOAD  [{}] {} / country initialized: {} / books: {}",
                    author.getId(), author.getName(), Hibernate.isInitialized(author.getCountry()),
                    author.getBooks().size()));
            log.info("  --> country keeps its mapping default (EAGER) under a LOAD graph");
        });

        log.info("  --> Author.country is intentionally EAGER only to make this contrast observable");
    }

    @Transactional(readOnly = true)
    public void demonstrateProgrammaticEntityGraph() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [8] Programmatic EntityGraph                    |");
        log.info("|  Build the graph with EntityManager at runtime   |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("programmatic fetchgraph hint", () -> {
            jakarta.persistence.EntityGraph<Author> graph = entityManager.createEntityGraph(Author.class);
            graph.addAttributeNodes("books");

            Author author = entityManager.find(
                    Author.class,
                    2L,
                    Map.of("jakarta.persistence.fetchgraph", graph)
            );

            log.info("  [{}] {} -> {} books", author.getId(), author.getName(), author.getBooks().size());
            log.info("  --> Useful when the fetch plan depends on runtime flags");
        });
    }

    @Transactional(readOnly = true)
    public AuthorDetailResponse findAuthorWithRuntimeGraph(
            Long authorId,
            boolean includeBooks,
            boolean includeAwards
    ) {
        return demoMetrics.measure("runtime graph: books=" + includeBooks + ", awards=" + includeAwards, () -> {
            jakarta.persistence.EntityGraph<Author> graph = entityManager.createEntityGraph(Author.class);
            graph.addAttributeNodes("country");

            if (includeBooks) {
                var booksGraph = graph.addSubgraph("books");
                booksGraph.addAttributeNodes("publisher");
            }
            if (includeAwards) {
                graph.addAttributeNodes("awards");
            }

            Author author = entityManager.find(
                    Author.class,
                    authorId,
                    Map.of("jakarta.persistence.fetchgraph", graph)
            );

            if (author == null) {
                throw new IllegalArgumentException("Author not found: " + authorId);
            }

            List<AuthorDetailResponse.BookSummary> books = includeBooks
                    ? author.getBooks().stream()
                    .map(book -> new AuthorDetailResponse.BookSummary(
                            book.getId(),
                            book.getTitle(),
                            book.getPublishYear(),
                            book.getPublisher().getName()
                    ))
                    .toList()
                    : null;

            List<AuthorDetailResponse.AwardSummary> awards = includeAwards
                    ? author.getAwards().stream()
                    .map(award -> new AuthorDetailResponse.AwardSummary(award.getId(), award.getName()))
                    .toList()
                    : null;

            return new AuthorDetailResponse(
                    author.getId(),
                    author.getName(),
                    author.getCountry().getName(),
                    includeBooks,
                    includeAwards,
                    books,
                    awards
            );
        });
    }

    @Transactional(readOnly = true)
    public void demonstrateMultipleCollectionsTradeoff() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [LIMIT 1] Graph with two ToMany collections     |");
        log.info("|  One query can still create a large result set   |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("books + awards graph: row multiplication", () -> {
            List<Author> authors = authorRepository.findAllWithBooksAndAwardsGraph();
            long estimatedRows = authors.stream()
                    .mapToLong(author -> distinctBookCount(author) * distinctAwardCount(author))
                    .sum();
            authors.forEach(author -> log.info(
                    "  [{}] {} -> {} distinct books x {} awards = {} SQL rows (hydrated List size: {})",
                    author.getId(), author.getName(), distinctBookCount(author), distinctAwardCount(author),
                    distinctBookCount(author) * distinctAwardCount(author), author.getBooks().size()));
            log.info("  --> Estimated joined rows: {} for only {} Authors", estimatedRows, authors.size());
            log.info("  --> A Set avoids MultipleBagFetchException here, but not Cartesian row multiplication");
        });
    }

    @Transactional(readOnly = true)
    public void demonstratePaginationBoundaries() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [LIMIT 2] EntityGraph + pagination              |");
        log.info("|  ToOne is stable; ToMany can paginate in memory  |");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("safe pagination: ToOne country graph", () -> {
            Page<Author> page = authorRepository.findPageWithCountryGraph(PageRequest.of(0, 2));
            page.forEach(author -> log.info("  [{}] {} / country: {}",
                    author.getId(), author.getName(), author.getCountry().getName()));
            log.info("  --> ToOne keeps one SQL row per Author, so LIMIT/OFFSET stays stable");
        });

        demoMetrics.measure("unsafe pagination: ToMany books graph", () -> {
            Page<Author> page = authorRepository.findPageWithBooksGraph(PageRequest.of(0, 2));
            page.forEach(author -> log.info("  [{}] {} -> {} books",
                    author.getId(), author.getName(), author.getBooks().size()));
            log.info("  --> Hibernate warns and may apply pagination in memory after joining all books");
        });

        demoMetrics.measure("safe two-step pagination: IDs then books graph", () -> {
            Page<Long> idPage = authorRepository.findAuthorIds(PageRequest.of(0, 2));
            List<Author> authors = idPage.isEmpty()
                    ? List.of()
                    : authorRepository.findByIdInOrderById(idPage.getContent());
            logAuthorBookCounts(authors);
            log.info("  --> Step 1 pages stable Author IDs; step 2 fetches books only for those IDs");
        });
    }

    private long distinctBookCount(Author author) {
        return author.getBooks().stream().map(Book::getId).distinct().count();
    }

    private long distinctAwardCount(Author author) {
        return author.getAwards().stream().map(award -> award.getId()).distinct().count();
    }

    private void logAuthorBookCounts(List<Author> authors) {
        for (Author author : authors) {
            log.info("  [{}] {} -> {} books", author.getId(), author.getName(), author.getBooks().size());
        }
    }

    private void logAuthorsBooksAndPublishers(List<Author> authors) {
        for (Author author : authors) {
            log.info("  [{}] {}", author.getId(), author.getName());
            for (Book book : author.getBooks()) {
                log.info("      - {} ({}) / publisher: {}",
                        book.getTitle(), book.getPublishYear(), book.getPublisher().getName());
            }
        }
    }
}
