package com.example.dtoprojection.dto.classprojection;

public class AuthorBookRowDto {
    private final Long authorId;
    private final String authorName;
    private final Long bookId;
    private final String bookTitle;
    private final int publishYear;

    public AuthorBookRowDto(Long authorId, String authorName, Long bookId, String bookTitle, int publishYear) {
        this.authorId = authorId;
        this.authorName = authorName;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.publishYear = publishYear;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public int getPublishYear() {
        return publishYear;
    }
}
