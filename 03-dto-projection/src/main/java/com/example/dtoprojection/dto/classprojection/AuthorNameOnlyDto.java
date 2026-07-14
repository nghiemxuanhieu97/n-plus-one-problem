package com.example.dtoprojection.dto.classprojection;

public class AuthorNameOnlyDto {
    private final Long id;
    private final String name;

    public AuthorNameOnlyDto(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
