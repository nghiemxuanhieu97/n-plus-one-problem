package com.example.dtoprojection.dto.classprojection;

public class AuthorBookAwardCountDto {
    private final Long id;
    private final String name;
    private final CountrySummaryDto country;
    private final Long bookCount;
    private final Long awardCount;

    public AuthorBookAwardCountDto(Long id, String name, Long countryId, String countryName, String countryRegion,
                                   Long bookCount, Long awardCount) {
        this.id = id;
        this.name = name;
        this.country = countryId == null ? null : new CountrySummaryDto(countryId, countryName, countryRegion);
        this.bookCount = bookCount;
        this.awardCount = awardCount;
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

    public Long getBookCount() {
        return bookCount;
    }

    public Long getAwardCount() {
        return awardCount;
    }
}
