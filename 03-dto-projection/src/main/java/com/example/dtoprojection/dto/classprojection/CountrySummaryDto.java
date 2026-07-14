package com.example.dtoprojection.dto.classprojection;

public class CountrySummaryDto {
    private final Long id;
    private final String name;
    private final String region;

    public CountrySummaryDto(Long id, String name, String region) {
        this.id = id;
        this.name = name;
        this.region = region;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }
}
