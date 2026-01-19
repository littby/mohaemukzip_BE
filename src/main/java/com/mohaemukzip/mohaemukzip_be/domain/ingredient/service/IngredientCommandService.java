package com.mohaemukzip.mohaemukzip_be.domain.ingredient.service;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.dto.IngredientRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.dto.IngredientResponseDTO;

public interface IngredientCommandService {

    //냉장고에 재료 추가 기능
    IngredientResponseDTO.AddFridgeResult addFridgeIngredient(Long memberId, IngredientRequestDTO.AddFridge request);

    // 냉장고 재료 삭제
    IngredientResponseDTO.DeleteFridgeIngredient  deleteIngredient(Long memberIngredientId, Long memberId);

    //재료 즐겨찾기
    IngredientResponseDTO.AddFavorite addFavorite(Long memberId, Long ingredientId);

}
