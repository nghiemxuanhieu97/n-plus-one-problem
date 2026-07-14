package com.example.dtoprojection.controller;

import com.example.dtoprojection.dto.recordprojection.AuthorBookAwardCountRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorBookRowRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorRecentBookRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorWithCountryRecord;
import com.example.dtoprojection.service.AuthorRecordProjectionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/record-projections/authors/page")
public class PagedAuthorRecordProjectionController {

    private final AuthorRecordProjectionService projectionService;

    public PagedAuthorRecordProjectionController(AuthorRecordProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @GetMapping("/with-country")
    public Page<AuthorWithCountryRecord> findAuthorsWithCountry(
            @RequestParam(defaultValue = "author") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return projectionService.findAuthorsWithCountry(keyword, pageRequest(page, size));
    }

    @GetMapping("/book-award-counts")
    public Page<AuthorBookAwardCountRecord> findAuthorBookAndAwardCounts(
            @RequestParam(defaultValue = "author") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return projectionService.findAuthorBookAndAwardCounts(keyword, pageRequest(page, size));
    }

    @GetMapping("/recent-books")
    public Page<AuthorRecentBookRecord> findAuthorsWithMostRecentBook(
            @RequestParam(defaultValue = "author") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return projectionService.findAuthorsWithMostRecentBook(keyword, pageRequest(page, size));
    }

    @GetMapping("/book-rows")
    public Page<AuthorBookRowRecord> findAuthorBookRows(
            @RequestParam(defaultValue = "author") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return projectionService.findAuthorBookRows(keyword, pageRequest(page, size));
    }

    private Pageable pageRequest(int page, int size) {
        return PageRequest.of(page, size);
    }
}
