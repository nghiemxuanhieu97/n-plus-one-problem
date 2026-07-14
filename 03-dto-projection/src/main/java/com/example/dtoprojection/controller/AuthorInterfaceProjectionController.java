package com.example.dtoprojection.controller;

import com.example.dtoprojection.dto.interfaceprojection.AuthorBookAwardCountProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorBrokenDisplayNameOpenProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorDisplayNameOpenProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorNameOnlyProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorRecentBookProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorWithBooksProjection;
import com.example.dtoprojection.dto.interfaceprojection.AuthorWithCountryProjection;
import com.example.dtoprojection.service.AuthorInterfaceProjectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/interface-projections/authors")
public class AuthorInterfaceProjectionController {

    private final AuthorInterfaceProjectionService projectionService;

    public AuthorInterfaceProjectionController(AuthorInterfaceProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @GetMapping("/with-country")
    public List<AuthorWithCountryProjection> findAuthorsWithCountry(
            @RequestParam(defaultValue = "author") String keyword
    ) {
        return projectionService.findAuthorsWithCountry(keyword);
    }

    @GetMapping("/names-only")
    public List<AuthorNameOnlyProjection> findAuthorNamesOnly(
            @RequestParam(defaultValue = "author") String keyword
    ) {
        return projectionService.findAuthorNamesOnly(keyword);
    }

    @GetMapping("/book-award-counts")
    public List<AuthorBookAwardCountProjection> findAuthorBookAndAwardCounts(
            @RequestParam(defaultValue = "author") String keyword
    ) {
        return projectionService.findAuthorBookAndAwardCounts(keyword);
    }

    @GetMapping("/recent-books")
    public List<AuthorRecentBookProjection> findAuthorsWithMostRecentBook(
            @RequestParam(defaultValue = "author") String keyword
    ) {
        return projectionService.findAuthorsWithMostRecentBook(keyword);
    }

    @GetMapping("/with-books")
    public List<AuthorWithBooksProjection> findAuthorsWithBooks(
            @RequestParam(defaultValue = "author") String keyword
    ) {
        return projectionService.findAuthorsWithBooks(keyword);
    }

    @GetMapping("/open-display-name")
    public ResponseEntity<?> findSampleAuthorDisplayName(
            @RequestParam(defaultValue = "valid") String type
    ) {
        Object result = switch (type.toLowerCase()) {
            case "valid" -> projectionService.findSampleAuthorDisplayName(AuthorDisplayNameOpenProjection.class);
            case "broken" -> projectionService.findSampleAuthorDisplayName(AuthorBrokenDisplayNameOpenProjection.class);
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported projection type: " + type + ". Use valid or broken."
            );
        };

        return ResponseEntity.ok(result);
    }
}
