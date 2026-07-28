package com.mohaemukzip.mohaemukzip_be.domain.recipe.controller;

import com.mohaemukzip.mohaemukzip_be.domain.recipe.service.command.RecipeCommandService;
import com.mohaemukzip.mohaemukzip_be.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.dto.RecipeRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.dto.RecipeResponseDTO;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.service.command.RecipeAdminFacade;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/recipes")
@Tag(name = "Admin Recipe", description = "관리자용 레시피 API")
public class AdminRecipeController {

    private final RecipeAdminFacade recipeAdminFacade;
    private final com.mohaemukzip.mohaemukzip_be.domain.recipe.service.command.AdminRecipeService adminRecipeService;

    public record BulkRecipeRequest(
            @NotNull(message = "dishId는 필수입니다.") Long dishId,
            @NotEmpty(message = "비디오 ID 목록은 비어있을 수 없습니다.") List<String> videoIds
    ) {}

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "레시피 대량 등록", description = "관리자 전용. 다수의 videoId를 입력받아 레시피를 순차 저장합니다.")
    public ApiResponse<String> createRecipesInBulk(@Valid @RequestBody BulkRecipeRequest request) {
        log.info("[관리자] 대량 레시피 비동기 등록 요청 - 요리ID: {}, 건수: {}", request.dishId(), request.videoIds().size());

        // 비동기 서비스 호출 (레시피 크롤링 -> 요약)
        adminRecipeService.processBulkRecipesAsync(request.dishId(), request.videoIds());

        return ApiResponse.onSuccess("레시피 대량 등록 작업이 백그라운드에서 시작되었습니다.");
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "레시피 저장 API (단건)", description = "관리자 전용. 특정 video_id를 가진 유튜브 영상에 관한 레시피를 저장합니다.")
    public ApiResponse<RecipeResponseDTO.RecipeCreateResponse> createRecipe(
            @Valid @RequestBody RecipeRequestDTO.RecipeCreateRequest request
    ) {
        Long recipeId = recipeAdminFacade.saveRecipeByVideoId(request.getDishId(), request.getVideoId());
        return ApiResponse.onSuccess(new RecipeResponseDTO.RecipeCreateResponse(recipeId));
    }

    @PostMapping("/{recipeId}/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "레시피 요약 생성 API", description = "관리자 전용. 특정 레시피의 유튜브 자막을 추출하고, Gemini를 이용해 요약 스텝을 생성합니다.")
    public ApiResponse<RecipeResponseDTO.SummaryCreateResult> createSummary(
            @PathVariable Long recipeId
    ) {
        var result = recipeAdminFacade.createSummary(recipeId);
        return ApiResponse.onSuccess(result);
    }
}
