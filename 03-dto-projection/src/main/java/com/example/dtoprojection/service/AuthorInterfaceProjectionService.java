package com.example.dtoprojection.service;

import com.example.dtoprojection.dto.interfaceprojection.AuthorBookAwardCountProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorNameOnlyProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorRecentBookProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorWithBooksProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorWithCountryProjection;
import com.example.dtoprojection.repository.AuthorInterfaceProjectionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthorInterfaceProjectionService {

    private static final long SAMPLE_AUTHOR_ID = 1L;

    private final AuthorInterfaceProjectionRepository authorRepository;

    @Transactional(readOnly = true)
    public List<AuthorWithCountryProjection> findAuthorsWithCountry(String keyword) {
        return authorRepository.findAuthorsWithCountryByNameContainingIgnoreCase(keyword);
    }

    @Transactional(readOnly = true)
    public Page<AuthorWithCountryProjection> findAuthorsWithCountry(String keyword, Pageable pageable) {
        return authorRepository.findAuthorsWithCountryByNameContainingIgnoreCase(keyword, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuthorNameOnlyProjection> findAuthorNamesOnly(String keyword) {
        return authorRepository.findAuthorNamesByNameContainingIgnoreCase(keyword);
    }

    @Transactional(readOnly = true)
    public List<AuthorBookAwardCountProjection> findAuthorBookAndAwardCounts(String keyword) {
        return authorRepository.findAuthorBookAndAwardCountsByName(keyword);
    }

    @Transactional(readOnly = true)
    public Page<AuthorBookAwardCountProjection> findAuthorBookAndAwardCounts(String keyword, Pageable pageable) {
        return authorRepository.findAuthorBookAndAwardCountsByName(keyword, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuthorRecentBookProjection> findAuthorsWithMostRecentBook(String keyword) {
        return authorRepository.findAuthorsWithMostRecentBookByName(keyword);
    }

    @Transactional(readOnly = true)
    public Page<AuthorRecentBookProjection> findAuthorsWithMostRecentBook(String keyword, Pageable pageable) {
        return authorRepository.findAuthorsWithMostRecentBookByName(keyword, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuthorWithBooksProjection> findAuthorsWithBooks(String keyword) {
        return authorRepository.findAuthorsWithBooksByNameContainingIgnoreCase(keyword);
    }

    @Transactional(readOnly = true)
    public Page<AuthorWithBooksProjection> findAuthorsWithBooks(String keyword, Pageable pageable) {
        return authorRepository.findAuthorsWithBooksByNameContainingIgnoreCase(keyword, pageable);
    }

    @Transactional(readOnly = true)
    public <T> T findSampleAuthorDisplayName(Class<T> projectionType) {
        return authorRepository
                .findProjectedById(SAMPLE_AUTHOR_ID, projectionType)
                .orElseThrow(() -> new EntityNotFoundException("Author not found with id: " + SAMPLE_AUTHOR_ID));
    }
}
