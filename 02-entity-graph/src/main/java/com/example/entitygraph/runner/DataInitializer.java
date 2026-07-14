package com.example.entitygraph.runner;

import com.example.entitygraph.entity.Author;
import com.example.entitygraph.entity.Book;
import com.example.entitygraph.entity.Publisher;
import com.example.entitygraph.repository.AuthorRepository;
import com.example.entitygraph.repository.PublisherRepository;
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
        Publisher harperCollins = Publisher.builder().name("HarperCollins").build();
        Publisher bloomsbury = Publisher.builder().name("Bloomsbury").build();
        Publisher bantam = Publisher.builder().name("Bantam Books").build();
        Publisher secker = Publisher.builder().name("Secker & Warburg").build();
        Publisher penguin = Publisher.builder().name("Penguin Classics").build();

        publisherRepository.saveAll(List.of(harperCollins, bloomsbury, bantam, secker, penguin));

        Author tolkien = Author.builder().name("J.R.R. Tolkien").build();
        addBook(tolkien, "The Hobbit", 1937, harperCollins);
        addBook(tolkien, "The Fellowship of the Ring", 1954, harperCollins);
        addBook(tolkien, "The Two Towers", 1954, harperCollins);

        Author rowling = Author.builder().name("J.K. Rowling").build();
        addBook(rowling, "Harry Potter and the Philosopher's Stone", 1997, bloomsbury);
        addBook(rowling, "Harry Potter and the Chamber of Secrets", 1998, bloomsbury);
        addBook(rowling, "Harry Potter and the Prisoner of Azkaban", 1999, bloomsbury);

        Author martin = Author.builder().name("George R.R. Martin").build();
        addBook(martin, "A Game of Thrones", 1996, bantam);
        addBook(martin, "A Clash of Kings", 1998, bantam);
        addBook(martin, "A Storm of Swords", 2000, bantam);

        Author orwell = Author.builder().name("George Orwell").build();
        addBook(orwell, "Animal Farm", 1945, secker);
        addBook(orwell, "Nineteen Eighty-Four", 1949, secker);
        addBook(orwell, "Homage to Catalonia", 1938, secker);

        Author dumas = Author.builder().name("Alexandre Dumas").build();
        addBook(dumas, "The Three Musketeers", 1844, penguin);
        addBook(dumas, "The Count of Monte Cristo", 1844, penguin);
        addBook(dumas, "Twenty Years After", 1845, penguin);

        authorRepository.saveAll(List.of(tolkien, rowling, martin, orwell, dumas));
        log.info("Data initialized: 5 authors, 15 books, 5 publishers");
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
