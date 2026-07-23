package com.example.joinfetch.dto.record;

import com.example.joinfetch.aspect.PersistenceContextProof;

import java.util.List;

public record Response<T>(
        String scenario,

        long ormQueryExecutionCount,
        List<String> ormQueries,

        long sqlStatementCount,
        List<String> sqlStatements,

        long estimatedDatabaseRows,

        double executionTimeMs,
        PersistenceContextProof persistenceContextProof,

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
                List.of(),
                estimatedDatabaseRows,
                0,
                null,
                result
        );
    }

    public Response<T> withBenchmark(
            String scenario,
            long ormQueryExecutionCount,
            List<String> ormQueries,
            long sqlStatementCount,
            List<String> sqlStatements,
            double executionTimeMs
//            double cpuTimeMs,
//            double threadAllocatedMb,
//            long gcCountDelta
    ) {
        return new Response<>(
                scenario,
                ormQueryExecutionCount,
                ormQueries,
                sqlStatementCount,
                sqlStatements,
                estimatedDatabaseRows,
                executionTimeMs,
null,
                result
        );
    }
    public Response<T> withPersistenceContextProof(
            PersistenceContextProof proof
    ) {
        return new Response<>(
                scenario,
                ormQueryExecutionCount,
                ormQueries,
                sqlStatementCount,
                sqlStatements,
                estimatedDatabaseRows,
                executionTimeMs,
                proof,
                result
        );
    }

}