package com.example.joinfetch.dto;

public record BenchmarkResponse(
        String strategy,
        long executionTimeMs,
        double heapBeforeMB,
        double heapAfterMB,
        double heapDeltaMB,
        long gcBefore,
        long gcAfter,
        long gcDelta,
        long cpuTimeMs,
        long ormQueryExecutionCount,
        long entityLoadCount,
        long collectionLoadCount,
        long collectionFetchCount,
        long authorsReturned,
        long estimatedDatabaseRows
) {
}
