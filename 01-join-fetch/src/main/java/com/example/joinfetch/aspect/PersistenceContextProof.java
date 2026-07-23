package com.example.joinfetch.aspect;

import java.util.Map;

public record PersistenceContextProof(
        int returnedObjectCount,
        String returnedObjectClass,
        long entityLoadCount,
        Map<String, Long> entityLoadsByType
) {
}