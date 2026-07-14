package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Author;

import java.util.List;

public record AuthorResponse(
        Long id,
        String name,
        CountryResponse country,
        List<BookResponse> books,
        List<AwardResponse> awards
) {

    public static AuthorResponse fromEntity(Author author) {
        return new AuthorResponse(
                author.getId(),
                author.getName(),
                CountryResponse.fromEntity(author.getCountry()),
                author.getBooks().stream()
                        .map(BookResponse::fromEntity)
                        .toList(),
                author.getAwards().stream()
                        .map(AwardResponse::fromEntity)
                        .toList()
        );
    }
}
