package com.example.joinfetch.dto.record;

public record BenchmarkSnapshot(
        long startedAtNanos,
        double heapBeforeMb,
        long gcBefore,
        long cpuBeforeNanos
) {
}