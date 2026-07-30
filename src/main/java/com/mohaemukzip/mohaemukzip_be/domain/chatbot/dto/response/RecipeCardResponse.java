package com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 챗봇이 반환하는 레시피 카드 DTO.
 * 유튜브 피드처럼 썸네일 미리보기 + 영상 길이를 보여주고, 클릭하면 레시피 상세 페이지로 이동하는 용도로 쓰인다.
 * recipe_id는 Gemini가 후보 목록 중에서 선택하지만, title/imageUrl/videoTime은 Gemini의 텍스트를 신뢰하지 않고
 * 서버가 DB의 실제 Recipe 데이터로 직접 채운다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "추천 레시피 카드 정보")
public class RecipeCardResponse {

    @JsonProperty("recipe_id")
    @Schema(description = "레시피 DB ID (클릭 시 상세 페이지 이동에 사용)", example = "2")
    private Long recipeId;

    @Schema(description = "레시피 제목", example = "1등🥇 식당처럼 맛있게 [돼지고기 김치찌개] 끓이는법! 한 숟갈만 드셔도 극찬이~ 실패는 없다!")
    private String title;

    @JsonProperty("image_url")
    @Schema(description = "영상 썸네일 이미지 URL", example = "https://img.youtube.com/vi/abcdefg/hqdefault.jpg")
    private String imageUrl;

    @JsonProperty("video_time")
    @Schema(description = "영상 길이 (mm:ss)", example = "10:54")
    private String videoTime;
}
