package com.example.joinfetch.dto;

import java.util.List;
import java.util.Map;

public record PerformanceResponse(
        String title,
        String scenarioType,
        String jpql,
        String expectedSql,
        long executionTimeMs,
        long totalAuthors,
        long totalBooksLoaded,
        long totalAwardsLoaded,
        long generatedRowsEstimate,
        StatisticsResponse statistics,
        BenchmarkResponse benchmark,
        List<String> notes,
        Map<String, Long> comparisonTimingsMs
) {
}
