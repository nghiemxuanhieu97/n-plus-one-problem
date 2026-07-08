package com.example.dtoprojection.dto;

/**
 * Interface projection — Spring Data JPA maps query result columns
 * to getter methods by name (camelCase matches alias in JPQL).
 */
public interface AuthorBookCountProjection {
    String getName();
    Long getBookCount();
}
