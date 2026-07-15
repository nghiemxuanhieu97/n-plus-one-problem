package com.example.joinfetch.runner;

import com.example.joinfetch.entity.Award;
import com.example.joinfetch.entity.Author;
import com.example.joinfetch.entity.Book;
import com.example.joinfetch.entity.Country;
import com.example.joinfetch.repository.AuthorRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AuthorRepository authorRepository;
    private final EntityManager entityManager;

    @Value("${demo.large-dataset:false}")
    private boolean largeDataset;

    @Override
    @Transactional
    public void run(String... args) {
        int countryCount = 50;
        int authorCount = largeDataset ? 5_000 : 500;
        int booksPerAuthor = largeDataset ? 50 : 20;
        int awardsPerAuthor = largeDataset ? 20 : 10;

        List<Country> countries = IntStream.rangeClosed(1, countryCount)
                .mapToObj(this::createCountry)
                .toList();
        countries.forEach(entityManager::persist);

        for (int authorIndex = 1; authorIndex <= authorCount; authorIndex++) {
            Country country = entityManager.getReference(
                    Country.class,
                    countries.get((authorIndex - 1) % countries.size()).getId()
            );
            Author author = createAuthor(authorIndex, country, booksPerAuthor, awardsPerAuthor);
            authorRepository.save(author);

            if (authorIndex % 100 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }

        log.info("Data initialized: {} countries, {} authors, {} books, {} awards (largeDataset={})",
                countryCount,
                authorCount,
                authorCount * booksPerAuthor,
                authorCount * awardsPerAuthor,
                largeDataset);
    }

    private Country createCountry(int index) {
        return Country.builder()
                .name("Country " + index)
                .location("Region " + ((index - 1) % 5 + 1))
                .region("Continent Group " + ((index - 1) % 3 + 1))
                .build();
    }

    private Author createAuthor(int authorIndex, Country country, int booksPerAuthor, int awardsPerAuthor) {
        Author author = Author.builder()
                .name("Author " + authorIndex)
                .country(country)
//                .biography("A".repeat(10000))
                .build();

        for (int bookIndex = 1; bookIndex <= booksPerAuthor; bookIndex++) {
            author.getBooks().add(Book.builder()
                    .title("Author " + authorIndex + " - Book " + bookIndex)
                    .publishYear(1980 + (bookIndex % 45))
                    .author(author)
                    .build());
        }

        for (int awardIndex = 1; awardIndex <= awardsPerAuthor; awardIndex++) {
            author.getAwards().add(Award.builder()
                    .name("Author " + authorIndex + " - Award " + awardIndex)
                    .author(author)
                    .build());
        }

        return author;
    }
}
