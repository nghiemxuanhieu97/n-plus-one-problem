package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Award;

public record AwardDto(
        Long id,
        String name
) {
    public static AwardDto fromEntity(Award award) {
        return new AwardDto(
                award.getId(),
                award.getName()
        );
    }
}
