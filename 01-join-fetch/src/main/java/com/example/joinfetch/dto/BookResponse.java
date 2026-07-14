package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Book;

public record BookResponse(
        Long id,
        String title,
        int publishYear,
        Long authorId
) {

    public static BookResponse fromEntity(Book book) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getPublishYear(),
                book.getAuthor() == null ? null : book.getAuthor().getId()
        );
    }
}
