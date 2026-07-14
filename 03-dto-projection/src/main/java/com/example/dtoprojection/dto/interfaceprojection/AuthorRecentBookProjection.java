package com.example.dtoprojection.dto.interfaceprojection;

public interface AuthorRecentBookProjection {
    Long getId();
    String getName();
    CountrySummaryProjection getCountry();
    String getRecentBookName();
}
