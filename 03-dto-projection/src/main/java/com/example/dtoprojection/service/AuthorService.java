package com.example.dtoprojection.service;

import com.example.dtoprojection.dto.AuthorBookCountProjection;
import com.example.dtoprojection.dto.AuthorBooksDto;
import com.example.dtoprojection.entity.Author;
import com.example.dtoprojection.repository.AuthorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorService {

    private final AuthorRepository authorRepository;

    @Transactional(readOnly = true)
    public void demonstrateNPlusOneProblem() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [PROBLEM] N+1 Queries                           |");
        log.info("|  findAll() returns full entities with LAZY books |");
        log.info("+--------------------------------------------------+");

        List<Author> authors = authorRepository.findAll(); // SQL #1
        for (Author author : authors) {
            log.info("  [{}] {} -> {} books", author.getId(), author.getName(), author.getBooks().size());
        }

        log.info("  --> Total SQL executed: 1 + {} = {} queries", authors.size(), 1 + authors.size());
    }

    @Transactional(readOnly = true)
    public void demonstrateInterfaceProjection() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [SOLUTION A] Interface Projection               |");
        log.info("|  SELECT a.name, COUNT(b) ... GROUP BY a.id       |");
        log.info("|  Returns: List<AuthorBookCountProjection>        |");
        log.info("+--------------------------------------------------+");

        List<AuthorBookCountProjection> projections = authorRepository.findAuthorBookCounts(); // SQL #1
        for (AuthorBookCountProjection p : projections) {
            log.info("  {} -> {} books", p.getName(), p.getBookCount());
        }

        log.info("  --> Total SQL executed: 1 (aggregation query — no entities, no lazy loading)");
    }

    @Transactional(readOnly = true)
    public void demonstrateClassProjection() {
        log.info("");
        log.info("+--------------------------------------------------+");
        log.info("|  [SOLUTION B] Class Projection (Record DTO)      |");
        log.info("|  SELECT new AuthorBooksDto(a.name, b.title, ...) |");
        log.info("|  Returns: List<AuthorBooksDto> (flat list)       |");
        log.info("+--------------------------------------------------+");

        List<AuthorBooksDto> dtos = authorRepository.findAllAuthorBooks(); // SQL #1
        String currentAuthor = "";
        for (AuthorBooksDto dto : dtos) {
            if (!dto.authorName().equals(currentAuthor)) {
                currentAuthor = dto.authorName();
                log.info("  Author: {}", currentAuthor);
            }
            log.info("    - {} ({})", dto.bookTitle(), dto.publishYear());
        }

        log.info("  --> Total SQL executed: 1 (JOIN query — returns flat DTO rows)");
    }
}
