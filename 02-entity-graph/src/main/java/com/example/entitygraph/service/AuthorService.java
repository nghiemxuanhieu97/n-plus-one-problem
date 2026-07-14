package com.example.entitygraph.service;

import com.example.entitygraph.entity.Author;
import com.example.entitygraph.entity.Book;
import com.example.entitygraph.repository.AuthorRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public void demonstrateFetchAndLoadGraphTypes() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [5] EntityGraph type: FETCH vs LOAD             |");
        log.info("|  FETCH is stricter; LOAD respects entity defaults|");
        log.info("+--------------------------------------------------+");

        demoMetrics.measure("FETCH graph type", () -> {
            List<Author> fetchGraphAuthors = authorRepository.findAllWithFetchGraphType();
            log.info("  FETCH graph result: {} authors, first author has {} books",
                    fetchGraphAuthors.size(), fetchGraphAuthors.get(0).getBooks().size());
        });

        demoMetrics.measure("LOAD graph type", () -> {
            List<Author> loadGraphAuthors = authorRepository.findAllWithLoadGraphType();
            log.info("  LOAD graph result:  {} authors, first author has {} books",
                    loadGraphAuthors.size(), loadGraphAuthors.get(0).getBooks().size());
        });

        log.info("  --> In this demo both SQL shapes are similar because other associations are LAZY");
        log.info("  --> Difference matters more when the entity has default EAGER associations");
    }

    @Transactional(readOnly = true)
    public void demonstrateNestedEntityGraph() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [6] Nested EntityGraph                          |");
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
        log.info("|  [7] JPQL @Query + EntityGraph                   |");
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
