package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Author;
import com.example.joinfetch.entity.Book;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AuthorBooksDto(
        Long id,
        String name,
        List<BookDto> books
) {
    public static AuthorBooksDto fromEntity(Author author) {
        return new AuthorBooksDto(
                author.getId(),
                author.getName(),
                uniqueBooks(author.getBooks())
        );
    }

    private static List<BookDto> uniqueBooks(List<Book> books) {
        Map<Long, BookDto> result = new LinkedHashMap<>();
        for (Book book : books) {
            result.putIfAbsent(book.getId(), BookDto.fromEntity(book));
        }
        return List.copyOf(result.values());
    }
}
