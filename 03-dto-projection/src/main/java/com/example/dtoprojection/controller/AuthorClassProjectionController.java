package com.example.dtoprojection.controller;

import com.example.dtoprojection.dto.classprojection.AuthorBookAwardCountDto;
import com.example.dtoprojection.dto.classprojection.AuthorBookRowDto;
import com.example.dtoprojection.dto.classprojection.AuthorDisplayNameDto;
import com.example.dtoprojection.dto.classprojection.AuthorNameOnlyDto;
import com.example.dtoprojection.dto.classprojection.AuthorRecentBookDto;
import com.example.dtoprojection.dto.classprojection.AuthorWithCountryDto;
import com.example.dtoprojection.service.AuthorClassProjectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/class-projections/authors")
public class AuthorClassProjectionController {

    private final AuthorClassProjectionService projectionService;

    public AuthorClassProjectionController(AuthorClassProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @GetMapping("/names-only")
    public List<AuthorNameOnlyDto> findAuthorNamesOnly(@RequestParam(defaultValue = "author") String keyword) {
        return projectionService.findAuthorNamesOnly(keyword);
    }

    @GetMapping("/with-country")
    public List<AuthorWithCountryDto> findAuthorsWithCountry(@RequestParam(defaultValue = "author") String keyword) {
        return projectionService.findAuthorsWithCountry(keyword);
    }

    @GetMapping("/book-award-counts")
    public List<AuthorBookAwardCountDto> findAuthorBookAndAwardCounts(
            @RequestParam(defaultValue = "author") String keyword
    ) {
        return projectionService.findAuthorBookAndAwardCounts(keyword);
    }

    @GetMapping("/recent-books")
    public List<AuthorRecentBookDto> findAuthorsWithMostRecentBook(
            @RequestParam(defaultValue = "author") String keyword
    ) {
        return projectionService.findAuthorsWithMostRecentBook(keyword);
    }

    @GetMapping("/book-rows")
    public List<AuthorBookRowDto> findAuthorBookRows(@RequestParam(defaultValue = "author") String keyword) {
        return projectionService.findAuthorBookRows(keyword);
    }

    @GetMapping("/display-name")
    public AuthorDisplayNameDto findSampleAuthorDisplayName() {
        return projectionService.findSampleAuthorDisplayName();
    }
}
