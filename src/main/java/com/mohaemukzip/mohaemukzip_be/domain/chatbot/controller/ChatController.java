package com.mohaemukzip.mohaemukzip_be.domain.chatbot.controller;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.request.ChatPostRequest;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatResponse;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.command.ChatCommandService;
import com.mohaemukzip.mohaemukzip_be.global.response.ApiResponse;
import com.mohaemukzip.mohaemukzip_be.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chats")
@Tag(name = "Chatbot", description = "챗봇 대화 및 레시피 추천 API")
@Validated
public class ChatController {

    private final ChatCommandService chatCommandService;

    /**
     * 챗봇 메시지 전송 및 응답
     * [POST] /chats
     *
     * @param userDetails 인증된 사용자 정보 (SecurityContext)
     * @param request 사용자 메시지
     * @return 봇의 응답 메시지 (레시피 추천 포함)
     */
    @Operation(summary = "챗봇 대화 요청", description = "사용자의 메시지를 분석하여 답변을 생성하고, 필요 시 맞춤형 레시피를 추천합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> processMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChatPostRequest request) {

        Long memberId = userDetails.getMember().getId();

        ChatResponse response = chatCommandService.processMessage(memberId, request);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
