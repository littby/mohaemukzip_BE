package com.mohaemukzip.mohaemukzip_be.domain.ingredient.dto;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.*;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.enums.IngredientStatus;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public class IngredientResponseDTO {

    //1-1. 재료 조회 api
    @Getter
    @Builder
    public static class IngredientPageResponse {
        private List<Detail> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;

        public static IngredientPageResponse from(Page<Detail> page) {
            return IngredientPageResponse.builder()
                    .content(page.getContent())
                    .page(page.getNumber())
                    .size(page.getSize())
                    .totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .first(page.isFirst())
                    .last(page.isLast())
                    .build();
        }
    }

    //1-2. 재료 조회 api
    @Getter
    @Builder
    public static class Detail {
        private Long ingredientId;
        private String name;
        private String category;
        private String unit;
        private Double weight;
        private Boolean isFavorite;

        // 엔티티 -> DTO 변환 메서드
        public static Detail from(Ingredient ingredient, boolean isFavorite) {
            return Detail.builder()
                    .ingredientId(ingredient.getId())
                    .name(ingredient.getName())
                    .category(ingredient.getCategory() != null ? ingredient.getCategory().getLabel() : null)
                    .unit(ingredient.getUnit() != null ? ingredient.getUnit().getLabel() : null)
                    .weight(ingredient.getWeight())
                    .isFavorite(isFavorite)
                    .build();
        }
    }

    //2. 냉장고에 추가 api
    @Getter
    @Builder
    public static class AddFridgeResult {
        private Long memberIngredientId;
    }

    //3-1. 개별 냉장고 재료 조회
    @Getter
    @Builder
    public static class FridgeIngredient {
        private Long memberIngredientId;
        private String name;
        private String storageType;
        private LocalDate expiryDate;
        private Double weight;
        private String unit;
        private String dDay;
        private String statusColor;

        // D-day 계산 & 색상 표시
        public static FridgeIngredient from(MemberIngredient entity) {
            Ingredient ingredient = entity.getIngredient();

            String dDayString = null;
            String color = null;

            // (1) D-day 계산
            if (entity.getExpireDate() != null) {
                LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
                LocalDate expireDate = entity.getExpireDate();

                long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(today, expireDate);

                if (daysBetween > 0) {
                    dDayString = "D-" + daysBetween;
                } else if (daysBetween < 0) {
                    dDayString = "D+" + Math.abs(daysBetween);
                } else {
                    dDayString = "D-Day";
                }

                // (2) 색상 정하기
                if (daysBetween >= 4) {
                    color = IngredientStatus.NORMAL.getColor();   // GREEN
                } else if (daysBetween >= 0) {
                    color = IngredientStatus.IMMINENT.getColor(); // ORANGE
                } else {
                    color = IngredientStatus.EXPIRED.getColor();  // RED
                }
            }


            return FridgeIngredient.builder()
                    .memberIngredientId(entity.getId())
                    .name(ingredient != null ? ingredient.getName() : null)
                    .storageType(entity.getStorageType() != null ? entity.getStorageType().toString() : null)
                    .expiryDate(entity.getExpireDate())
                    .weight(entity.getWeight())
                    .unit(ingredient != null && ingredient.getUnit() != null ? ingredient.getUnit().getLabel() : null)
                    .dDay(dDayString)
                    .statusColor(color)
                    .build();
        }
    }

    //3-2. 냉장고 재료 리스트
    @Getter
    @Builder
    public static class FridgeIngredientList {
        private List<FridgeIngredient> fridgeList;
    }

    //4. 냉장고 재료 삭제

    @Builder
    public record DeleteFridgeIngredient(Long memberIngredientId) {
    }


    // 6. 즐겨찾기 토글 응답
    @Builder
    public record ToggleFavorite(
            Long memberFavoriteId,
            Long ingredientId,
            Boolean isFavorite  // true: 등록됨, false: 삭제됨
    ) {
    }


    // 8-1. 최근 재료 검색 조회
    @Builder
    public record RecentSearch(
            String keyword
    ) {
        public static RecentSearch from(String keyword) {
            return new RecentSearch(keyword);
        }
    }

    //8-2. 최근 재료 검색 리스트 조회
    @Builder
    public record RecentSearchList(
            List<RecentSearch> recentList
    ) {
        public static RecentSearchList from(List<String> keywords) {
            List<RecentSearch> dto = keywords.stream()
                    .map(RecentSearch::from)
                    .toList();

            return new RecentSearchList(dto);
        }
    }

    @Builder
    //9. 관리자용 재료 요청 목록 조회
    public record AdminRequestList(
            Long requestId,
            Long requestMemberId,
            String ingredientName,
            LocalDate createdAt
    ) {
        public static AdminRequestList from(IngredientRequest entity) {
            return AdminRequestList.builder()
                    .requestId(entity.getId())
                    .requestMemberId(entity.getMember() != null ? entity.getMember().getId() : null)
                    .ingredientName(entity.getIngredientName())
                    .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toLocalDate() : null)
                    .build();
        }
    }

    //10. 재료 추천 소비기한 받기
    @Builder
    public record RecommendedExpirationDate(
            Long ingredientId,
            String ingredientName,
            LocalDate recommendedDate
    ) {
        public static RecommendedExpirationDate of(Ingredient ingredient, LocalDate recommendedDate) {
            return RecommendedExpirationDate.builder()
                    .ingredientId(ingredient.getId())
                    .ingredientName(ingredient.getName())
                    .recommendedDate(recommendedDate)
                    .build();
        }
    }

    // 11. 냉장고 재료 수정
    @Builder
    public record UpdateFridgeResult(
            Long memberIngredientId
    ) {
    }
}
