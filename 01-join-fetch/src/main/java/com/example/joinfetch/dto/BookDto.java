package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Book;

public record BookDto(
        Long id,
        String title,
        int publishYear
) {
    public static BookDto fromEntity(Book book) {
        return new BookDto(
                book.getId(),
                book.getTitle(),
                book.getPublishYear()
        );
    }
}
