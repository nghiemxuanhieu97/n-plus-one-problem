package com.example.dtoprojection.dto.classprojection;

public class AuthorDisplayNameDto {
    private final Long id;
    private final String name;
    private final String displayName;

    public AuthorDisplayNameDto(Long id, String name, String displayName) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }
}
