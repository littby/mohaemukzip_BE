package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.command;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.converter.ChatConverter;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.RedisChatMessage;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.request.ChatPostRequest;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatProcessorResult;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatResponse;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.RecipeCardResponse;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity.enums.SenderType;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.processor.ChatProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatCommandServiceImpl implements ChatCommandService {

    private final ChatProcessor chatProcessor;
    private final ChatLogService chatLogService;

    @Qualifier("redisCacheTemplate")
    private final RedisTemplate<String, Object> redisTemplate;

    private static final long CHAT_TTL_MINUTES = 30;

    @Override
    public ChatResponse processMessage(Long memberId, ChatPostRequest request) {
        String redisKey = getRedisKey(memberId);

        // 1. 이전 대화 내역 조회 (최근 6개 = 3턴)
        List<RedisChatMessage> history = getRecentHistory(redisKey, 6);

        // 2. Processor에 대화 내역 전달
        ChatProcessorResult result = chatProcessor.process(memberId, request.getMessage(), history);

        // 3. 사용자 메시지 Redis 저장
        RedisChatMessage userMessage = RedisChatMessage.builder()
                .sender(SenderType.USER)
                .content(request.getMessage())
                .timestamp(LocalDateTime.now().toString())
                .build();
        saveToRedis(redisKey, userMessage);

        // 4. 봇 메시지 Redis 저장
        RedisChatMessage botMessage = RedisChatMessage.builder()
                .sender(SenderType.BOT)
                .title(result.getTitle())
                .content(result.getMessage())
                .timestamp(LocalDateTime.now().toString())
                .build();
        saveToRedis(redisKey, botMessage);

        // 5. TTL 갱신 (마지막 활동 기준 30분 연장)
        redisTemplate.expire(redisKey, CHAT_TTL_MINUTES, TimeUnit.MINUTES);

        // 6. 모니터링/분석용 대화 로그 비동기 저장 (응답 지연 없음)
        List<Long> recipeIds = result.getRecipeCards() != null
                ? result.getRecipeCards().stream().map(RecipeCardResponse::getRecipeId).collect(Collectors.toList())
                : List.of();
        chatLogService.saveChatLog(memberId, request.getMessage(),
                result.getTitle(), result.getMessage(), recipeIds);

        // 7. 최종 응답 DTO 변환
        return ChatConverter.toChatResponse(result, botMessage.getId());
    }

    private List<RedisChatMessage> getRecentHistory(String key, int count) {
        List<Object> rawList = redisTemplate.opsForList().range(key, -count, -1);
        if (rawList == null || rawList.isEmpty()) {
            return new ArrayList<>();
        }

        return rawList.stream()
                .map(obj -> (RedisChatMessage) obj)
                .collect(Collectors.toList());
    }

    private void saveToRedis(String key, RedisChatMessage message) {
        redisTemplate.opsForList().rightPush(key, message);
    }

    private String getRedisKey(Long memberId) {
        return "chat:room:" + memberId + ":messages";
    }
}
