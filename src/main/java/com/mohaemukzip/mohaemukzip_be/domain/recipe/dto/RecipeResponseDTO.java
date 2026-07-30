package com.mohaemukzip.mohaemukzip_be.domain.recipe.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

public class RecipeResponseDTO {

    @SuperBuilder
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class RecipeCommonDTO {
        private Long id;
        private String title;
        private String channelName;
        private Long viewCount;
        private String videoId;
        private String channelId;
        private String videoDuration;
        private Integer cookingTimeMinutes;
        private Double difficulty;
        private Boolean isBookmarked;
    }

    @SuperBuilder
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class RecipePreviewDTO extends RecipeCommonDTO {
        // 필드가 없으므로 @AllArgsConstructor 제거 (NoArgsConstructor와 충돌 방지)
    }

    @Builder
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class RecipePreviewListDTO {
        private List<RecipePreviewDTO> recipeList;
        private Integer listSize;
        private Integer totalPage;
        private Long totalElements;
        private Boolean isFirst;
        private Boolean isLast;
    }


    public record RecipeCreateResponse(Long recipeId) {}

    @Builder
    public record SummaryCreateResult(
            boolean summaryExists,
            int stepCount
    ) {}

    @Builder
    public record MissingSummaryItem(
            Long recipeId,
            String title,
            Long dishId,
            String videoId
    ) {}

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CookingRecordCreateResponseDTO {

        private Long cookingRecordId;
        private Long recipeId;
        private Integer rating;      // 사용자가 준 별점 (1~5)
        private Double recipeLevel;  // 갱신된 레시피 난이도
        private Integer ratingCount; // 갱신된 평가 수
        private Integer rewardScore; // 이번 요청으로 오른 점수, 없으면 0
        private Boolean leveledUp; // 레벨업 유무
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookmarkToggleResult {
        private Boolean isBookmarked;
        private String message;
    }

    public record SummaryCreateResponse(
            boolean summaryExists,
            int stepCount
    ) {}
}
