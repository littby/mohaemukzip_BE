package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.processor;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.RedisChatMessage;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatProcessorResult;

import java.util.List;

public interface ChatProcessor {
    ChatProcessorResult process(Long memberId, String userMessage, List<RedisChatMessage> history);
}
