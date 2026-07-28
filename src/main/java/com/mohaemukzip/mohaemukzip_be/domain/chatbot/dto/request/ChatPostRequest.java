package com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "챗봇 메시지 전송 요청 DTO")
public class ChatPostRequest {

    @NotBlank(message = "메시지를 입력해주세요.")
    @Schema(description = "사용자 메시지 내용", example = "냉장고에 콩나물이 있는데 뭐 해먹을까?")
    private String message;

    @NotBlank(message = "세션 ID가 필요합니다.")
    @Schema(description = "앱 실행마다 프론트에서 새로 발급하는 대화 세션 ID. 대화 히스토리는 이 값 기준으로 저장/조회되며, 앱을 재실행해 새 sessionId를 보내면 이전 대화 맥락은 이어지지 않습니다.",
            example = "3f2a9c1e-4b7d-4e21-9c3a-2d6e7f8a1b23")
    private String sessionId;
}
