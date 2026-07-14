package com.example.dtoprojection.dto.interfaceprojection;

import java.util.List;

public interface AuthorWithBooksProjection {
    Long getId();
    String getName();
    List<BookSummaryProjection> getBooks();
}
