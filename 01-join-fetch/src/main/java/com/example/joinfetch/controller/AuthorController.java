package com.example.joinfetch.controller;

import com.example.joinfetch.dto.*;
import com.example.joinfetch.dto.record.Response;
import com.example.joinfetch.service.AuthorService;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/demos/authors")
@RequiredArgsConstructor
@OpenAPIDefinition(
        tags = {
                @Tag(
                        name = "Baseline",
                        description = "Baseline author loading and intentional N+1 demonstrations."
                ),
                @Tag(
                        name =  "Join Fetch without Pagination",
                        description = "Join Fetch scenarios for to-one, one collection, and multiple collection fetches."
                ),
                @Tag(
                        name = "Pagination",
                        description = "Pagination patterns with Join Fetch, and manual query attempts."
                )
        }
)
public class AuthorController {

    private final AuthorService authorService;

    @Tag(name = "Baseline")
    @Operation(
            summary = "Load Authors only",
            description = """
                    Scenario: AUTHORS_ONLY

                    Purpose: Establish the baseline cost of loading Author rows without touching lazy associations.

                    Service flow: AuthorService.demoAuthorsOnly() calls authorRepository.findAll(), maps each Author to AuthorBasicDto, and returns a preview of the first 5 DTOs.

                    Result DTO: AuthorBasicDto with id and name only.

                    Associations accessed by mapper: none. The mapper does not call getCountry(), getBooks(), or getAwards().

                    Expected behavior: one ORM author query and no lazy association SQL during DTO mapping.

                    Metrics: estimatedDatabaseRows is the full number of Authors loaded, while result contains only a small preview.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Baseline author metrics and AuthorBasicDto preview")
    )
    @GetMapping("/baseline")
    public Response<List<AuthorBasicDto>> getBaselineAuthors() {
        return authorService.demoAuthorsOnly();
    }

    @Tag(name = "Baseline")
    @Operation(
            summary = "N+1 Books",
            description = """
                    Scenario: N_PLUS_ONE_BOOK

                    Purpose: Demonstrate the classic N+1 problem for a lazy one-to-many association.

                    Service flow: AuthorService.demoNPlusOneBook() loads all Authors first, then maps to AuthorBooksDto inside the transaction. The mapper intentionally calls author.getBooks().

                    Result DTO: AuthorBooksDto with id, name, and books. It does not include country or awards.

                    Why this is bad: after the initial Author query, Hibernate must initialize the books collection for each Author. Without batching, this can become one Author query plus many Books queries.

                    Metrics: SQL statements, ORM query execution count, CPU time, allocation, GC delta, and estimatedDatabaseRows include the DTO mapping work that triggers the lazy loads.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "N+1 Books benchmark metrics and AuthorBooksDto preview")
    )
    @GetMapping("/n-plus-one/book")
    public Response<List<AuthorBooksDto>> getAuthorsWithNPlusOneBooks() {
        return authorService.demoNPlusOneBook();
    }

    @Tag(name = "Baseline")
    @Operation(
            summary = "N+1 Books and Awards",
            description = """
                    Scenario: N_PLUS_ONE_BOOK_AWARD

                    Purpose: Demonstrate N+1 behavior for two lazy collections on the same root entity.

                    Service flow: AuthorService.demoNPlusOneBookAward() loads all Authors, then maps to AuthorBooksAwardsDto inside the transaction. The mapper intentionally calls author.getBooks() and author.getAwards().

                    Result DTO: AuthorBooksAwardsDto with id, name, books, and awards. It does not include country.

                    Why this is worse than the Books-only case: each Author can require lazy initialization for both collections, so the request can produce many Books queries and many Awards queries after the initial Author query.

                    Metrics: estimatedDatabaseRows is based on Authors plus loaded Books plus loaded Awards. The JSON result is only a preview, but the benchmark work covers the full loaded set.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "N+1 Books and Awards benchmark metrics")
    )
    @GetMapping("/n-plus-one/book-and-award")
    public Response<List<AuthorBooksAwardsDto>> getAuthorsWithNPlusOneBooksAndAwards() {
        return authorService.demoNPlusOneBookAward();
    }

    @Tag(name =  "Join Fetch without Pagination")
    @Operation(
            summary = "Join Fetch Country",
            description = """
                    Scenario: JOIN_FETCH_TO_ONE

                    Purpose: Show the safe Join Fetch case for a to-one association.

                    Repository query: select a from Author a Join Fetch a.country order by a.id

                    Service flow: AuthorService.demoJoinFetchToOne() fetches Authors with Country in one query and maps to AuthorCountryDto inside the transaction.

                    Result DTO: AuthorCountryDto with id, name, and country. It does not include books or awards.

                    Why this is safe: a to-one join does not multiply Author rows, so SQL row volume stays close to the number of Authors.

                    Expected behavior: no lazy SQL for country during serialization because the response contains DTOs, not JPA entities.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "To-one Join Fetch metrics and AuthorCountryDto preview")
    )
    @GetMapping("/join-fetch/country")
    public Response<List<AuthorCountryDto>> getAuthorsWithJoinFetchedCountry() {
        return authorService.demoJoinFetchToOne();
    }

    @Tag(name =  "Join Fetch without Pagination")
    @Operation(
            summary = "Join Fetch Books",
            description = """
                    Scenario: JOIN_FETCH_BOOKS

                    Purpose: Remove the lazy Books N+1 problem by fetching one collection with the root Authors.

                    Repository query: select distinct a from Author a Join Fetch a.books order by a.id

                    Service flow: AuthorService.demoJoinFetchBooks() fetches Authors and Books together, then maps to AuthorBooksDto inside the transaction.

                    Result DTO: AuthorBooksDto with id, name, and books. It must not contain country or awards.

                    Tradeoff: SQL statement count is low, but the database still returns approximately one joined row per Author-Book pair. This can be large even when only one SQL statement is executed.

                    Metrics: estimatedDatabaseRows is based on the number of Book DTOs across the full result, not on the 5-item response preview.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "One-collection Join Fetch metrics and AuthorBooksDto preview")
    )
    @GetMapping("/join-fetch/book")
    public Response<List<AuthorBooksDto>> getAuthorsWithJoinFetchedBooks() {
        return authorService.demoJoinFetchBooks();
    }

    @Tag(name = "Join Fetch without Pagination")
    @Operation(
            summary = "Join Fetch Books and Awards, Cartesian failure demo",
            description = """
                    Scenario: JOIN_FETCH_CARTESIAN

                    Purpose: Demonstrate the dangerous multiple-collection Join Fetch shape: Author.books plus Author.awards.

                    Repository query: select distinct a from Author a Join Fetch a.books Join Fetch a.awards order by a.id

                    Service flow: AuthorService.demoCartesianProduct() attempts to fetch Authors, Books, and Awards in one query, then map to AuthorBooksAwardsDto.

                    Result DTO if it completes: AuthorBooksAwardsDto with id, name, books, and awards. It does not include country.

                    Important current behavior: this endpoint is expected to overload memory under the configured demo resource limits. The practical result of the request can be OutOfMemoryError instead of a usable benchmark response.

                    Why it fails: for each Author, every Book row is combined with every Award row. For example, 20 Books x 10 Awards becomes 200 joined rows for one Author before Hibernate de-duplicates the root Author objects.

                    How to read the failure: an OutOfMemoryError is the demonstration. It means one SQL statement created a result set too large for the configured JVM heap to hydrate and assemble.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "May return metrics if it completes; commonly fails with OutOfMemoryError under demo limits")
    )
    @GetMapping("/join-fetch/book-and-award")
    public Response<List<AuthorBooksAwardsDto>> getAuthorsWithJoinFetchedBooksAndAwards() {
        return authorService.demoCartesianProduct();
    }

    @Tag(name = "Join Fetch without Pagination")
    @Operation(
            summary = "Join Fetch Review and Awards, MultipleBagFetchException failure demo",
            description = """
                    Scenario: JOIN_FETCH_MultipleBagFetchException

                    Purpose: Demonstrate the dangerous multiple-collection Join Fetch shape: Author.reviews plus Author.awards.
                    With the reviews and awards are stored as List collection, this would cause the MultipleBagFetchException

                    Repository query: select distinct a from Author a Join Fetch a.reviews Join Fetch a.awards order by a.id

                    Service flow: AuthorService.demoCartesianProduct() attempts to fetch Authors, Reviews, and Awards in one query.
    
                    Service throw MultipleBagFetchException.
                    Why it fails: for each Author, every Review row is combined with every Award row. For example, 20 Reviews x 10 Awards becomes 200 joined rows and they are both List without any indexes or sign for Hibernate to define which one is duplicated by join fetch Author objects.
                    Hibernate won't try to solve this duplication but throw error to stop the process, do not make the wrong move.

                    """,
            responses = @ApiResponse(responseCode = "200", description = "May return metrics if it completes; commonly fails with OutOfMemoryError under demo limits")
    )
    @GetMapping("/join-fetch/review-and-award")
    public Response<List<AuthorReviewAwardsDto>> findAuthorWithReviewWithAwards() {
        return authorService.findAuthorWithReviewWithAwards();
    }

    @Tag(name = "Baseline")
    @Operation(
            summary = "Pagination N+1 Books",
            description = """
                    Scenario: PAGINATION_N_PLUS_ONE_BOOK

                    Purpose: Show that pagination limits the number of root Authors, but lazy collection access can still create N+1 behavior inside the page.

                    Service flow: AuthorService.demoPaginationNPlusOne() loads a Page<Author>, then maps the page content to AuthorBooksDto inside the transaction. The mapper intentionally calls author.getBooks().

                    Result DTO: AuthorBooksDto with id, name, and books.

                    Expected behavior: Spring Data runs the page content query and count query, then Hibernate initializes Books for Authors in the returned page.

                    Metrics: estimatedDatabaseRows is page Authors plus Books loaded for that page.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Paged N+1 Books metrics")
    )
    @GetMapping("/pagination/n-plus-one/book")
    public Response<List<AuthorBooksDto>> demoPaginationNPlusOne(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return authorService.demoPaginationNPlusOne(page, size);
    }

    @Tag(name = "Pagination")
    @Operation(
            summary = "Pagination Join Fetch Country",
            description = """
                    Scenario: PAGINATION_TO_ONE

                    Purpose: Show the safe pagination case for a to-one Join Fetch.

                    Repository query: select a from Author a Join Fetch a.country order by a.id

                    Service flow: AuthorService.demoPaginationToOne() loads a Page<Author> with Country fetched, then maps to AuthorCountryDto.

                    Result DTO: AuthorCountryDto with id, name, and country. It does not contain books or awards.

                    Why this is safe: joining a to-one association does not multiply Author rows, so SQL limit and offset remain meaningful.

                    Metrics: estimatedDatabaseRows is the number of Authors in the returned page preview source.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Safe to-one pagination metrics")
    )
    @GetMapping("/pagination/join-fetch/country")
    public Response<List<AuthorCountryDto>> demoPaginationToOne(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return authorService.demoPaginationToOne(page, size);
    }

    @Tag(name = "Pagination")
    @Operation(
            summary = "Pagination Join Fetch Books, Hibernate in-memory pagination demo",
            description = """
                    Scenario: PAGINATION_COLLECTION_JOIN_FETCH

                    Purpose: Demonstrate why Pageable should not be applied directly to a collection Join Fetch.

                    Repository query: select distinct a from Author a Join Fetch a.books order by a.id

                    Service flow: AuthorService.demoPaginationBooksBad() calls the pageable repository method, maps returned Authors to AuthorBooksDto, and estimates processed rows from Hibernate entity load statistics.

                    Result DTO: AuthorBooksDto with id, name, and books.

                    Why this is bad: Hibernate may fetch a large Author-Book joined result, de-duplicate Authors, and apply pagination in memory. The response page can look small while the database and Hibernate processed much more data.

                    Warning to look for: HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory.

                    Recommendation: use the two-step pagination endpoint instead.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Collection fetch join pagination metrics")
    )
    @GetMapping("/pagination/join-fetch/book")
    public Response<List<AuthorBooksDto>> demoPaginationBooksBad(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return authorService.demoPaginationBooksBad(page, size);
    }

    @Tag(name = "Pagination")
    @Operation(
            summary = "Two-step Pagination Books",
            description = """
                    Scenario: SAFE_PAGINATION_TWO_STEP

                    Purpose: Demonstrate a safer pattern for paginating Authors with Books.

                    Step 1 query: select a.id from Author a order by a.id

                    Step 2 query: select distinct a from Author a Join Fetch a.books where a.id in :ids order by a.id

                    Service flow: AuthorService.demoSafePagination() pages stable Author IDs first, then fetches Books only for those IDs, then maps to AuthorBooksDto.

                    Result DTO: AuthorBooksDto with id, name, and books.

                    Why this is better: SQL row volume is bounded by the requested page of Author IDs, not by all joined Author-Book rows in the table.

                    Metrics: estimatedDatabaseRows is page IDs plus Books for those Authors.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Two-step pagination metrics")
    )
    @GetMapping("/pagination/two-step/book")
    public Response<List<AuthorBooksDto>> demoSafePagination(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return authorService.demoSafePagination(page, size);
    }

    @Tag(name = "Pagination")
    @Operation(
            summary = "Self-defined Pagination Books, SQL vs JPQL failure cases",
            description = """
                    Scenario: MANUAL_DEFINED_JOIN_FETCH_PAGINATION

                    Purpose: Demonstrate why manually adding limit/offset to a fetch-style pagination query is not a safe replacement for two-step pagination.

                    Endpoint parameter: queryType controls the implementation. Use queryType=SQL for the native SQL version. Any other value uses the JPQL version.

                    SQL case: AuthorService.demoSelfDefinedJoinFetchPaginationSQL() calls a native query that joins authors and books and applies limit/offset to joined SQL rows. This can produce an incorrect Author response because pagination is applied to rows, not Authors. In the current demo data, the response can contain only 1 Author in the list even when size is larger, because the limited joined rows may all belong to the same Author.

                    JPQL case: AuthorService.demoSelfDefinedJoinFetchPaginationJPQL() attempts to use limit/offset syntax in a JPQL query. This currently fails with: org.hibernate.query.UnknownParameterException: Could not resolve jakarta.persistence.Parameter 'SqmNamedParameter(noItem)' to org.hibernate.query.QueryParameter

                    Result DTO if the SQL case completes: AuthorBooksDto with id, name, and books. The result is intentionally not trustworthy as a correct Author page.

                    Recommendation: use /demos/authors/pagination/two-step/book for a bounded and correct Author page with Books.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Manual pagination demo; SQL can return incorrect Author page, JPQL currently throws UnknownParameterException")
    )
    @GetMapping("/pagination/self-defined/book")
    public Response<List<AuthorBooksDto>> demoSelfDefinedJoinFetchPagination(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "SQL") String queryType
    ) {
        if (queryType.equalsIgnoreCase("SQL")) {
            return authorService.demoSelfDefinedJoinFetchPaginationSQL(page, size);
        } else {
            return authorService.demoSelfDefinedJoinFetchPaginationJPQL(page, size);
        }
    }
}
