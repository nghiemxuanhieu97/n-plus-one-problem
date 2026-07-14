package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Country;

public record CountryDto(
        Long id,
        String name,
        String location,
        String region
) {
    public static CountryDto fromEntity(Country country) {
        if (country == null) {
            return null;
        }

        return new CountryDto(
                country.getId(),
                country.getName(),
                country.getLocation(),
                country.getRegion()
        );
    }
}
