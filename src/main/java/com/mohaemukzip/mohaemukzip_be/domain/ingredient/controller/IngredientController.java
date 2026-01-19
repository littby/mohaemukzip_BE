package com.mohaemukzip.mohaemukzip_be.domain.ingredient.controller;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.dto.IngredientResponseDTO;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.enums.Category;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.service.IngredientCommandService;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.service.IngredientQueryService;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.response.ApiResponse;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import com.mohaemukzip.mohaemukzip_be.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ingredients")
@Tag(name = "Ingredient", description = "재료 검색 관련 API")
@Validated
public class IngredientController {

    private final IngredientQueryService ingredientQueryService;
    private final IngredientCommandService ingredientCommandService;

    @Operation(summary = "재료 검색")
    @GetMapping
    public ApiResponse<List<IngredientResponseDTO.Detail>> searchIngredients(
      @RequestParam(name = "query", required = false) String query,
      @RequestParam(name = "category", required = false) Category category
    ) {

        List<IngredientResponseDTO.Detail> response = ingredientQueryService.getIngredients(query,category);

        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "즐겨찾기 재료 목록 조회")
    @GetMapping("/favorites")
    public ApiResponse<IngredientResponseDTO.FavoriteList> getFavoriteList(
            @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        if (customUserDetails == null) {
            throw new BusinessException(ErrorStatus.TOKEN_MISSING);
        }
        Long memberId = customUserDetails.getMember().getId();

        IngredientResponseDTO.FavoriteList response = ingredientQueryService.getFavoriteList(memberId);

        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "즐겨찾기 재료 등록")
    @PostMapping("/{ingredientId}/favorites")
    public ApiResponse<IngredientResponseDTO.AddFavorite> addFavorite(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long ingredientId
    ) {

        if (customUserDetails == null) {
            throw new BusinessException(ErrorStatus.TOKEN_MISSING);
        }
        Long memberId = customUserDetails.getMember().getId();

        IngredientResponseDTO.AddFavorite result =
                ingredientCommandService.addFavorite(memberId, ingredientId);

        return ApiResponse.onSuccess(result);
    }


}


