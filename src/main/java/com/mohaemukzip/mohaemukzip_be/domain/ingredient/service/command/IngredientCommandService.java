package com.mohaemukzip.mohaemukzip_be.domain.ingredient.service.command;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.dto.IngredientRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.dto.IngredientResponseDTO;

public interface IngredientCommandService {

    //냉장고에 재료 등록
    IngredientResponseDTO.AddFridgeResult addFridgeIngredient(Long memberId, IngredientRequestDTO.AddFridge request);

    // 냉장고 재료 삭제
    IngredientResponseDTO.DeleteFridgeIngredient  deleteIngredient(Long memberIngredientId, Long memberId);

    // 즐겨찾기 토글 (등록/삭제)
    IngredientResponseDTO.ToggleFavorite toggleFavorite(Long memberId, Long ingredientId);

    //재료 요청하기
    void createIngredientRequest(Long memberId, IngredientRequestDTO.IngredientReq request);

    // 냉장고 재료 수정
    IngredientResponseDTO.UpdateFridgeResult updateFridgeIngredient(
            Long memberId,
            Long memberIngredientId,
            IngredientRequestDTO.UpdateFridge request
    );

}
