package com.example.dtoprojection.service;

import com.example.dtoprojection.dto.classprojection.AuthorBookAwardCountDto;
import com.example.dtoprojection.dto.classprojection.AuthorBookRowDto;
import com.example.dtoprojection.dto.classprojection.AuthorDisplayNameDto;
import com.example.dtoprojection.dto.classprojection.AuthorNameOnlyDto;
import com.example.dtoprojection.dto.classprojection.AuthorRecentBookDto;
import com.example.dtoprojection.dto.classprojection.AuthorWithCountryDto;
import com.example.dtoprojection.repository.AuthorClassProjectionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthorClassProjectionService {

    private static final long SAMPLE_AUTHOR_ID = 1L;

    private final AuthorClassProjectionRepository authorRepository;

    @Transactional(readOnly = true)
    public List<AuthorNameOnlyDto> findAuthorNamesOnly(String keyword) {
        return authorRepository.findAuthorNamesByName(keyword);
    }

    @Transactional(readOnly = true)
    public List<AuthorWithCountryDto> findAuthorsWithCountry(String keyword) {
        return authorRepository.findAuthorsWithCountryByName(keyword);
    }

    @Transactional(readOnly = true)
    public Page<AuthorWithCountryDto> findAuthorsWithCountry(String keyword, Pageable pageable) {
        return authorRepository.findAuthorsWithCountryByName(keyword, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuthorBookAwardCountDto> findAuthorBookAndAwardCounts(String keyword) {
        return authorRepository.findAuthorBookAndAwardCountsByName(keyword);
    }

    @Transactional(readOnly = true)
    public Page<AuthorBookAwardCountDto> findAuthorBookAndAwardCounts(String keyword, Pageable pageable) {
        return authorRepository.findAuthorBookAndAwardCountsByName(keyword, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuthorRecentBookDto> findAuthorsWithMostRecentBook(String keyword) {
        return authorRepository.findAuthorsWithMostRecentBookByName(keyword);
    }

    @Transactional(readOnly = true)
    public Page<AuthorRecentBookDto> findAuthorsWithMostRecentBook(String keyword, Pageable pageable) {
        return authorRepository.findAuthorsWithMostRecentBookByName(keyword, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuthorBookRowDto> findAuthorBookRows(String keyword) {
        return authorRepository.findAuthorBookRowsByName(keyword);
    }

    @Transactional(readOnly = true)
    public Page<AuthorBookRowDto> findAuthorBookRows(String keyword, Pageable pageable) {
        return authorRepository.findAuthorBookRowsByName(keyword, pageable);
    }

    @Transactional(readOnly = true)
    public AuthorDisplayNameDto findSampleAuthorDisplayName() {
        AuthorDisplayNameDto dto = authorRepository.findAuthorDisplayNameById(SAMPLE_AUTHOR_ID);
        if (dto == null) {
            throw new EntityNotFoundException("Author not found with id: " + SAMPLE_AUTHOR_ID);
        }
        return dto;
    }
}
