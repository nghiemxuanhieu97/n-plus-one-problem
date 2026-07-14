package com.example.joinfetch.dto;

import java.util.List;

public record PaginationDemoResponse(
        String title,
        String jpql,
        String expectedSql,
        int page,
        int size,
        int recordsReturned,
        long executionTimeMs,
        Long idsQueryTimeMs,
        Long fetchQueryTimeMs,
        long totalAuthors,
        long totalBooksLoaded,
        long totalAwardsLoaded,
        long generatedRowsEstimate,
        StatisticsResponse statistics,
        BenchmarkResponse benchmark,
        List<String> notes
) {
}
