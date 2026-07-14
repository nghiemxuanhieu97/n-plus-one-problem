package com.example.dtoprojection.dto.interfaceprojection;

public interface AuthorBookAwardCountProjection {
    Long getId();
    String getName();
    CountrySummaryProjection getCountry();
    Long getBookCount();
    Long getAwardCount();
}
