package com.mohaemukzip.mohaemukzip_be.domain.ingredient.dto;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.Ingredient;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.MemberFavorite;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.MemberIngredient;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.enums.IngredientStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public class IngredientResponseDTO {

    //1. 재료 조회 api
    @Getter
    @Builder
    public static class Detail {
        private Long ingredientId;
        private String name;
        private String category;
        private String unit;

        // 엔티티 -> DTO 변환 메서드
        public static Detail from(Ingredient ingredient) {
            return Detail.builder()
                    .ingredientId(ingredient.getId())
                    .name(ingredient.getName())
                    .category(ingredient.getCategory() != null ? ingredient.getCategory().getLabel() : null)
                    .unit(ingredient.getUnit() != null ? ingredient.getUnit().getLabel() : null)
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
  
    //5. 재료 즐겨찾기 등록
    @Builder
    public record AddFavorite(Long memberFavoriteId, Long ingredientId) {
    }

    // 6-1. 즐겨찾기 재료 상세 조회
    @Builder
    public record FavoriteDetail(
            Long memberFavoriteId,
            Long ingredientId,
            String name,
            Double weight,
            String unit
    ) {

        public static FavoriteDetail from(MemberFavorite favorite) {

            Ingredient ingredient = favorite.getIngredient();

            if (ingredient == null) {
                return FavoriteDetail.builder()
                        .memberFavoriteId(favorite.getId())
                        .build();
            }
            return FavoriteDetail.builder()
                    .memberFavoriteId(favorite.getId())
                    .ingredientId(ingredient.getId())
                    .name(ingredient.getName())
                    .weight(ingredient.getWeight())
                    .unit(ingredient.getUnit() != null ? ingredient.getUnit().getLabel() : null)
                    .build();
        }
    }

    // 6-2. 즐겨찾기 재료 리스트
    @Builder
    public record FavoriteList(
            List<FavoriteDetail> favoriteList
    ) {}

}
