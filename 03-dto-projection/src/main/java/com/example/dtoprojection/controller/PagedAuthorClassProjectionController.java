package com.example.dtoprojection.controller;

import com.example.dtoprojection.dto.classprojection.AuthorBookAwardCountDto;
import com.example.dtoprojection.dto.classprojection.AuthorBookRowDto;
import com.example.dtoprojection.dto.classprojection.AuthorRecentBookDto;
import com.example.dtoprojection.dto.classprojection.AuthorWithCountryDto;
import com.example.dtoprojection.service.AuthorClassProjectionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/class-projections/authors/page")
public class PagedAuthorClassProjectionController {

    private final AuthorClassProjectionService projectionService;

    public PagedAuthorClassProjectionController(AuthorClassProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @GetMapping("/with-country")
    public Page<AuthorWithCountryDto> findAuthorsWithCountry(
            @RequestParam(defaultValue = "author") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return projectionService.findAuthorsWithCountry(keyword, pageRequest(page, size));
    }

    @GetMapping("/book-award-counts")
    public Page<AuthorBookAwardCountDto> findAuthorBookAndAwardCounts(
            @RequestParam(defaultValue = "author") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return projectionService.findAuthorBookAndAwardCounts(keyword, pageRequest(page, size));
    }

    @GetMapping("/recent-books")
    public Page<AuthorRecentBookDto> findAuthorsWithMostRecentBook(
            @RequestParam(defaultValue = "author") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return projectionService.findAuthorsWithMostRecentBook(keyword, pageRequest(page, size));
    }

    @GetMapping("/book-rows")
    public Page<AuthorBookRowDto> findAuthorBookRows(
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
