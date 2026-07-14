package com.example.joinfetch.controller;

import com.example.joinfetch.dto.AuthorBasicDto;
import com.example.joinfetch.dto.AuthorBooksAwardsDto;
import com.example.joinfetch.dto.AuthorBooksDto;
import com.example.joinfetch.dto.AuthorCountryDto;
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
@RequestMapping("/demo")
@RequiredArgsConstructor
@OpenAPIDefinition(
        tags = {
                @Tag(
                        name = "N+1 Problem Demonstrations",
                        description = "Các API minh họa vấn đề N+1"
                ),
                @Tag(
                        name = "Good JOIN FETCH Scenarios",
                        description = "Các trường hợp sử dụng JOIN FETCH phù hợp"
                ),
                @Tag(
                        name = "Cartesian Product Demonstrations",
                        description = "Các trường hợp JOIN FETCH gây Cartesian product"
                ),
                @Tag(
                        name = "Pagination Demonstrations",
                        description = "Các trường hợp pagination với JOIN FETCH"
                ),
                @Tag(
                        name = "Performance Comparison APIs",
                        description = "Các API so sánh hiệu suất"
                )
        }
)
public class AuthorController {

    private final AuthorService authorService;
    @Tag(name = "Good JOIN FETCH Scenarios")
    @Operation(
            summary = "Load Authors only",
            description = "Returns Authors without touching lazy associations."
    )
    @GetMapping("/authors/basic")
    public Response<List<AuthorBasicDto>> demoAuthorsOnly() {
        return authorService.demoAuthorsOnly();
    }

    @Tag(name = "N+1 Problem Demonstrations")
    @Operation(
            summary = "N+1 problem",
            description = """
                    Title: N+1 Problem with Lazy Books (BAD)

                    Purpose: Load Authors first, then access author.getBooks() inside a loop.

                    JPQL: select a from Author a

                    Expected SQL: one select from authors, then extra selects for lazy books. With hibernate.default_batch_fetch_size, Hibernate may batch those lazy book queries.

                    Backend execution flow: Step 1 load parent Authors. Step 2 iterate Authors. Step 3 touch lazy Books. Step 4 Hibernate initializes collections.

                    Why bad: work happens after the parent query and may scale with parent count.

                    Expected performance impact: many round trips without batching; fewer but still extra queries with batch fetching.

                    Memory / CPU implications: entity hydration and collection initialization grow with loaded Authors and Books.

                    Production recommendation: use JOIN FETCH, DTO projection, or batch fetching only when the API really needs the relationship.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "N+1 demo metrics")
    )
    @GetMapping("/n-plus-one/book")
    public Response<List<AuthorBooksDto>> demoNPlusOne() {
        return authorService.demoNPlusOneBook();
    }
    @Tag(name = "N+1 Problem Demonstrations")
    @Operation(
            summary = "N+1 problem",
            description = """
                    Title: N+1 Problem with Lazy Books, Awards (BAD)

                    Purpose: Load Authors first, then access author.getBooks() and getAwards() inside a loop. T

                    JPQL: select a from Author a

                    Expected SQL: one select from authors, then extra selects for lazy books.

                    Backend execution flow: Step 1 load parent Authors. Step 2 iterate Authors. Step 3 touch lazy Books. Step 4 Hibernate initializes collections.

                    Why bad: work happens after the parent query and may scale with parent count.

                    Expected performance impact: many round trips without batching; fewer but still extra queries with batch fetching.

                    Memory / CPU implications: entity hydration and collection initialization grow with loaded Authors and Books.

                    Production recommendation: use JOIN FETCH, DTO projection, or batch fetching only when the API really needs the relationship.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "N+1 demo metrics")
    )
    @GetMapping("/n-plus-one/book&award")
    public Response<List<AuthorBooksAwardsDto>> demoNPlusOneBookAward() {
        return authorService.demoNPlusOneBookAward();
    }

    @Tag(name = "Good JOIN FETCH Scenarios")
    @Operation(
            summary = "JOIN FETCH with to-one relation",
            description = """
                    Title: JOIN FETCH Author -> Country (GOOD)

                    Purpose: Show safe JOIN FETCH with ManyToOne.

                    JPQL: select a from Author a join fetch a.country order by a.id

                    Expected SQL: authors join countries by country_id.

                    Backend execution flow: Hibernate loads Author and Country in one SQL result.

                    Why good: to-one joins keep one row per Author, so row count stays stable.

                    Expected performance impact: avoids lazy country queries with little row expansion.

                    Memory / CPU implications: low because no collection duplication occurs.

                    Production recommendation: safe and common when the API needs the to-one data.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "To-one JOIN FETCH metrics")
    )
    @GetMapping("/join-fetch/to-one")
    public Response<List<AuthorCountryDto>> demoJoinFetchToOne() {
        return authorService.demoJoinFetchToOne();
    }

    @Tag(name = "Good JOIN FETCH Scenarios")
    @Operation(
            summary = "JOIN FETCH one collection",
            description = """
                    Title: JOIN FETCH Author -> Books (GOOD WHEN CONTROLLED)

                    Purpose: Show single-query loading of one to-many collection.

                    JPQL: select distinct a from Author a join fetch a.books order by a.id

                    Expected SQL: authors join books, returning one SQL row per Author-Book pair.

                    Backend execution flow: database returns joined rows; Hibernate hydrates Books and de-duplicates Authors.

                    Why good: removes N+1 when the API needs Authors with Books.

                    Expected performance impact: fewer SQL round trips but larger result set.

                    Memory / CPU implications: grows with number of Books loaded.

                    Production recommendation: use for detail screens or small/medium result sets.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "One collection JOIN FETCH metrics")
    )
    @GetMapping("/join-fetch/books")
    public Response<List<AuthorBooksDto>> demoJoinFetchBooks() {
        return authorService.demoJoinFetchBooks();
    }

    @Tag(name = "Cartesian Product Demonstrations")
    @Operation(
            summary = "Multiple collection JOIN FETCH",
            description = """
                    Title: Multiple Collection JOIN FETCH (BAD)

                    Purpose: Demonstrate Cartesian product style row explosion.

                    JPQL: select distinct a from Author a join fetch a.books join fetch a.awards order by a.id

                    Expected SQL: authors join books join awards.

                    Backend execution flow: for each Author, every Book row is combined with every Award row.

                    Why bad: 20 Books and 10 Awards become 200 SQL rows per Author.

                    Expected performance impact: high network transfer and Hibernate hydration cost.

                    Memory / CPU implications: large persistence context work, duplicate parent data in SQL rows, possible OOM on large datasets.

                    Production recommendation: do not fetch multiple to-many collections in one query; split queries or return a DTO read model.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Cartesian product metrics")
    )
    @GetMapping("/join-fetch/cartesian")
    public Response<List<AuthorBooksAwardsDto>> demoCartesianProduct() {
        return authorService.demoCartesianProduct();
    }
    @Tag(name = "N+1 Problem Demonstrations")
    @GetMapping("/pagination/n-plus-one/book")
    @Operation(
            summary = "Pagination with Author then fetch books")
    public Response<List<AuthorBooksDto>> demoPaginationNPlusOne(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return authorService.demoPaginationNPlusOne(page, size);
    }
    @Tag(name = "Pagination Demonstrations")
    @Operation(
            summary = "Pagination with to-one JOIN FETCH",
            description = """
                    Title: Pagination with To-One JOIN FETCH (GOOD)

                    Purpose: Prove SQL limit/offset still works with ManyToOne JOIN FETCH.

                    JPQL: select a from Author a join fetch a.country order by a.id

                    Expected SQL: authors join countries with limit/offset.

                    Backend execution flow: database applies pagination to stable Author rows.

                    Why good: Country does not multiply Author rows.

                    Expected performance impact: predictable page query.

                    Memory / CPU implications: bounded by requested page size.

                    Production recommendation: safe pagination pattern for to-one data.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Good pagination metrics")
    )
    @GetMapping("/pagination/to-one")
    public Response<List<AuthorCountryDto>> demoPaginationToOne(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return authorService.demoPaginationToOne(page, size);
    }

    @Tag(name = "Pagination Demonstrations")
    @Operation(
            summary = "Pagination with collection JOIN FETCH",
            description = """
                    Title: Pagination with Collection JOIN FETCH (BAD)

                    Purpose: Demonstrate why Pageable should not be combined directly with @OneToMany JOIN FETCH.

                    JPQL: select distinct a from Author a join fetch a.books order by a.id

                    Expected SQL: Hibernate may fetch all Author-Book joined rows without SQL limit/offset.

                    Hibernate behavior: detects collection fetch plus pagination, logs HHH90003004, and applies pagination in memory.

                    Backend execution flow: fetch joined rows, hydrate entities, de-duplicate Authors, then keep only the requested page.

                    Why bad: database, JDBC, network, and Hibernate may process far more rows than the page needs.

                    Expected performance impact: slow response for large tables.

                    Memory / CPU implications: large memory usage, high CPU, possible OOM.

                    Production recommendation: use /demo/pagination/safe.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Bad pagination metrics")
    )
    @GetMapping("/pagination/books")
    public Response<List<AuthorBooksDto>> demoPaginationBooksBad(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return authorService.demoPaginationBooksBad(page, size);
    }

    @Tag(name = "Pagination Demonstrations")
    @Operation(
            summary = "Safe pagination strategy",
            description = """
                    Title: Safe Pagination with Two-Step Query (GOOD)

                    Purpose: Demonstrate production-friendly pagination for Authors with Books.

                    JPQL step 1: select a.id from Author a order by a.id

                    JPQL step 2: select distinct a from Author a join fetch a.country join fetch a.books where a.id in :ids

                    Expected SQL: first query uses limit/offset on authors only; second query fetches associations for that small ID list.

                    Backend execution flow: page parent IDs, then fetch the requested page's graph.

                    Why good: collection fetch is bounded to the page.

                    Expected performance impact: two queries, but stable memory and predictable page size.

                    Memory / CPU implications: bounded by page size and books in that page.

                    Production recommendation: prefer this over collection JOIN FETCH with Pageable.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Safe pagination metrics")
    )
    @GetMapping("/pagination/safe")
    public Response<List<AuthorBooksDto>> demoSafePagination(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return authorService.demoSafePagination(page, size);
    }
//
//    @Tag(name = "Performance Comparison APIs")
//    @Operation(
//            summary = "Batch fetch comparison",
//            description = """
//                    Title: Batch Fetch Comparison
//
//                    Purpose: Compare lazy loading with hibernate.default_batch_fetch_size against JOIN FETCH.
//
//                    JPQL: select a from Author a, then lazy books; compared with select distinct a from Author a join fetch a.books.
//
//                    Expected SQL: batch fetching groups lazy collection loads; JOIN FETCH returns joined rows.
//
//                    Backend execution flow: run lazy batched access, then run JOIN FETCH, then compare timings.
//
//                    Why useful: JOIN FETCH is not always the fastest solution for large result sets.
//
//                    Expected performance impact: depends on data shape and page size.
//
//                    Memory / CPU implications: batch fetching can reduce huge joined result sets.
//
//                    Production recommendation: measure both with realistic data.
//                    """,
//            responses = @ApiResponse(responseCode = "200", description = "Batch fetch comparison")
//    )
//    @GetMapping("/batch-fetch")
//    public Response<List<AuthorBooksDto>> demoBatchFetchComparison() {
//        return authorService.demoBatchFetchComparison();
//    }
//
//    @Tag(name = "Performance Comparison APIs")
//    @Operation(
//            summary = "Memory explosion estimate",
//            description = """
//                    Title: Memory Explosion Scenario
//
//                    Purpose: Estimate the number of SQL rows that a multi-collection JOIN FETCH would create.
//
//                    JPQL: select distinct a from Author a join fetch a.books join fetch a.awards
//
//                    Expected SQL: authors join books join awards.
//
//                    Backend execution flow: count the joined rows instead of hydrating the full graph.
//
//                    Why useful: shows risk before running an expensive query.
//
//                    Expected performance impact: generated rows grow multiplicatively.
//
//                    Memory / CPU implications: result-set size drives hydration and de-duplication cost.
//
//                    Production recommendation: avoid multi-collection JOIN FETCH for large datasets.
//                    """,
//            responses = @ApiResponse(responseCode = "200", description = "Memory estimate")
//    )
//    @GetMapping("/performance/memory-explosion")
//    public Response<List<AuthorBasicDto>> demoMemoryExplosionEstimate() {
//        return authorService.demoMemoryExplosionEstimate();
//    }

//    @Tag(name = "Production Recommended Solutions")
//    @Operation(
//            summary = "Production recommendations",
//            description = """
//                    Title: Production Recommended Solutions
//
//                    Purpose: Summarize practical rules after running the demos.
//
//                    JPQL: not applicable.
//
//                    Expected SQL: not applicable.
//
//                    Backend execution flow: returns static guidance.
//
//                    Why useful: turns the demo into operational rules.
//
//                    Expected performance impact: helps choose the right strategy before coding.
//
//                    Memory / CPU implications: warns about unbounded collection fetches.
//
//                    Production recommendation: follow these rules and measure with Hibernate statistics.
//                    """,
//            responses = @ApiResponse(responseCode = "200", description = "Recommendations")
//    )
//    @GetMapping("/production/recommendations")
//    public List<String> productionRecommendations() {
//        return authorService.productionRecommendations();
//    }
}

