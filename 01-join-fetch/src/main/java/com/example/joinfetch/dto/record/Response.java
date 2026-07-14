package com.example.joinfetch.dto.record;

import java.util.List;

public record Response<T>(
        String scenario,

        long ormQueryExecutionCount,
        List<String> ormQueries,

        long sqlStatementCount,
        long preparedStatementCount,
        List<String> sqlStatements,

        long estimatedDatabaseRows,

        double executionTimeMs,
        double cpuTimeMs,
        double threadAllocatedMb,
        long gcCountDelta,

        T result
) {

    public static <T> Response<T> payload(
            T result,
            long estimatedDatabaseRows
    ) {
        return new Response<>(
                null,
                0,
                List.of(),
                0,
                0,
                List.of(),
                estimatedDatabaseRows,
                0,
                0,
                0,
                0,
                result
        );
    }

    public Response<T> withBenchmark(
            String scenario,
            long ormQueryExecutionCount,
            List<String> ormQueries,
            long sqlStatementCount,
            long preparedStatementCount,
            List<String> sqlStatements,
            double executionTimeMs,
            double cpuTimeMs,
            double threadAllocatedMb,
            long gcCountDelta
    ) {
        return new Response<>(
                scenario,
                ormQueryExecutionCount,
                ormQueries,
                sqlStatementCount,
                preparedStatementCount,
                sqlStatements,
                estimatedDatabaseRows,
                executionTimeMs,
                cpuTimeMs,
                threadAllocatedMb,
                gcCountDelta,
                result
        );
    }
}