package com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity.enums.SenderType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "챗봇 응답 결과 DTO")
public class ChatResponse {
    
    @Schema(description = "메시지 ID", example = "bf41f47d-4f2c-4ee8-a4ff-57136db17360")
    private String id;

    @Schema(description = "발신자 타입 (USER/BOT)", example = "BOT")
    private SenderType senderType;

    @Schema(description = "챗봇 응답 제목", example = "맞춤 레시피 추천")
    private String title;

    @Schema(description = "챗봇 응답 메시지", example = "회원님의 상황에 맞는 레시피를 찾아봤어요! 아래 카드를 확인해 보세요 🍳")
    private String message;

    @Schema(description = "메시지 생성 시간 (ISO 8601)", example = "2026-05-03T15:03:18.80987")
    private LocalDateTime createdAt;

    @Schema(description = "화면 표시용 시간 포맷", example = "오후 3:03")
    private String formattedTime;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "RAG 추천 레시피 카드 목록 (추천 이유 포함)")
    private List<RecipeCardResponse> recipeCards;
}
