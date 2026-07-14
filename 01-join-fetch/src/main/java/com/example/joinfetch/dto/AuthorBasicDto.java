package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Author;

public record AuthorBasicDto(
        Long id,
        String name
) {
    public static AuthorBasicDto fromEntity(Author author) {
        return new AuthorBasicDto(
                author.getId(),
                author.getName()
        );
    }
}
