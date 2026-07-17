package com.example.batchsize.controller;

import com.example.batchsize.service.AuthorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/batch-size")
@RequiredArgsConstructor
@Tag(name = "BatchSize demos", description = "Execute one lazy-loading scenario, then inspect Hibernate SQL and [METRICS] in the console")
public class BatchSizeDemoController {

    private final AuthorService authorService;
    private final Environment environment;

    @GetMapping("/00-full-flow")
    @Operation(
            summary = "Run the complete BatchSize demo flow",
            description = """
                    Runs the scenarios in presentation order:
                    baseline N+1, collection batching, entity-proxy batching,
                    global fallback, preload trade-off, and parent pagination.
                    The global fallback scenario is skipped unless the global-batch profile is active.
                    Follow the SQL and [METRICS] blocks in the application console.
                    """
    )
    public FullFlowResponse fullFlow() {
        authorService.demonstrateBaselineWithoutBatch();
        authorService.demonstrateBatchSize();
        authorService.demonstrateClassLevelBatchSize();
        authorService.demonstrateApplicationLevelBatchSize();
        authorService.demonstrateBatchPreloadTradeoff();
        authorService.demonstratePaginationFriendlyAccess();

        return new FullFlowResponse(
                "Complete BatchSize flow",
                globalBatchSize() > 0 ? "global-batch" : "default",
                List.of(
                        "01 - Baseline: unbatched Publisher.books",
                        "02 - Association batch: Author.books",
                        "03 - Entity proxy batch: Book.author",
                        "04 - Global fallback: Book.publisher (requires global-batch)",
                        "05 - Preload trade-off",
                        "06 - Parent pagination"
                ),
                "Completed. Read each SQL and [METRICS] block in the application console."
        );
    }

    @GetMapping("/01-baseline-no-batch")
    @Operation(summary = "Baseline without annotation", description = "Accesses unannotated Publisher.books. Default profile expects 6 statements; global-batch expects 2.")
    public DemoResponse baseline() {
        authorService.demonstrateBaselineWithoutBatch();
        boolean global = globalBatchSize() > 0;
        return response("Unannotated Publisher.books", global ? "2 statements via global fallback" : "6 statements: 1 + 5", "Compare this endpoint with and without global-batch profile");
    }

    @GetMapping("/02-association-batch")
    @Operation(summary = "@BatchSize on Author.books", description = "Loads 12 Authors and initializes their Book collections in batches of 5.")
    public DemoResponse associationBatch() {
        authorService.demonstrateBatchSize();
        return response("Association-level @BatchSize", "1 + ceil(12/5) = 4 statements", "Look for three Book queries using author_id IN (...)");
    }

    @GetMapping("/03-entity-proxy-batch")
    @Operation(summary = "@BatchSize on Author class", description = "Loads Books first, then initializes lazy Author proxies in batches of 5.")
    public DemoResponse entityProxyBatch() {
        authorService.demonstrateClassLevelBatchSize();
        return response("Class-level @BatchSize", "1 Book query + 3 Author proxy batches", "Look for Author id IN (...), not Book author_id IN (...)");
    }

    @GetMapping("/04-global-batch")
    @Operation(summary = "Global default batch size", description = "Requires profile global-batch. Demonstrates fallback size 7 on Book.publisher without annotations.")
    public DemoResponse globalBatch() {
        authorService.demonstrateApplicationLevelBatchSize();
        int size = globalBatchSize();
        return response("Global default_batch_fetch_size", size > 0 ? "Active with size " + size : "Skipped because profile is inactive", "Restart with --spring.profiles.active=global-batch");
    }

    @GetMapping("/05-preload-trade-off")
    @Operation(summary = "Preload trade-off", description = "Accesses Books of only the first Author, but Hibernate can initialize up to 5 collections.")
    public DemoResponse preloadTradeoff() {
        authorService.demonstrateBatchPreloadTradeoff();
        return response("Batch preload trade-off", "2 statements, but data for up to 5 Authors is loaded", "Compare collections loaded with the one collection accessed in code");
    }

    @GetMapping("/06-pagination")
    @Operation(summary = "Parent pagination with BatchSize", description = "Pages 3 Authors first, then loads Books using one IN query.")
    public DemoResponse pagination() {
        authorService.demonstratePaginationFriendlyAccess();
        return response("Pagination + @BatchSize", "3 statements: page + count + Book batch", "The IN query has 5 slots although the page has only 3 Authors");
    }

    private int globalBatchSize() {
        return environment.getProperty("spring.jpa.properties.hibernate.default_batch_fetch_size", Integer.class, 0);
    }

    private DemoResponse response(String scenario, String expected, String consoleHint) {
        return new DemoResponse(scenario, expected, consoleHint, "Scenario completed. Read the application console for SQL and [METRICS].");
    }

    public record DemoResponse(String scenario, String expected, String consoleHint, String status) {
    }

    public record FullFlowResponse(
            String scenario,
            String activeMode,
            List<String> executionOrder,
            String status
    ) {
    }
}
