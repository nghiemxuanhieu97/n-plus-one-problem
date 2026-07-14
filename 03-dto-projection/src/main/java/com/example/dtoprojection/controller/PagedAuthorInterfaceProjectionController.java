package com.example.dtoprojection.controller;

import com.example.dtoprojection.dto.interfaceprojection.AuthorBookAwardCountProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorRecentBookProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorWithBooksProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorWithCountryProjection;
import com.example.dtoprojection.service.AuthorInterfaceProjectionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/interface-projections/authors/page")
public class PagedAuthorInterfaceProjectionController {

    private final AuthorInterfaceProjectionService projectionService;

    public PagedAuthorInterfaceProjectionController(AuthorInterfaceProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @GetMapping("/with-country")
    public Page<AuthorWithCountryProjection> findAuthorsWithCountry(
            @RequestParam(defaultValue = "author") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return projectionService.findAuthorsWithCountry(keyword, pageRequest(page, size));
    }

    @GetMapping("/book-award-counts")
    public Page<AuthorBookAwardCountProjection> findAuthorBookAndAwardCounts(
            @RequestParam(defaultValue = "author") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return projectionService.findAuthorBookAndAwardCounts(keyword, pageRequest(page, size));
    }

    @GetMapping("/recent-books")
    public Page<AuthorRecentBookProjection> findAuthorsWithMostRecentBook(
            @RequestParam(defaultValue = "author") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return projectionService.findAuthorsWithMostRecentBook(keyword, pageRequest(page, size));
    }

    @GetMapping("/with-books")
    public Page<AuthorWithBooksProjection> findAuthorsWithBooks(
            @RequestParam(defaultValue = "author") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return projectionService.findAuthorsWithBooks(keyword, pageRequest(page, size));
    }

    private Pageable pageRequest(int page, int size) {
        return PageRequest.of(page, size);
    }
}
