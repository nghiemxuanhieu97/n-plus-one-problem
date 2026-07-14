package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Author;
import com.example.joinfetch.entity.Award;
import com.example.joinfetch.entity.Book;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AuthorBooksAwardsDto(
        Long id,
        String name,
        List<BookDto> books,
        List<AwardDto> awards
) {
    public static AuthorBooksAwardsDto fromEntity(Author author) {
        return new AuthorBooksAwardsDto(
                author.getId(),
                author.getName(),
                uniqueBooks(author.getBooks()),
                uniqueAwards(author.getAwards())
        );
    }

    private static List<BookDto> uniqueBooks(List<Book> books) {
        Map<Long, BookDto> result = new LinkedHashMap<>();
        for (Book book : books) {
            result.putIfAbsent(book.getId(), BookDto.fromEntity(book));
        }
        return List.copyOf(result.values());
    }

    private static List<AwardDto> uniqueAwards(Collection<Award> awards) {
        Map<Long, AwardDto> result = new LinkedHashMap<>();
        for (Award award : awards) {
            result.putIfAbsent(award.getId(), AwardDto.fromEntity(award));
        }
        return List.copyOf(result.values());
    }
}
