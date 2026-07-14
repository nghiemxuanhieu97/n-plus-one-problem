package com.example.dtoprojection.dto.recordprojection;

public record AuthorBookAwardCountRecord(
        Long id,
        String name,
        CountrySummaryRecord country,
        Long bookCount,
        Long awardCount
) {
    public AuthorBookAwardCountRecord(Long id, String name, Long countryId, String countryName, String countryRegion,
                                      Long bookCount, Long awardCount) {
        this(id,
                name,
                countryId == null ? null : new CountrySummaryRecord(countryId, countryName, countryRegion),
                bookCount,
                awardCount);
    }
}
