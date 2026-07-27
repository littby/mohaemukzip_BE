package com.mohaemukzip.mohaemukzip_be.domain.chatbot.converter;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatProcessorResult;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatResponse;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity.enums.SenderType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ChatConverter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREA);

    public static ChatResponse toChatResponse(ChatProcessorResult result, String id) {
        LocalDateTime now = LocalDateTime.now();
        return ChatResponse.builder()
                .id(id)
                .senderType(SenderType.BOT)
                .title(result.getTitle())
                .message(result.getMessage())
                .createdAt(now)
                .formattedTime(formatTime(now))
                .recipeCards(result.getRecipeCards())
                .build();
    }

    private static String formatTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(TIME_FORMATTER) : null;
    }
}
