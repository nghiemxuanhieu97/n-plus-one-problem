package com.example.joinfetch.dto;

public record StatisticsResponse(
        long entityLoadCount,
        long collectionFetchCount,
        long queryExecutionCount,
        long prepareStatementCount
) {
}
