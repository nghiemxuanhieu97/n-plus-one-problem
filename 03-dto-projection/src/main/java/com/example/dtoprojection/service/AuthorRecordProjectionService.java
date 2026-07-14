package com.example.dtoprojection.service;

import com.example.dtoprojection.dto.recordprojection.AuthorBookAwardCountRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorBookRowRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorDisplayNameRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorNameOnlyRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorRecentBookRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorWithCountryRecord;
import com.example.dtoprojection.repository.AuthorRecordProjectionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthorRecordProjectionService {

    private static final long SAMPLE_AUTHOR_ID = 1L;

    private final AuthorRecordProjectionRepository authorRepository;

    @Transactional(readOnly = true)
    public List<AuthorNameOnlyRecord> findAuthorNamesOnly(String keyword) {
        return authorRepository.findAuthorNamesByName(keyword);
    }

    @Transactional(readOnly = true)
    public List<AuthorWithCountryRecord> findAuthorsWithCountry(String keyword) {
        return authorRepository.findAuthorsWithCountryByName(keyword);
    }

    @Transactional(readOnly = true)
    public Page<AuthorWithCountryRecord> findAuthorsWithCountry(String keyword, Pageable pageable) {
        return authorRepository.findAuthorsWithCountryByName(keyword, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuthorBookAwardCountRecord> findAuthorBookAndAwardCounts(String keyword) {
        return authorRepository.findAuthorBookAndAwardCountsByName(keyword);
    }

    @Transactional(readOnly = true)
    public Page<AuthorBookAwardCountRecord> findAuthorBookAndAwardCounts(String keyword, Pageable pageable) {
        return authorRepository.findAuthorBookAndAwardCountsByName(keyword, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuthorRecentBookRecord> findAuthorsWithMostRecentBook(String keyword) {
        return authorRepository.findAuthorsWithMostRecentBookByName(keyword);
    }

    @Transactional(readOnly = true)
    public Page<AuthorRecentBookRecord> findAuthorsWithMostRecentBook(String keyword, Pageable pageable) {
        return authorRepository.findAuthorsWithMostRecentBookByName(keyword, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuthorBookRowRecord> findAuthorBookRows(String keyword) {
        return authorRepository.findAuthorBookRowsByName(keyword);
    }

    @Transactional(readOnly = true)
    public Page<AuthorBookRowRecord> findAuthorBookRows(String keyword, Pageable pageable) {
        return authorRepository.findAuthorBookRowsByName(keyword, pageable);
    }

    @Transactional(readOnly = true)
    public AuthorDisplayNameRecord findSampleAuthorDisplayName() {
        AuthorDisplayNameRecord record = authorRepository.findAuthorDisplayNameById(SAMPLE_AUTHOR_ID);
        if (record == null) {
            throw new EntityNotFoundException("Author not found with id: " + SAMPLE_AUTHOR_ID);
        }
        return record;
    }
}
