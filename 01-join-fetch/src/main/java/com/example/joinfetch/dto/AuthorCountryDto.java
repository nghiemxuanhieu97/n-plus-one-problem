package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Author;

public record AuthorCountryDto(
        Long id,
        String name,
        CountryDto country
) {
    public static AuthorCountryDto fromEntity(Author author) {
        return new AuthorCountryDto(
                author.getId(),
                author.getName(),
                CountryDto.fromEntity(author.getCountry())
        );
    }
}
