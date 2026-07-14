package com.example.joinfetch.dto.record;

public record BenchmarkSnapshot(
        long startedAtNanos,
        long gcBefore,
        long cpuBeforeNanos,
        long allocatedBytesBefore
) {
}