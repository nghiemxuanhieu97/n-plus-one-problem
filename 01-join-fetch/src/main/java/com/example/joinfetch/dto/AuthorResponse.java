package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Author;

public record AuthorResponse(
        Long id,
        String name,
        CountryResponse country,
        Long noBooks,
        Long noAwards
) {

    public static AuthorResponse fromEntity(Author author) {
        return new AuthorResponse(
                author.getId(),
                author.getName(),
                CountryResponse.fromEntity(author.getCountry()),
                (long) author.getBooks().size(),
                (long) author.getAwards().size()
        );
    }
}
