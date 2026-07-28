package com.mohaemukzip.mohaemukzip_be.domain.recipe.controller;

import com.mohaemukzip.mohaemukzip_be.domain.recipe.dto.RecipeDetailResponseDTO;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.dto.RecipeRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.dto.RecipeResponseDTO;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.service.command.RecipeCommandService;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.service.query.RecipeQueryService;
import com.mohaemukzip.mohaemukzip_be.global.response.ApiResponse;
import com.mohaemukzip.mohaemukzip_be.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/recipes")
@Tag(name = "Recipe" , description = "레시피 관련 API")
@Validated
public class RecipeController {

    private final RecipeQueryService recipeQueryService;
    private final RecipeCommandService recipeCommandService;


    @GetMapping("/{recipeId}")
    @Operation(summary = "세부 레시피 조회 API", description = "특정 videoId에 속하는 레시피에 관한 정보를 조회합니다.")
    public ApiResponse<RecipeDetailResponseDTO> getRecipeDetail(
            @PathVariable Long recipeId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.onSuccess(
                recipeQueryService.getRecipeDetail(recipeId, userDetails.getMember().getId())
        );
    }


    @PostMapping("/{recipeId}/complete")
    @Operation(summary = "요리 완료 API", description = "특정 RecipeId에 속하는 레시피를 CookingRecord에 저장하고, 해당 레시피의 난이도를 갱신합니다.")
    public ApiResponse<RecipeResponseDTO.CookingRecordCreateResponseDTO> createCookingRecord(
            @PathVariable Long recipeId,
            @RequestParam int rating,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.onSuccess(
                recipeCommandService.createCookingRecord(
                        userDetails.getMember().getId(),
                        recipeId,
                        rating
                )
        );
    }

    @PostMapping("/{recipeId}/bookmark")
    @Operation(summary = "북마크 토글 API", description = "특정 레시피를 북마크에 저장하거나, 이미 저장된 경우 삭제합니다.")
    public ApiResponse<RecipeResponseDTO.BookmarkToggleResult> toggleBookmark(
            @PathVariable Long recipeId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.onSuccess(
                recipeCommandService.toggleBookmark(userDetails.getMember().getId(), recipeId)
        );
    }

}
