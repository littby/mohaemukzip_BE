package com.mohaemukzip.mohaemukzip_be.domain.ingredient.controller;


import com.mohaemukzip.mohaemukzip_be.domain.ingredient.dto.IngredientRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.dto.IngredientResponseDTO;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.service.IngredientCommandService;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.service.IngredientQueryService;
import com.mohaemukzip.mohaemukzip_be.global.response.ApiResponse;
import com.mohaemukzip.mohaemukzip_be.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/me")
@Tag(name = "My Fridge", description = "냉장고 관련 API")
@Validated
public class FridgeController {


    private final IngredientCommandService ingredientCommandService;
    private final IngredientQueryService ingredientQueryService;

    @Operation(summary = "냉장고 재료 추가")
    @PostMapping("/ingredients")
    public ApiResponse<IngredientResponseDTO.AddFridgeResult> addFridgeIngredient(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestBody @Valid IngredientRequestDTO.AddFridge request
    ) {
        Long memberId = customUserDetails.getMember().getId();

        IngredientResponseDTO.AddFridgeResult result = ingredientCommandService.addFridgeIngredient(memberId, request);

        return ApiResponse.onSuccess(result);
    }

    @Operation(summary = "냉장고 재료 조회")
    @GetMapping("/ingredients")
    public ApiResponse<IngredientResponseDTO.FridgeIngredientList> getMyFridgeIngredients(
            @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        Long memberId = customUserDetails.getMember().getId();

        IngredientResponseDTO.FridgeIngredientList result = ingredientQueryService.getMyFridgeIngredients(memberId);

        return ApiResponse.onSuccess(result);
    }

    @Operation(summary = "냉장고 재료 삭제")
    @DeleteMapping("/ingredients/{ingredientId}")
    public ApiResponse<IngredientResponseDTO.DeleteFridgeIngredient> deleteFridgeIngredient(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long ingredientId
    ) {
        Long memberId = customUserDetails.getMember().getId();

        IngredientResponseDTO.DeleteFridgeIngredient result =
                ingredientCommandService.deleteIngredient(ingredientId, memberId);

        return ApiResponse.onSuccess(result);
    }
}
