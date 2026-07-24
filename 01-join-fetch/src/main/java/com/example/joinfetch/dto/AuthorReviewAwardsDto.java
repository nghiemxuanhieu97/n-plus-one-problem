package com.example.joinfetch.dto;

import com.example.joinfetch.entity.Author;
import com.example.joinfetch.entity.Review;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AuthorReviewAwardsDto(
        Long id,
        String name,
        Long noAwards,
        List<ReviewDto> reviews
) {
    public static AuthorReviewAwardsDto fromEntity(Author author) {
        return new AuthorReviewAwardsDto(
                author.getId(),
                author.getName(),
                (long) author.getAwards().size(),
                uniqueReview(author.getReviews())
        );
    }

    private static List<ReviewDto> uniqueReview(Collection<Review> reviews) {
        Map<Long, ReviewDto> result = new LinkedHashMap<>();
        for (Review review : reviews) {
            result.putIfAbsent(review.getId(), ReviewDto.fromEntity(review));
        }
        return List.copyOf(result.values());
    }
}
