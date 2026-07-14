package com.example.dtoprojection.controller;

import com.example.dtoprojection.dto.recordprojection.AuthorBookAwardCountRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorBookRowRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorDisplayNameRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorNameOnlyRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorRecentBookRecord;
import com.example.dtoprojection.dto.recordprojection.AuthorWithCountryRecord;
import com.example.dtoprojection.service.AuthorRecordProjectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/record-projections/authors")
public class AuthorRecordProjectionController {

    private final AuthorRecordProjectionService projectionService;

    public AuthorRecordProjectionController(AuthorRecordProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @GetMapping("/names-only")
    public List<AuthorNameOnlyRecord> findAuthorNamesOnly(@RequestParam(defaultValue = "author") String keyword) {
        return projectionService.findAuthorNamesOnly(keyword);
    }

    @GetMapping("/with-country")
    public List<AuthorWithCountryRecord> findAuthorsWithCountry(@RequestParam(defaultValue = "author") String keyword) {
        return projectionService.findAuthorsWithCountry(keyword);
    }

    @GetMapping("/book-award-counts")
    public List<AuthorBookAwardCountRecord> findAuthorBookAndAwardCounts(
            @RequestParam(defaultValue = "author") String keyword
    ) {
        return projectionService.findAuthorBookAndAwardCounts(keyword);
    }

    @GetMapping("/recent-books")
    public List<AuthorRecentBookRecord> findAuthorsWithMostRecentBook(
            @RequestParam(defaultValue = "author") String keyword
    ) {
        return projectionService.findAuthorsWithMostRecentBook(keyword);
    }

    @GetMapping("/book-rows")
    public List<AuthorBookRowRecord> findAuthorBookRows(@RequestParam(defaultValue = "author") String keyword) {
        return projectionService.findAuthorBookRows(keyword);
    }

    @GetMapping("/display-name")
    public AuthorDisplayNameRecord findSampleAuthorDisplayName() {
        return projectionService.findSampleAuthorDisplayName();
    }
}
