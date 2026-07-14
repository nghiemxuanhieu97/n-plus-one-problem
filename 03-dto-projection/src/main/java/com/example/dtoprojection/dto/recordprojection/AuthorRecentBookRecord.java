package com.example.dtoprojection.dto.recordprojection;

public record AuthorRecentBookRecord(
        Long id,
        String name,
        CountrySummaryRecord country,
        String recentBookName
) {
    public AuthorRecentBookRecord(Long id, String name, Long countryId, String countryName, String countryRegion,
                                  String recentBookName) {
        this(id,
                name,
                countryId == null ? null : new CountrySummaryRecord(countryId, countryName, countryRegion),
                recentBookName);
    }
}
