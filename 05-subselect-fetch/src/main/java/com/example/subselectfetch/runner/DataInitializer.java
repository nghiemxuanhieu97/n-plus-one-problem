package com.example.subselectfetch.runner;

import com.example.subselectfetch.entity.Author;
import com.example.subselectfetch.entity.Book;
import com.example.subselectfetch.repository.AuthorRepository;
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

    @Override
    @Transactional
    public void run(String... args) {
        Author tolkien = Author.builder().name("J.R.R. Tolkien").build();
        tolkien.getBooks().add(Book.builder().title("The Hobbit").publishYear(1937).author(tolkien).build());
        tolkien.getBooks().add(Book.builder().title("The Fellowship of the Ring").publishYear(1954).author(tolkien).build());
        tolkien.getBooks().add(Book.builder().title("The Two Towers").publishYear(1954).author(tolkien).build());

        Author rowling = Author.builder().name("J.K. Rowling").build();
        rowling.getBooks().add(Book.builder().title("Harry Potter and the Philosopher's Stone").publishYear(1997).author(rowling).build());
        rowling.getBooks().add(Book.builder().title("Harry Potter and the Chamber of Secrets").publishYear(1998).author(rowling).build());
        rowling.getBooks().add(Book.builder().title("Harry Potter and the Prisoner of Azkaban").publishYear(1999).author(rowling).build());

        Author martin = Author.builder().name("George R.R. Martin").build();
        martin.getBooks().add(Book.builder().title("A Game of Thrones").publishYear(1996).author(martin).build());
        martin.getBooks().add(Book.builder().title("A Clash of Kings").publishYear(1998).author(martin).build());
        martin.getBooks().add(Book.builder().title("A Storm of Swords").publishYear(2000).author(martin).build());

        Author orwell = Author.builder().name("George Orwell").build();
        orwell.getBooks().add(Book.builder().title("Animal Farm").publishYear(1945).author(orwell).build());
        orwell.getBooks().add(Book.builder().title("Nineteen Eighty-Four").publishYear(1949).author(orwell).build());
        orwell.getBooks().add(Book.builder().title("Homage to Catalonia").publishYear(1938).author(orwell).build());

        Author dumas = Author.builder().name("Alexandre Dumas").build();
        dumas.getBooks().add(Book.builder().title("The Three Musketeers").publishYear(1844).author(dumas).build());
        dumas.getBooks().add(Book.builder().title("The Count of Monte Cristo").publishYear(1844).author(dumas).build());
        dumas.getBooks().add(Book.builder().title("Twenty Years After").publishYear(1845).author(dumas).build());

        authorRepository.saveAll(List.of(tolkien, rowling, martin, orwell, dumas));
        log.info("Data initialized: 5 authors, 15 books");
    }
}
