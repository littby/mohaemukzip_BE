package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 챗봇 사용량(토큰/상태)을 CHATBOT_MONITOR 로거에 구조화된 JSON으로 기록하는 공용 컴포넌트.
 * GeminiService, RagChatProcessor 등에서 각자 로거를 재선언하고 문자열을 수동 포맷팅하던 중복을 제거한다.
 */
@Component
@RequiredArgsConstructor
public class ChatMonitorLogger {

    private static final Logger chatbotMonitorLog = LoggerFactory.getLogger("CHATBOT_MONITOR");

    private final ObjectMapper objectMapper;

    public void logUsage(Long memberId, int promptTokens, int completionTokens, int totalTokens, String status) {
        logUsage(memberId, promptTokens, completionTokens, totalTokens, status, null);
    }

    public void logUsage(Long memberId, int promptTokens, int completionTokens, int totalTokens,
                          String status, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "CHATBOT_USAGE");
        payload.put("memberId", memberId);
        payload.put("promptTokens", promptTokens);
        payload.put("completionTokens", completionTokens);
        payload.put("totalTokens", totalTokens);
        payload.put("status", status);
        if (reason != null) {
            payload.put("reason", reason);
        }

        try {
            chatbotMonitorLog.info(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            chatbotMonitorLog.warn("Failed to serialize chatbot usage log: {}", payload, e);
        }
    }
}
