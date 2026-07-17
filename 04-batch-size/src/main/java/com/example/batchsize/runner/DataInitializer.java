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
        Publisher modernLibrary = Publisher.builder().name("Modern Library").build();
        Publisher worldBooks = Publisher.builder().name("World Books").build();
        List<Publisher> publishers = List.of(
                classicHouse, fantasyPress, historyPress, modernLibrary, worldBooks
        );
        publisherRepository.saveAll(publishers);

        List<String> authorNames = List.of(
                "J.R.R. Tolkien", "J.K. Rowling", "George R.R. Martin", "George Orwell",
                "Alexandre Dumas", "Jane Austen", "Virginia Woolf", "Ernest Hemingway",
                "Mark Twain", "Agatha Christie", "Haruki Murakami", "Toni Morrison"
        );

        List<Author> authors = new java.util.ArrayList<>();
        for (int index = 0; index < authorNames.size(); index++) {
            Author author = Author.builder().name(authorNames.get(index)).build();
            Publisher publisher = publishers.get(index % publishers.size());
            addBook(author, "Book " + (index + 1) + "A", 1900 + index, publisher);
            addBook(author, "Book " + (index + 1) + "B", 1950 + index, publisher);
            authors.add(author);
        }

        authorRepository.saveAll(authors);
        log.info("Data initialized: 12 authors, 24 books, 5 publishers");
    }

    private void addBook(Author author, String title, int publishYear, Publisher publisher) {
        Book book = Book.builder()
                .title(title)
                .publishYear(publishYear)
                .author(author)
                .publisher(publisher)
                .build();
        author.getBooks().add(book);
        publisher.getBooks().add(book);
    }
}
