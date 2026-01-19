package com.mohaemukzip.mohaemukzip_be.domain.ingredient.service;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.dto.IngredientResponseDTO;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.enums.Category;

import java.util.List;

public interface IngredientQueryService {

    //키워드, 카테고리 주면 재료 목록(dto 리스트) 가져옴
    List<IngredientResponseDTO.Detail> getIngredients(String keyword, Category category);

    // 냉장고 조회
    IngredientResponseDTO.FridgeIngredientList getMyFridgeIngredients(Long memberId);

    //즐겨찾기 조회
    IngredientResponseDTO.FavoriteList getFavoriteList(Long memberId);


}
