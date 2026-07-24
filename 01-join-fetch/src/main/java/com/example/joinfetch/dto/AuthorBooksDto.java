package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Author;

public record AuthorBooksDto(
        Long id,
        String name,
        Long noBooks
) {
    public static AuthorBooksDto fromEntity(Author author) {
        return new AuthorBooksDto(
                author.getId(),
                author.getName(),
                (long) author.getBooks().size()
        );
    }
}
