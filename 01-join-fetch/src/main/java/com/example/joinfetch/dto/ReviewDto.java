package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Award;
import com.example.joinfetch.entity.Review;

public record ReviewDto(
        Long id,
        String name
) {
    public static ReviewDto fromEntity(Review award) {
        return new ReviewDto(
                award.getId(),
                award.getName()
        );
    }
}
