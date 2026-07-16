package com.example.entitygraph.dto;

import java.util.List;

public record AuthorDetailResponse(
        Long id,
        String name,
        String country,
        boolean booksIncluded,
        boolean awardsIncluded,
        List<BookSummary> books,
        List<AwardSummary> awards
) {
    public record BookSummary(Long id, String title, int publishYear, String publisher) {
    }

    public record AwardSummary(Long id, String name) {
    }
}
