package com.example.entitygraph.runner;

import com.example.entitygraph.entity.Author;
import com.example.entitygraph.entity.Award;
import com.example.entitygraph.entity.Book;
import com.example.entitygraph.entity.Country;
import com.example.entitygraph.entity.Publisher;
import com.example.entitygraph.repository.AuthorRepository;
import com.example.entitygraph.repository.CountryRepository;
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
    private final CountryRepository countryRepository;
    private final PublisherRepository publisherRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Country unitedKingdom = Country.builder().name("United Kingdom").build();
        Country unitedStates = Country.builder().name("United States").build();
        Country france = Country.builder().name("France").build();
        countryRepository.saveAll(List.of(unitedKingdom, unitedStates, france));

        Publisher harperCollins = Publisher.builder().name("HarperCollins").build();
        Publisher bloomsbury = Publisher.builder().name("Bloomsbury").build();
        Publisher bantam = Publisher.builder().name("Bantam Books").build();
        Publisher secker = Publisher.builder().name("Secker & Warburg").build();
        Publisher penguin = Publisher.builder().name("Penguin Classics").build();

        publisherRepository.saveAll(List.of(harperCollins, bloomsbury, bantam, secker, penguin));

        Author tolkien = Author.builder().name("J.R.R. Tolkien").country(unitedKingdom).build();
        addBook(tolkien, "The Hobbit", 1937, harperCollins);
        addBook(tolkien, "The Fellowship of the Ring", 1954, harperCollins);
        addBook(tolkien, "The Two Towers", 1954, harperCollins);

        addAward(tolkien, "International Fantasy Award");
        addAward(tolkien, "Locus Award");

        Author rowling = Author.builder().name("J.K. Rowling").country(unitedKingdom).build();
        addBook(rowling, "Harry Potter and the Philosopher's Stone", 1997, bloomsbury);
        addBook(rowling, "Harry Potter and the Chamber of Secrets", 1998, bloomsbury);
        addBook(rowling, "Harry Potter and the Prisoner of Azkaban", 1999, bloomsbury);

        addAward(rowling, "British Book Award");
        addAward(rowling, "Hugo Award");

        Author martin = Author.builder().name("George R.R. Martin").country(unitedStates).build();
        addBook(martin, "A Game of Thrones", 1996, bantam);
        addBook(martin, "A Clash of Kings", 1998, bantam);
        addBook(martin, "A Storm of Swords", 2000, bantam);

        addAward(martin, "Hugo Award");
        addAward(martin, "Nebula Award");

        Author orwell = Author.builder().name("George Orwell").country(unitedKingdom).build();
        addBook(orwell, "Animal Farm", 1945, secker);
        addBook(orwell, "Nineteen Eighty-Four", 1949, secker);
        addBook(orwell, "Homage to Catalonia", 1938, secker);

        addAward(orwell, "Prometheus Hall of Fame");
        addAward(orwell, "Retro Hugo Award");

        Author dumas = Author.builder().name("Alexandre Dumas").country(france).build();
        addBook(dumas, "The Three Musketeers", 1844, penguin);
        addBook(dumas, "The Count of Monte Cristo", 1844, penguin);
        addBook(dumas, "Twenty Years After", 1845, penguin);
        addAward(dumas, "Legion of Honour");
        addAward(dumas, "Prix litteraire");

        authorRepository.saveAll(List.of(tolkien, rowling, martin, orwell, dumas));
        log.info("Data initialized: 5 authors, 15 books, 10 awards, 5 publishers, 3 countries");
    }

    private void addBook(Author author, String title, int publishYear, Publisher publisher) {
        author.getBooks().add(Book.builder()
                .title(title)
                .publishYear(publishYear)
                .author(author)
                .publisher(publisher)
                .build());
    }

    private void addAward(Author author, String name) {
        author.getAwards().add(Award.builder()
                .name(name)
                .author(author)
                .build());
    }
}
