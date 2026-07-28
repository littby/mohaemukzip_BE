package com.mohaemukzip.mohaemukzip_be.domain.chatbot.controller;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.converter.ChatLogConverter;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatLogResponse;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity.ChatLog;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.repository.ChatLogRepository;
import com.mohaemukzip.mohaemukzip_be.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/chats")
@Tag(name = "Admin Chat", description = "[관리자용] 챗봇 대화 로그 조회")
@Validated
public class AdminChatController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ChatLogRepository chatLogRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "챗봇 대화 로그 조회", description = "관리자 전용. memberId를 지정하면 해당 회원의 대화 이력만, 지정하지 않으면 전체 최신 대화 로그를 최신순으로 조회합니다.")
    public ApiResponse<ChatLogResponse.PageResponse> getChatLogs(
            @RequestParam(required = false) Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<ChatLog> chatLogPage = (memberId != null)
                ? chatLogRepository.findAllByMember_IdOrderByCreatedAtDesc(memberId, pageRequest)
                : chatLogRepository.findAllByOrderByCreatedAtDesc(pageRequest);

        return ApiResponse.onSuccess(ChatLogConverter.toPageResponse(chatLogPage));
    }
}
