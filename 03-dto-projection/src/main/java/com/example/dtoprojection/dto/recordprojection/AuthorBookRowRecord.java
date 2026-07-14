package com.example.dtoprojection.dto.recordprojection;

public record AuthorBookRowRecord(
        Long authorId,
        String authorName,
        Long bookId,
        String bookTitle,
        int publishYear
) {
}
