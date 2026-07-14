package com.example.dtoprojection.dto.interfaceprojection;

public interface AuthorWithCountryProjection {
    Long getId();
    String getName();
    CountrySummaryProjection getCountry();
}
