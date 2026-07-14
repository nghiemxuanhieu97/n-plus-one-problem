package com.example.dtoprojection.dto.classprojection;

public class AuthorRecentBookDto {
    private final Long id;
    private final String name;
    private final CountrySummaryDto country;
    private final String recentBookName;

    public AuthorRecentBookDto(Long id, String name, Long countryId, String countryName, String countryRegion,
                               String recentBookName) {
        this.id = id;
        this.name = name;
        this.country = countryId == null ? null : new CountrySummaryDto(countryId, countryName, countryRegion);
        this.recentBookName = recentBookName;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CountrySummaryDto getCountry() {
        return country;
    }

    public String getRecentBookName() {
        return recentBookName;
    }
}
