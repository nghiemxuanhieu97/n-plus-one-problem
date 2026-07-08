package com.example.dtoprojection.dto;

/**
 * Class-based (record) projection — constructed via JPQL "new" expression.
 * Fully immutable, no entity lifecycle.
 */
public record AuthorBooksDto(String authorName, String bookTitle, int publishYear) {
}
