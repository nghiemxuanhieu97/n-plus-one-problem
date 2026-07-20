package com.example.joinfetch.service;

import com.example.joinfetch.dto.*;
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

        return Response.payload(result.stream().limit(5).toList(), result.size());
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
        return Response.payload(result.stream().limit(5).toList(), estimatedRows);
    }

    @BenchmarkScenario("N_PLUS_ONE_BOOK")
    @Transactional(readOnly = true)
    public Response<List<AuthorCountryDto>> demoNPlusOneCountry() {
        List<Author> authors = authorRepository.findAll();

        // Mapping intentionally accesses getBooks() inside the transaction
        // so the lazy collection queries belong to this benchmark.
        List<AuthorCountryDto> result = authors.stream()
                .map(AuthorCountryDto::fromEntity)
                .toList();

        return Response.payload(result.stream().limit(5).toList(), result.size());
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
        List<AuthorBooksAwardsDto> reviewResult = result.stream().limit(5).toList();
        return Response.payload(reviewResult, estimatedRows);
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
        
        return Response.payload(result.stream().limit(5).toList(), result.size());
    }

    @BenchmarkScenario("JOIN_FETCH_BOOKS")
    @Transactional(readOnly = true)
    public Response<List<AuthorBooksDto>> demoJoinFetchBooks() {
        List<AuthorBooksDto> result = authorRepository
                .findAllWithBooksJoinFetch()
                .stream()
                .map(AuthorBooksDto::fromEntity)
                .toList();

        return Response.payload(result.stream().limit(5).toList(), countBooks(result));
    }

    @BenchmarkScenario("JOIN_FETCH_CARTESIAN")
    @Transactional(readOnly = true)
    public Response<List<AuthorBooksAwardsDto>> demoCartesianProduct() {
        List<AuthorBooksAwardsDto> result = authorRepository
                .findAllWithBooksAndAwardsJoinFetch()
                .stream()
                .map(AuthorBooksAwardsDto::fromEntity)
                .toList();

        return Response.payload(result.stream().limit(5).toList(), estimateCartesianRowsAuthorBookReview(result));
    }


    @BenchmarkScenario("JOIN_FETCH_CARTESIAN")
    @Transactional(readOnly = true)
    public Response<List<AuthorReviewAwardsDto>> findAuthorWithReviewWithAwards() {
        List<AuthorReviewAwardsDto> result = authorRepository
                .findAuthorWithReviewWithAwards()
                .stream()
                .map(AuthorReviewAwardsDto::fromEntity)
                .toList();

        return Response.payload(result.stream().limit(5).toList(), estimateCartesianRows(result));
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
        return Response.payload(result.stream().limit(5).toList(), estimatedRows);
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

        return Response.payload(result.stream().limit(5).toList(), result.size());
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

        return Response.payload(result.stream().limit(5).toList(), estimatedRows);
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
        return Response.payload(result.stream().limit(5).toList(), estimatedRows);
    }


    @BenchmarkScenario("MANUAL_DEFINED_JOIN_FETCH_PAGINATION")
    @Transactional(readOnly = true)
    public Response<List<AuthorBooksDto>> demoSelfDefinedJoinFetchPaginationSQL(int page, int size) {
        List<Author> authorPage = authorRepository.findAuthorWithDefinedPaginationSQL(size, page);

        List<AuthorBooksDto> result = authorPage.stream()
                .map(AuthorBooksDto::fromEntity)
                .toList();

        return Response.payload(result.stream().limit(5).toList(), result.size());
    }


    @BenchmarkScenario("MANUAL_DEFINED_JOIN_FETCH_PAGINATION")
    @Transactional(readOnly = true)
    public Response<List<AuthorBooksDto>> demoSelfDefinedJoinFetchPaginationJPQL(int page, int size) {
        List<Author> authorPage = authorRepository.findAuthorWithDefinedPaginationJPQL(size, page);

        List<AuthorBooksDto> result = authorPage.stream()
                .map(AuthorBooksDto::fromEntity)
                .toList();

        return Response.payload(result.stream().limit(5).toList(), result.size());
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
    private long estimateCartesianRowsAuthorBookReview(List<AuthorBooksAwardsDto> authors) {
        return authors.stream()
                .mapToLong(author ->
                        (long) author.books().size() * author.awards().size()
                )
                .sum();
    }

    private long estimateCartesianRows(List<AuthorReviewAwardsDto> authors) {
        return authors.stream()
                .mapToLong(author ->
                        (long) author.reviews().size() * author.awards().size()
                )
                .sum();
    }

    private Statistics getStatistics() {
        return entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
    }
}