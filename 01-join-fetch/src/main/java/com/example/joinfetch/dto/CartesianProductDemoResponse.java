package com.example.joinfetch.dto;

import java.util.List;

public record CartesianProductDemoResponse(
        String title,
        String jpql,
        String expectedSql,
        long executionTimeMs,
        long totalAuthors,
        long totalBooksLoaded,
        long totalAwardsLoaded,
        long generatedRowsEstimate,
        long rowsPerAuthorEstimate,
        StatisticsResponse statistics,
        BenchmarkResponse benchmark,
        List<String> notes
) {
}
