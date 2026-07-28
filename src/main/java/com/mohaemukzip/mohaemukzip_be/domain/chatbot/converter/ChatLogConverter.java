package com.mohaemukzip.mohaemukzip_be.domain.chatbot.converter;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatLogResponse;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity.ChatLog;
import org.springframework.data.domain.Page;

public class ChatLogConverter {

    public static ChatLogResponse.Detail toDetail(ChatLog chatLog) {
        return ChatLogResponse.Detail.builder()
                .id(chatLog.getId())
                .memberId(chatLog.getMember().getId())
                .sessionId(chatLog.getSessionId())
                .userMessage(chatLog.getUserMessage())
                .botTitle(chatLog.getBotTitle())
                .botMessage(chatLog.getBotMessage())
                .recommendedRecipeIds(chatLog.getRecommendedRecipeIds())
                .createdAt(chatLog.getCreatedAt())
                .build();
    }

    public static ChatLogResponse.PageResponse toPageResponse(Page<ChatLog> chatLogPage) {
        return ChatLogResponse.PageResponse.from(chatLogPage.map(ChatLogConverter::toDetail));
    }
}
