package com.example.joinfetch.service;

import com.example.joinfetch.dto.AuthorBasicDto;
import com.example.joinfetch.dto.AuthorBooksAwardsDto;
import com.example.joinfetch.dto.AuthorBooksDto;
import com.example.joinfetch.dto.AuthorCountryDto;
import com.example.joinfetch.dto.record.Response;
import com.example.joinfetch.entity.Author;
import com.example.joinfetch.repository.AuthorRepository;
import com.example.joinfetch.scenario.BenchmarkScenario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthorService {

    private final AuthorRepository authorRepository;
    private final EntityManagerFactory entityManagerFactory;
    private final EntityManager entityManager;

    // =========================================================
    // BASIC DEMO
    // =========================================================

    @BenchmarkScenario("AUTHORS_ONLY")
    @Transactional(readOnly = true)
    public Response<List<AuthorBasicDto>> demoAuthorsOnly() {
        List<AuthorBasicDto> result = authorRepository.findAll().stream()
                .map(AuthorBasicDto::fromEntity)
                .toList();

        return Response.payload(result, result.size());
    }

    // =========================================================
    // N+1 DEMOS
    // =========================================================

    /**
     * Expected without batch fetching:
     * 1 query for Authors + N queries for Books.
     */
    @BenchmarkScenario("N_PLUS_ONE_BOOK")
    @Transactional(readOnly = true)
    public Response<List<AuthorBooksDto>> demoNPlusOneBook() {
        List<Author> authors = authorRepository.findAll();

        // Mapping intentionally accesses getBooks() inside the transaction
        // so the lazy collection queries belong to this benchmark.
        List<AuthorBooksDto> result = authors.stream()
                .map(AuthorBooksDto::fromEntity)
                .toList();

        long estimatedRows = result.size() + countBooks(result);
        return Response.payload(result, estimatedRows);
    }

    /**
     * Expected without batch fetching:
     * 1 query for Authors + N queries for Books + N queries for Awards.
     */
    @BenchmarkScenario("N_PLUS_ONE_BOOK_AWARD")
    @Transactional(readOnly = true)
    public Response<List<AuthorBooksAwardsDto>> demoNPlusOneBookAward() {
        List<Author> authors = authorRepository.findAll();

        // Mapping intentionally accesses both lazy collections.
        List<AuthorBooksAwardsDto> result = authors.stream()
                .map(AuthorBooksAwardsDto::fromEntity)
                .toList();

        long estimatedRows = result.size()
                + countBooksFromBooksAwardsDto(result)
                + countAwards(result);

        return Response.payload(result, estimatedRows);
    }

    // =========================================================
    // JOIN FETCH DEMOS
    // =========================================================

    @BenchmarkScenario("JOIN_FETCH_TO_ONE")
    @Transactional(readOnly = true)
    public Response<List<AuthorCountryDto>> demoJoinFetchToOne() {
        List<AuthorCountryDto> result = authorRepository
                .findAllWithCountryJoinFetch()
                .stream()
                .map(AuthorCountryDto::fromEntity)
                .toList();

        return Response.payload(result, result.size());
    }

    @BenchmarkScenario("JOIN_FETCH_BOOKS")
    @Transactional(readOnly = true)
    public Response<List<AuthorBooksDto>> demoJoinFetchBooks() {
        List<AuthorBooksDto> result = authorRepository
                .findAllWithBooksJoinFetch()
                .stream()
                .map(AuthorBooksDto::fromEntity)
                .toList();

        return Response.payload(result, countBooks(result));
    }

    @BenchmarkScenario("JOIN_FETCH_CARTESIAN")
    @Transactional(readOnly = true)
    public Response<List<AuthorBooksAwardsDto>> demoCartesianProduct() {
        List<AuthorBooksAwardsDto> result = authorRepository
                .findAllWithBooksAndAwardsJoinFetch()
                .stream()
                .map(AuthorBooksAwardsDto::fromEntity)
                .toList();

        return Response.payload(result, estimateCartesianRows(result));
    }

    // =========================================================
    // PAGINATION DEMOS
    // =========================================================

    /**
     * Page query + count query + lazy Books queries for Authors in this page.
     */
    @BenchmarkScenario("PAGINATION_N_PLUS_ONE_BOOK")
    @Transactional(readOnly = true)
    public Response<List<AuthorBooksDto>> demoPaginationNPlusOne(int page, int size) {
        Page<Author> authorPage = authorRepository.findAll(PageRequest.of(page, size));

        List<AuthorBooksDto> result = authorPage.getContent().stream()
                .map(AuthorBooksDto::fromEntity)
                .toList();

        long estimatedRows = result.size() + countBooks(result);
        return Response.payload(result, estimatedRows);
    }

    @BenchmarkScenario("PAGINATION_TO_ONE")
    @Transactional(readOnly = true)
    public Response<List<AuthorCountryDto>> demoPaginationToOne(int page, int size) {
        Page<Author> authorPage = authorRepository.findPageWithCountryJoinFetch(
                PageRequest.of(page, size)
        );

        List<AuthorCountryDto> result = authorPage.getContent().stream()
                .map(AuthorCountryDto::fromEntity)
                .toList();

        return Response.payload(result, result.size());
    }

    @BenchmarkScenario("PAGINATION_COLLECTION_JOIN_FETCH")
    @Transactional(readOnly = true)
    public Response<List<AuthorBooksDto>> demoPaginationBooksBad(int page, int size) {
        Page<Author> authorPage = authorRepository.findPageWithBooksJoinFetch(
                PageRequest.of(page, size)
        );

        List<AuthorBooksDto> result = authorPage.getContent().stream()
                .map(AuthorBooksDto::fromEntity)
                .toList();

        /*
         * This endpoint may paginate in memory. Counting only the page DTOs would
         * underestimate the rows Hibernate processed. For this controlled demo,
         * entityLoadCount contains Authors + Books, while totalElements represents
         * the Author population. The value remains an estimate, not a JDBC ResultSet
         * row counter.
         */
        long allLoadedEntities = getStatistics().getEntityLoadCount();
        long estimatedRows = Math.max(
                0,
                allLoadedEntities - authorPage.getTotalElements()
        );

        return Response.payload(result, estimatedRows);
    }

    @BenchmarkScenario("SAFE_PAGINATION_TWO_STEP")
    @Transactional(readOnly = true)
    public Response<List<AuthorBooksDto>> demoSafePagination(int page, int size) {
        Page<Long> idPage = authorRepository.findAuthorIds(PageRequest.of(page, size));

        List<Author> authors = idPage.getContent().isEmpty()
                ? List.of()
                : authorRepository.findAuthorsWithBooksByIds(idPage.getContent());

        List<AuthorBooksDto> result = authors.stream()
                .map(AuthorBooksDto::fromEntity)
                .toList();

        long estimatedRows = idPage.getContent().size() + countBooks(result);
        return Response.payload(result, estimatedRows);
    }

    // =========================================================
    // OPTIONAL COMPARISON / ESTIMATE ENDPOINTS
    // =========================================================

    @BenchmarkScenario("BATCH_FETCH_COMPARISON")
    @Transactional(readOnly = true)
    public Response<List<AuthorBooksDto>> demoBatchFetchComparison() {
        List<AuthorBooksDto> lazyResult = authorRepository.findAll().stream()
                .map(AuthorBooksDto::fromEntity)
                .toList();

        // Prevent initialized collections from making the JOIN FETCH half of the
        // comparison appear artificially cheap.
        entityManager.clear();

        List<AuthorBooksDto> joinedResult = authorRepository
                .findAllWithBooksJoinFetch()
                .stream()
                .map(AuthorBooksDto::fromEntity)
                .toList();

        long estimatedRows = Math.max(
                countBooks(lazyResult),
                countBooks(joinedResult)
        );

        return Response.payload(joinedResult, estimatedRows);
    }

    @BenchmarkScenario("MEMORY_EXPLOSION_ESTIMATE")
    @Transactional(readOnly = true)
    public Response<List<AuthorBasicDto>> demoMemoryExplosionEstimate() {
        authorRepository.countAuthors();
        authorRepository.countBooks();
        authorRepository.countAwards();

        long estimatedRows = authorRepository
                .countRowsWhenJoiningBooksAndAwards();

        return Response.payload(List.of(), estimatedRows);
    }

    public List<String> productionRecommendations() {
        return List.of(
                "Keep associations LAZY by default.",
                "Use JOIN FETCH for required to-one relationships.",
                "Use JOIN FETCH for one to-many collection only when result size is controlled.",
                "Do not apply Pageable directly to a collection fetch join for large datasets.",
                "Use two-step pagination: page parent IDs, then fetch associations by IDs.",
                "Avoid fetching multiple to-many collections in one query.",
                "Use DTO projection for API-specific read models.",
                "Use batch fetching when controlled lazy loading is preferable to one large join.",
                "Inspect SQL logs, prepared statement count, entity loads, collection loads, row volume, CPU, heap, and GC together."
        );
    }

    private long countBooks(List<AuthorBooksDto> authors) {
        return authors.stream()
                .mapToLong(author -> author.books().size())
                .sum();
    }

    private long countBooksFromBooksAwardsDto(List<AuthorBooksAwardsDto> authors) {
        return authors.stream()
                .mapToLong(author -> author.books().size())
                .sum();
    }

    private long countAwards(List<AuthorBooksAwardsDto> authors) {
        return authors.stream()
                .mapToLong(author -> author.awards().size())
                .sum();
    }

    /**
     * Estimate for an INNER JOIN FETCH of Books and Awards. This is not a JDBC
     * ResultSet row counter.
     */
    private long estimateCartesianRows(List<AuthorBooksAwardsDto> authors) {
        return authors.stream()
                .mapToLong(author ->
                        (long) author.books().size() * author.awards().size()
                )
                .sum();
    }

    private Statistics getStatistics() {
        return entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
    }
}