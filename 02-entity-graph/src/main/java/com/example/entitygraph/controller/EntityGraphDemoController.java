package com.example.entitygraph.controller;

import com.example.entitygraph.service.AuthorService;
import com.example.entitygraph.dto.AuthorDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/entity-graph")
@RequiredArgsConstructor
@Tag(name = "EntityGraph demos", description = "Execute one fetch scenario, then inspect Hibernate SQL and [METRICS] in the console")
public class EntityGraphDemoController {

    private final AuthorService authorService;

    @GetMapping("/01-baseline-n-plus-one")
    @Operation(summary = "Baseline N+1", description = "Loads 5 Authors, then lazily accesses Books. Expected: 6 JDBC statements.")
    public DemoResponse baseline() {
        authorService.demonstrateNPlusOneProblem();
        return response("Baseline N+1", "1 Author query + 5 lazy Book queries", "Look for repeated WHERE author_id = ?");
    }

    @GetMapping("/02-dynamic-graph")
    @Operation(summary = "Dynamic EntityGraph", description = "Uses attributePaths=books. Expected: one joined statement.")
    public DemoResponse dynamicGraph() {
        authorService.demonstrateDynamicEntityGraph();
        return response("Dynamic EntityGraph", "1 JDBC statement", "Look for LEFT JOIN books");
    }

    @GetMapping("/03-derived-query")
    @Operation(summary = "Derived query plus EntityGraph", description = "Spring Data creates the name filter while the named graph fetches Books.")
    public DemoResponse derivedQuery() {
        authorService.demonstrateEntityGraphOnDerivedQuery();
        return response("Derived query + EntityGraph", "Filter and fetch plan stay separate", "Look for name filter plus LEFT JOIN books");
    }

    @GetMapping("/04-nested-graph")
    @Operation(summary = "Nested graph", description = "Compares dynamic and named Author -> Books -> Publisher graphs.")
    public DemoResponse nestedGraph() {
        authorService.demonstrateNestedEntityGraph();
        return response("Nested EntityGraph", "Books and Publishers loaded by a deep graph", "Two metric blocks are printed: dynamic and named");
    }

    @GetMapping("/05-pagination")
    @Operation(summary = "Pagination boundaries", description = "Runs safe ToOne pagination, unsafe ToMany pagination, then safe two-step pagination.")
    public DemoResponse pagination() {
        authorService.demonstratePaginationBoundaries();
        return response("EntityGraph pagination", "ToOne=stable; ToMany=in-memory risk; two-step=safe", "Look for warning HHH90003004");
    }

    @GetMapping("/06-multiple-collections")
    @Operation(summary = "Two ToMany collections", description = "Fetches Books and Awards together to expose Cartesian row multiplication.")
    public DemoResponse multipleCollections() {
        authorService.demonstrateMultipleCollectionsTradeoff();
        return response("Books + Awards graph", "1 statement but about 30 joined rows", "Compare distinct Books with hydrated List size");
    }

    @GetMapping("/07-named-graph")
    @Operation(summary = "Named EntityGraph", description = "Uses reusable graph Author.withBooks. SQL is intentionally similar to the dynamic graph.")
    public DemoResponse namedGraph() {
        authorService.demonstrateNamedEntityGraph();
        return response("Named EntityGraph", "Same SQL shape as dynamic graph", "The benefit is reuse, not fewer statements");
    }

    @GetMapping("/08-find-by-id")
    @Operation(summary = "EntityGraph on findById", description = "Overrides JpaRepository.findById to load one Author and Books together.")
    public DemoResponse findById() {
        authorService.demonstrateFindByIdWithEntityGraph();
        return response("findById + EntityGraph", "One Author detail query with Books", "Useful for detail screens");
    }

    @GetMapping("/09-fetch-vs-load")
    @Operation(summary = "FETCH graph versus LOAD graph", description = "Uses intentionally EAGER country to make the semantic difference observable.")
    public DemoResponse fetchVsLoad() {
        authorService.demonstrateFetchAndLoadGraphTypes();
        return response("FETCH vs LOAD", "FETCH leaves Country uninitialized; LOAD respects EAGER Country", "Expected metrics: FETCH=1, LOAD=4 statements");
    }

    @GetMapping("/10-query-plus-graph")
    @Operation(summary = "Custom JPQL plus EntityGraph", description = "JPQL chooses rows and EntityGraph chooses associations.")
    public DemoResponse queryPlusGraph() {
        authorService.demonstrateQueryWithEntityGraph();
        return response("JPQL + EntityGraph", "Custom condition plus reusable fetch plan", "Inspect the OR condition and Books join");
    }

    @GetMapping("/11-programmatic-graph")
    @Operation(summary = "Programmatic EntityGraph", description = "Builds a graph at runtime with EntityManager and jakarta.persistence.fetchgraph.")
    public DemoResponse programmaticGraph() {
        authorService.demonstrateProgrammaticEntityGraph();
        return response("Programmatic EntityGraph", "Runtime-selected Books fetch", "Useful when fetch fields depend on runtime flags");
    }

    @GetMapping("/authors/{id}")
    @Operation(
            summary = "Runtime-selected Author graph",
            description = "Builds a fetch graph from includeBooks/includeAwards and returns the selected associations. " +
                    "Try all four boolean combinations and compare SQL. When both collections are requested, " +
                    "Hibernate may use secondary selects because an EntityGraph defines what must be loaded, not one exact SQL shape."
    )
    public AuthorDetailResponse authorWithRuntimeGraph(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean includeBooks,
            @RequestParam(defaultValue = "false") boolean includeAwards
    ) {
        return authorService.findAuthorWithRuntimeGraph(id, includeBooks, includeAwards);
    }

    private DemoResponse response(String scenario, String expected, String consoleHint) {
        return new DemoResponse(scenario, expected, consoleHint, "Scenario completed. Read the application console for SQL and [METRICS].");
    }

    public record DemoResponse(String scenario, String expected, String consoleHint, String status) {
    }
}
