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
@RequestMapping("/demo/author")
@RequiredArgsConstructor
@OpenAPIDefinition(
        tags = {
                @Tag(
                        name = "01 - Basic Loading and N+1 Problem",
                        description = "Basic Author loading and intentional N+1 query demonstrations."
                ),
                @Tag(
                        name = "02 - JOIN FETCH without Pagination",
                        description = "JOIN FETCH scenarios for a to-one association, one collection, and multiple collections."
                ),
                @Tag(
                        name = "03 - JOIN FETCH with Pagination",
                        description = "Safe and unsafe pagination patterns when related data must also be loaded."
                )
        }
)
public class AuthorController {

    private final AuthorService authorService;

    @Tag(name = "01 - Basic Loading and N+1 Problem")
    @Operation(
            summary = "Load Authors without associations",
            description = """
                    Goal:
                    Show the safest Author loading case without accessing any lazy association.

                    Use case:
                    On a book search page, the UI needs an Author dropdown. The Author id is used as the filter value, and the Author name is shown as the label.

                    Why this API matches the scenario:
                    The screen only needs Author id and name.
                    It does not need Country, Books, or Awards, so accessing lazy associations would be unnecessary.

                    Repository query:
                    authorRepository.findAll()

                    Service flow:
                    AuthorService.demoAuthorsOnly() loads Authors, maps them to AuthorBasicDto,
                    and returns a small preview of the result.

                    Result DTO:
                    AuthorBasicDto with id and name only.

                    Expected behavior:
                    Hibernate loads Authors only.
                    No lazy SQL is triggered for Country, Books, or Awards.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Author metrics and AuthorBasicDto preview")
    )
    @GetMapping("")
    public Response<List<AuthorBasicDto>> getAuthors() {
        return authorService.demoAuthorsOnly();
    }

    @Tag(name = "01 - Basic Loading and N+1 Problem")
    @Operation(
            summary = "Load Authors and lazily access Country (N+1)",
            description = """
                    Goal:
                    Demonstrate the N+1 query problem caused by accessing a lazy to-one association.

                    Use case:
                    An administration page displays Authors together with their Country and Region.
                    The response therefore needs one Country object nested under each Author.

                    Why this API matches the scenario:
                    The API first loads Authors without Country.
                    While mapping the response, it accesses author.getCountry() for every Author.
                    Because Author.country is LAZY, Hibernate loads a Country only when it is first accessed.

                    Repository query:
                    authorRepository.findAll()

                    Service flow:
                    AuthorService.demoNPlusOneCountry() loads Authors and then maps them to AuthorCountryDto.
                    The mapper intentionally accesses author.getCountry().

                    Result DTO:
                    AuthorCountryDto with Author id, Author name, and Country information.

                    Expected behavior:
                    Hibernate executes one query for Authors and additional queries for Countries.
                    With the default data, 500 Authors reference 50 distinct Countries, so the current Persistence Context allows Hibernate to reuse already loaded Countries: 1 Author query + 50 Country queries.

                    Lesson:
                    A lazy to-one association can also cause N+1 behavior when it is accessed repeatedly.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "N+1 Country benchmark metrics and AuthorCountryDto preview")
    )
    @GetMapping("/n-plus-one/country")
    public Response<List<AuthorCountryDto>> getAuthorsWithNPlusOneCountry() {
        return authorService.demoNPlusOneCountry();
    }

    @Tag(name = "01 - Basic Loading and N+1 Problem")
    @Operation(
            summary = "Load Authors and lazily access Books (N+1)",
            description = """
                    Goal:
                    Demonstrate the N+1 query problem caused by accessing a lazy collection.

                    Use case:
                    An administration page displays Authors and the Books written by each Author.
                    Books must remain nested under the correct Author instead of being returned as a separate flat list.

                    Why this API matches the scenario:
                    The API first loads all Authors.
                    While mapping the response, it accesses author.getBooks() for every Author.
                    Because Author.books is LAZY, Hibernate initializes each Books collection when it is accessed.

                    Repository query:
                    authorRepository.findAll()

                    Service flow:
                    AuthorService.demoNPlusOneBook() loads Authors and then maps them to AuthorBooksDto.
                    The mapper intentionally accesses author.getBooks().

                    Result DTO:
                    AuthorBooksDto with Author id, Author name, and the nested Books collection.

                    Expected behavior:
                    The response data is correct, but Hibernate executes one query for Authors and up to one additional Books query for each Author.
                    With 500 Authors, this produces approximately 501 SQL statements.

                    Lesson:
                    LAZY loading avoids unnecessary work until an association is needed, but accessing a lazy collection inside a loop can create the N+1 query problem.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "N+1 Books benchmark metrics and AuthorBooksDto preview")
    )
    @GetMapping("/n-plus-one/book")
    public Response<List<AuthorBooksDto>> getAuthorsWithNPlusOneBooks() {
        return authorService.demoNPlusOneBook();
    }

    @Tag(name = "01 - Basic Loading and N+1 Problem")
    @Operation(
            summary = "Paginate Authors and lazily access Books (N+1)",
            description = """
                    Goal:
                    Show that pagination reduces the number of Authors loaded but does not remove N+1 behavior when a lazy collection is accessed.

                    Use case:
                    An administration page displays one page of Authors together with the Books written by each Author.
                    Books must remain nested under the correct Author.

                    Why this API matches the scenario:
                    The API first loads only the requested page of Authors.
                    While mapping the page, it accesses author.getBooks() for each Author in that page.
                    Because Author.books is LAZY, each access can trigger another SQL statement.

                    Repository query:
                    authorRepository.findAll(PageRequest.of(page, size))

                    Service flow:
                    AuthorService.demoPaginationNPlusOne() loads a Page<Author> and maps its content to AuthorBooksDto inside the transaction.
                    The mapper intentionally accesses author.getBooks().

                    Result DTO:
                    AuthorBooksDto with Author id, Author name, and the nested Books collection.

                    Expected behavior:
                    Hibernate executes one query for the requested Author page, one count query for pagination metadata, and up to one Books query for each Author in the page.
                    For size=10, this can produce 12 SQL statements.

                    Lesson:
                    Pagination limits the size of the N+1 problem, but lazy collection access inside the page still needs to be controlled.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Paged N+1 Books metrics")
    )
    @GetMapping("/n-plus-one/pagination/book")
    public Response<List<AuthorBooksDto>> demoPaginationNPlusOne(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return authorService.demoPaginationNPlusOne(page, size);
    }

    @Tag(name = "02 - JOIN FETCH without Pagination")
    @Operation(
            summary = "Load Authors with Country using JOIN FETCH",
            description = """
                    Goal:
                    Demonstrate the safe JOIN FETCH case for a to-one association.

                    Use case:
                    An administration page displays Authors together with their Country.
                    Each row needs basic Author information and the related Country information.

                    Why this API matches the scenario:
                    The response needs one Country for each Author.
                    Because Author.country is a to-one association, joining Country does not multiply one Author into several joined rows.

                    Repository query:
                    select a
                    from Author a
                    join fetch a.country
                    order by a.id

                    Service flow:
                    AuthorService.demoJoinFetchToOne() loads Authors and Country together,
                    then maps them to AuthorCountryDto inside the transaction.

                    Result DTO:
                    AuthorCountryDto with Author id, Author name, and Country information.
                    It does not contain Books or Awards.

                    Expected behavior:
                    Hibernate executes one SQL statement that loads Authors and Country together.
                    No additional lazy Country query is triggered during mapping.

                    Lesson:
                    JOIN FETCH is generally safe and effective for to-one associations such as ManyToOne and OneToOne.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "To-one Join Fetch metrics and AuthorCountryDto preview")
    )
    @GetMapping("/join-fetch/country")
    public Response<List<AuthorCountryDto>> getAuthorsWithJoinFetchedCountry() {
        return authorService.demoJoinFetchToOne();
    }

    @Tag(name = "01 - Basic Loading and N+1 Problem")
    @Operation(
            summary = "Load Authors and lazily access Books and Awards (N+1)",
            description = """
                    Goal:
                    Demonstrate N+1 behavior when two lazy collections are accessed for every Author.

                    Use case:
                    An administration page displays Authors together with both their Books and Awards.
                    Each response item must keep both collections nested under the correct Author.

                    Why this API matches the scenario:
                    The API first loads all Authors without Books or Awards.
                    While mapping the response, it accesses both author.getBooks() and author.getAwards() for every Author.
                    Each uninitialized collection can require a separate SQL statement.

                    Repository query:
                    authorRepository.findAll()

                    Service flow:
                    AuthorService.demoNPlusOneBookAward() loads Authors and maps them to AuthorBooksAwardsDto.
                    The mapper intentionally accesses both lazy collections.

                    Result DTO:
                    AuthorBooksAwardsDto with Author id, Author name, Books, and Awards.

                    Expected behavior:
                    The response data is correct, but Hibernate executes one Author query plus Books queries and Awards queries.
                    With 500 Authors, this can produce approximately 1,001 SQL statements.

                    Lesson:
                    Accessing two lazy collections can double the collection-loading part of the N+1 problem.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "N+1 Books and Awards benchmark metrics")
    )
    @GetMapping("/n-plus-one/book-and-award")
    public Response<List<AuthorBooksAwardsDto>> getAuthorsWithNPlusOneBooksAndAwards() {
        return authorService.demoNPlusOneBookAward();
    }

    @Tag(name = "02 - JOIN FETCH without Pagination")
    @Operation(
            summary = "Load Authors with Books using JOIN FETCH",
            description = """
                    Goal:
                    Demonstrate how JOIN FETCH removes the lazy Books N+1 problem by loading Authors and Books in one query.

                    Use case:
                    An administration page displays Authors and all Books written by each Author.
                    Books must remain nested under the correct Author.

                    Why this API matches the scenario:
                    The response needs the Books collection for every loaded Author.
                    The query explicitly fetches Author.books instead of allowing one lazy Books query per Author.

                    Repository query:
                    select distinct a
                    from Author a
                    join fetch a.books
                    order by a.id

                    Service flow:
                    AuthorService.demoJoinFetchBooks() loads Authors and Books together,
                    then maps them to AuthorBooksDto.

                    Result DTO:
                    AuthorBooksDto with Author id, Author name, and the nested Books collection.

                    Expected behavior:
                    Hibernate executes one SQL statement instead of approximately 501 statements.
                    However, the database returns one joined row for each Author-Book pair.
                    With 500 Authors and 20 Books per Author, the result contains approximately 10,000 joined rows.

                    Lesson:
                    JOIN FETCH solves the query-count problem for one collection, but the joined result size must still be measured.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "One-collection Join Fetch metrics and AuthorBooksDto preview")
    )
    @GetMapping("/join-fetch/book")
    public Response<List<AuthorBooksDto>> getAuthorsWithJoinFetchedBooks() {
        return authorService.demoJoinFetchBooks();
    }

    @Tag(name = "02 - JOIN FETCH without Pagination")
    @Operation(
            summary = "Risky: JOIN FETCH Books and Awards causes row explosion",
            description = """
                    Goal:
                    Demonstrate the row explosion caused by fetching two to-many associations in one query.

                    Use case:
                    An administration page tries to display every Author together with both Books and Awards.
                    The final response needs two nested collections under each Author.

                    Why this API matches the scenario:
                    The query fetches Author.books and Author.awards at the same time.
                    For each Author, every Book row is combined with every Award row in the SQL result.
                    This is a Cartesian product between the two collections for that Author.

                    Repository query:
                    select distinct a
                    from Author a
                    join fetch a.books
                    join fetch a.awards
                    order by a.id

                    Service flow:
                    AuthorService.demoCartesianProduct() loads Authors, Books, and Awards in one query,
                    then maps them to AuthorBooksAwardsDto.

                    Result DTO:
                    AuthorBooksAwardsDto with Author id, Author name, Books, and Awards.
                    The final nested result can be correct even though the SQL result contains many repeated combinations.

                    Expected behavior:
                    The query executes as one SQL statement, but it produces approximately one row per Author-Book-Award combination.
                    With 500 Authors, 20 Books per Author, and 10 Awards per Author, Hibernate must process approximately 100,000 joined rows before rebuilding 500 Authors, 10,000 Books, and 5,000 Awards.
                    The distinct keyword prevents duplicate root Authors in the Java result; it does not remove the joined-row multiplication that the database and Hibernate must process.

                    Lesson:
                    A lower SQL statement count does not guarantee better performance.
                    In this benchmark, the two-collection JOIN FETCH processes far more rows and can be slower than the N+1 version.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Multiple-collection JOIN FETCH metrics and AuthorBooksAwardsDto preview")
    )
    @GetMapping("/join-fetch/book-and-award")
    public Response<List<AuthorBooksAwardsDto>> getAuthorsWithJoinFetchedBooksAndAwards() {
        return authorService.demoCartesianProduct();
    }

    @Tag(name = "02 - JOIN FETCH without Pagination")
    @Operation(
            summary = "Failure: JOIN FETCH two List bags",
            description = """
                    Goal:
                    Demonstrate why Hibernate refuses to fetch two bag collections in one query.

                    Use case:
                    An administration page tries to display every Author together with Reviews and Awards.
                    Both associations are mapped as plain List collections without an order column.

                    Why this API matches the scenario:
                    Hibernate treats both Author.reviews and Author.awards as bags.
                    A joined row repeats each Review for every Award and each Award for every Review.
                    Because a bag has no index and may contain real duplicates, Hibernate cannot reliably distinguish stored duplicates from duplicates created by the join.

                    Repository query:
                    select distinct a
                    from Author a
                    join fetch a.reviews
                    join fetch a.awards
                    order by a.id

                    Service flow:
                    AuthorService.findAuthorWithReviewWithAwards() calls the repository method that attempts to fetch both List bags.

                    Result DTO:
                    No AuthorReviewAwardsDto result is produced because Hibernate rejects the query before loading the collections.

                    Expected behavior:
                    Hibernate throws MultipleBagFetchException: cannot simultaneously fetch multiple bags.
                    This is different from the Books-and-Awards endpoint: that query can execute because Books is an indexed List, but it remains vulnerable to row explosion.

                    Lesson:
                    MultipleBagFetchException protects the application from reconstructing two ambiguous bag collections from one multiplied SQL result.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "The request fails with MultipleBagFetchException before producing an AuthorReviewAwardsDto result")
    )
    @GetMapping("/join-fetch/review-and-award")
    public Response<List<AuthorReviewAwardsDto>> findAuthorWithReviewWithAwards() {
        return authorService.findAuthorWithReviewWithAwards();
    }

    @Tag(name = "03 - JOIN FETCH with Pagination")
    @Operation(
            summary = "Safely paginate Authors with JOIN FETCH Country",
            description = """
                    Goal:
                    Demonstrate safe pagination with a to-one JOIN FETCH.

                    Use case:
                    An administration page displays one page of Authors together with the Country of each Author.

                    Why this API matches the scenario:
                    Each Author has at most one Country, so joining Country does not multiply the root Author rows.
                    Database LIMIT and OFFSET therefore still represent the requested number of Authors.

                    Repository query:
                    select a
                    from Author a
                    join fetch a.country
                    order by a.id

                    Service flow:
                    AuthorService.demoPaginationToOne() loads a Page<Author> with Country fetched,
                    then maps the page content to AuthorCountryDto.

                    Result DTO:
                    AuthorCountryDto with Author id, Author name, and Country information.

                    Expected behavior:
                    The database applies pagination directly to the joined data query.
                    Hibernate executes a paginated Author-Country query and a count query, without additional lazy Country queries.

                    Lesson:
                    Pagination with a to-one JOIN FETCH is safe because one joined row still represents one root Author.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Safe to-one pagination metrics")
    )
    @GetMapping("/join-fetch/pagination/country")
    public Response<List<AuthorCountryDto>> demoPaginationToOne(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return authorService.demoPaginationToOne(page, size);
    }

    @Tag(name = "03 - JOIN FETCH with Pagination")
    @Operation(
            summary = "Risky: paginate over a Books JOIN FETCH",
            description = """
                    Goal:
                    Demonstrate why Pageable should not be applied directly to a collection JOIN FETCH.

                    Use case:
                    An administration page requests one page of Authors together with the complete Books collection of each Author.

                    Why this API matches the scenario:
                    Joining Books creates multiple SQL rows for each Author.
                    Applying LIMIT and OFFSET to those joined rows could split one Author's Books collection, so Hibernate cannot safely paginate the SQL result by root Author.

                    Repository query:
                    select distinct a
                    from Author a
                    join fetch a.books
                    order by a.id

                    Service flow:
                    AuthorService.demoPaginationBooksBad() calls the pageable repository method,
                    then maps the returned Authors to AuthorBooksDto.

                    Result DTO:
                    AuthorBooksDto with Author id, Author name, and the complete Books collection.

                    Expected behavior:
                    With hibernate.query.fail_on_pagination_over_collection_fetch=false, Hibernate loads the complete Author-Book joined result, rebuilds and de-duplicates Authors, and applies pagination in JVM memory.
                    With the default data, a request for 10 Authors can still make the database return approximately 10,000 joined rows.
                    Hibernate logs warning HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory.

                    Lesson:
                    A small response page does not mean that the database and Hibernate processed only one page of data.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Collection fetch join pagination metrics")
    )
    @GetMapping("/join-fetch/pagination/book")
    public Response<List<AuthorBooksDto>> demoPaginationBooksBad(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return authorService.demoPaginationBooksBad(page, size);
    }
//
//    @Tag(name = "03 - JOIN FETCH with Pagination")
//    @Operation(
//            summary = "Safely paginate Authors with Books in two steps",
//            description = """
//                    Goal:
//                    Demonstrate the recommended two-step pattern for paginating Authors with a collection.
//
//                    Use case:
//                    An administration page needs one correct page of Authors together with the complete Books collection of each Author.
//
//                    Why this API matches the scenario:
//                    The first query paginates root Author ids, where one row represents one Author.
//                    The second query fetches Books only for the selected Author ids.
//                    This keeps the page correct and bounds the joined data to the requested Authors.
//
//                    Step 1 query:
//                    select a.id
//                    from Author a
//                    order by a.id
//
//                    Step 2 query:
//                    select distinct a
//                    from Author a
//                    join fetch a.books
//                    where a.id in :ids
//                    order by a.id
//
//                    Service flow:
//                    AuthorService.demoSafePagination() pages Author ids first,
//                    fetches Authors and Books for those ids, and maps them to AuthorBooksDto.
//
//                    Result DTO:
//                    AuthorBooksDto with Author id, Author name, and the complete Books collection.
//
//                    Expected behavior:
//                    The page contains the requested Authors without splitting or omitting their Books.
//                    For size=10 and 20 Books per Author, the collection-fetch query processes approximately 200 joined Author-Book rows instead of all 10,000 rows.
//
//                    Lesson:
//                    Paginate root ids first, then fetch the collection for only those ids.
//                    """,
//            responses = @ApiResponse(responseCode = "200", description = "Two-step pagination metrics")
//    )
//    @GetMapping("/pagination/two-step/book")
//    public Response<List<AuthorBooksDto>> demoSafePagination(
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "10") int size
//    ) {
//        return authorService.demoSafePagination(page, size);
//    }

    @Tag(name = "03 - JOIN FETCH with Pagination")
    @Operation(
            summary = "Failure: apply LIMIT and OFFSET to SQL manually joined rows",
            description = """
                    Goal:
                    Demonstrate why manually adding LIMIT and OFFSET to an Author-Book join does not create a correct Author page.

                    Use case:
                    An administration page needs a page of Authors together with the complete Books collection of each Author.
                    The implementation tries to force database pagination over the joined rows.

                    Why this API matches the scenario:
                    A collection join produces multiple rows for each Author.
                    LIMIT and OFFSET therefore count Author-Book rows, not unique Authors.
                    For example, LIMIT 10 can return 10 rows belonging to only Author 1 instead of 10 different Authors.

                    SQL query when queryType=SQL:
                    select a.*
                    from authors a
                    join books b on b.author_id = a.id
                    order by a.id
                    limit :limit offset :offset

                    Service flow:
                    queryType=SQL calls AuthorService.demoSelfDefinedJoinFetchPaginationSQL().
                    Any other value calls AuthorService.demoSelfDefinedJoinFetchPaginationJPQL().

                    Result DTO:
                    The SQL branch returns AuthorBooksDto objects, but the list is intentionally an incorrect Author page.
                    The result may repeat the same Author id several times even though each DTO can show that Author's full Books collection.

                    Expected behavior:
                    The SQL branch demonstrates an incorrect result because pagination is applied to joined rows instead of root Authors.
                    The JPQL branch fails because JPQL does not support LIMIT and OFFSET syntax inside the query string; in the current demo it throws UnknownParameterException.

                    Lesson:
                    Returning the requested number of SQL rows is not the same as returning the requested number of complete Authors.
                    Use /demo/author/pagination/two-step/book for a bounded and correct Author page with Books.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Manual pagination demo; SQL can return incorrect Author page, JPQL currently throws UnknownParameterException")
    )
    @GetMapping("/self-defined/pagination/book")
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