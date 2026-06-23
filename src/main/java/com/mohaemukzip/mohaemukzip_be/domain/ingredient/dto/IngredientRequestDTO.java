package com.mohaemukzip.mohaemukzip_be.domain.ingredient.dto;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.enums.StorageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

//ci/cd 테스트용주석
public class IngredientRequestDTO {

    //냉장고 재료 추가
    @Getter
    @Builder
    public static class AddFridge {
        @NotNull
        private Long ingredientId;
        @NotNull
        private StorageType storageType;
        private LocalDate expireDate;
        @Positive
        private Double weight;
    }

    @Getter
    @Builder
    public static class IngredientReq {
        @NotBlank(message = "재료 이름을 입력해주세요.")
        private String ingredientName;
    }

    //냉장고 재료 수정
    @Getter
    @Builder
    public static class UpdateFridge {
        @NotNull(message = "보관장소는 필수입니다.")
        private StorageType storageType;

        @NotNull(message = "소비기한은 필수입니다.")
        private LocalDate expireDate;

        @NotNull(message = "중량은 필수입니다.")
        @Positive(message = "중량은 0보다 커야 합니다.")
        private Double weight;
    }

}
