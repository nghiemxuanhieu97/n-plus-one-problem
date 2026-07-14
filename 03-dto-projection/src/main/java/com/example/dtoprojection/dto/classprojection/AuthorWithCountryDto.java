package com.example.dtoprojection.dto.classprojection;

public class AuthorWithCountryDto {
    private final Long id;
    private final String name;
    private final CountrySummaryDto country;

    public AuthorWithCountryDto(Long id, String name, Long countryId, String countryName, String countryRegion) {
        this.id = id;
        this.name = name;
        this.country = countryId == null ? null : new CountrySummaryDto(countryId, countryName, countryRegion);
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
}
