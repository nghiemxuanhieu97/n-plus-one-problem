package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Author;

public record AuthorBooksAwardsDto(
        Long id,
        String name,
        Long noBooks,
        Long noAwards
) {
    public static AuthorBooksAwardsDto fromEntity(Author author) {
        return new AuthorBooksAwardsDto(
                author.getId(),
                author.getName(),
                (long) author.getBooks().size(),
                (long) author.getAwards().size()
        );
    }
}
