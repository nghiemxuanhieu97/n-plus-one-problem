package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Country;

public record CountryResponse(
        Long id,
        String name,
        String location,
        String region
) {

    public static CountryResponse fromEntity(Country country) {
        if (country == null) {
            return null;
        }

        return new CountryResponse(
                country.getId(),
                country.getName(),
                country.getLocation(),
                country.getRegion()
        );
    }
}
