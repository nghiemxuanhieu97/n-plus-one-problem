package com.example.batchsize.runner;

import com.example.batchsize.entity.Author;
import com.example.batchsize.entity.Book;
import com.example.batchsize.entity.Publisher;
import com.example.batchsize.repository.AuthorRepository;
import com.example.batchsize.repository.PublisherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Publisher classicHouse = Publisher.builder().name("Classic House").build();
        Publisher fantasyPress = Publisher.builder().name("Fantasy Press").build();
        Publisher historyPress = Publisher.builder().name("History Press").build();
        publisherRepository.saveAll(List.of(classicHouse, fantasyPress, historyPress));

        Author tolkien = Author.builder().name("J.R.R. Tolkien").build();
        addBook(tolkien, "The Hobbit", 1937, fantasyPress);
        addBook(tolkien, "The Fellowship of the Ring", 1954, fantasyPress);
        addBook(tolkien, "The Two Towers", 1954, fantasyPress);

        Author rowling = Author.builder().name("J.K. Rowling").build();
        addBook(rowling, "Harry Potter and the Philosopher's Stone", 1997, fantasyPress);
        addBook(rowling, "Harry Potter and the Chamber of Secrets", 1998, fantasyPress);
        addBook(rowling, "Harry Potter and the Prisoner of Azkaban", 1999, fantasyPress);

        Author martin = Author.builder().name("George R.R. Martin").build();
        addBook(martin, "A Game of Thrones", 1996, fantasyPress);
        addBook(martin, "A Clash of Kings", 1998, fantasyPress);
        addBook(martin, "A Storm of Swords", 2000, fantasyPress);

        Author orwell = Author.builder().name("George Orwell").build();
        addBook(orwell, "Animal Farm", 1945, classicHouse);
        addBook(orwell, "Nineteen Eighty-Four", 1949, classicHouse);
        addBook(orwell, "Homage to Catalonia", 1938, historyPress);

        Author dumas = Author.builder().name("Alexandre Dumas").build();
        addBook(dumas, "The Three Musketeers", 1844, classicHouse);
        addBook(dumas, "The Count of Monte Cristo", 1844, classicHouse);
        addBook(dumas, "Twenty Years After", 1845, classicHouse);

        authorRepository.saveAll(List.of(tolkien, rowling, martin, orwell, dumas));
        log.info("Data initialized: 5 authors, 15 books, 3 publishers");
    }

    private void addBook(Author author, String title, int publishYear, Publisher publisher) {
        author.getBooks().add(Book.builder()
                .title(title)
                .publishYear(publishYear)
                .author(author)
                .publisher(publisher)
                .build());
    }
}
