package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Author;
import com.example.joinfetch.entity.Award;
import com.example.joinfetch.entity.Review;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AuthorReviewAwardsDto(
        Long id,
        String name,
        List<AwardDto> awards,
        List<ReviewDto> reviews
) {
    public static AuthorReviewAwardsDto fromEntity(Author author) {
        return new AuthorReviewAwardsDto(
                author.getId(),
                author.getName(),
                uniqueAwards(author.getAwards()),
                uniqueReview(author.getReviews())
        );
    }

    private static List<AwardDto> uniqueAwards(Collection<Award> awards) {
        Map<Long, AwardDto> result = new LinkedHashMap<>();
        for (Award award : awards) {
            result.putIfAbsent(award.getId(), AwardDto.fromEntity(award));
        }
        return List.copyOf(result.values());
    }
    private static List<ReviewDto> uniqueReview(Collection<Review> reviews) {
        Map<Long, ReviewDto> result = new LinkedHashMap<>();
        for (Review review : reviews) {
            result.putIfAbsent(review.getId(), ReviewDto.fromEntity(review));
        }
        return List.copyOf(result.values());
    }
}
