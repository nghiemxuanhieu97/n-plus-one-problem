package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Author;
import com.example.joinfetch.entity.Book;

import java.util.*;

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

    private static List<BookDto> uniqueBooks(Collection<Book> books) {
        Map<Long, BookDto> result = new LinkedHashMap<>();
        for (Book book : books) {
            result.putIfAbsent(book.getId(), BookDto.fromEntity(book));
        }
        return List.copyOf(result.values());
    }
}
