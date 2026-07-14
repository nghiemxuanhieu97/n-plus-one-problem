package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Award;

public record AwardResponse(
        Long id,
        String name,
        Long authorId
) {

    public static AwardResponse fromEntity(Award award) {
        return new AwardResponse(
                award.getId(),
                award.getName(),
                award.getAuthor() == null ? null : award.getAuthor().getId()
        );
    }
}
