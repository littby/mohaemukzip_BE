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
}
