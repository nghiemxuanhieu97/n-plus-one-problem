package com.example.dtoprojection.dto.recordprojection;

public record AuthorWithCountryRecord(Long id, String name, CountrySummaryRecord country) {
    public AuthorWithCountryRecord(Long id, String name, Long countryId, String countryName, String countryRegion) {
        this(id, name, countryId == null ? null : new CountrySummaryRecord(countryId, countryName, countryRegion));
    }
}
