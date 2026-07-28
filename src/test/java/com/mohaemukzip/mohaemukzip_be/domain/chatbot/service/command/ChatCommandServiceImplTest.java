package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.command;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.RedisChatMessage;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.request.ChatPostRequest;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatProcessorResult;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatResponse;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.RecipeCardResponse;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity.enums.SenderType;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.processor.ChatProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatCommandServiceImplTest {

    @Mock
    private ChatProcessor chatProcessor;

    @Mock
    private ChatLogService chatLogService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ListOperations<String, Object> listOperations;

    private ChatCommandServiceImpl chatCommandService;

    private static final Long MEMBER_ID = 1L;
    private static final String SESSION_ID = "session-abc-123";
    private static final String REDIS_KEY = "chat:session:" + SESSION_ID + ":messages";

    @BeforeEach
    void setUp() {
        chatCommandService = new ChatCommandServiceImpl(chatProcessor, chatLogService, redisTemplate);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    @Test
    @DisplayName("이전 대화 이력 없이 메시지를 처리하면 사용자/봇 메시지를 객체 그대로 Redis에 저장하고 TTL을 갱신한다")
    void processMessageSavesRawObjectsAndRefreshesTtl() {
        when(listOperations.range(eq(REDIS_KEY), anyLong(), anyLong())).thenReturn(List.of());
        ChatProcessorResult processorResult = ChatProcessorResult.builder()
                .title("추천 제목")
                .message("추천 메시지")
                .recipeCards(List.of())
                .build();
        when(chatProcessor.process(eq(MEMBER_ID), eq("냉장고 파먹기"), anyList())).thenReturn(processorResult);

        ChatResponse response = chatCommandService.processMessage(MEMBER_ID, new ChatPostRequest("냉장고 파먹기", SESSION_ID));

        assertThat(response.getTitle()).isEqualTo("추천 제목");
        assertThat(response.getMessage()).isEqualTo("추천 메시지");
        assertThat(response.getSenderType()).isEqualTo(SenderType.BOT);

        ArgumentCaptor<RedisChatMessage> messageCaptor = ArgumentCaptor.forClass(RedisChatMessage.class);
        verify(listOperations, times(2)).rightPush(eq(REDIS_KEY), messageCaptor.capture());

        List<RedisChatMessage> pushedMessages = messageCaptor.getAllValues();
        assertThat(pushedMessages.get(0).getSender()).isEqualTo(SenderType.USER);
        assertThat(pushedMessages.get(0).getContent()).isEqualTo("냉장고 파먹기");
        assertThat(pushedMessages.get(1).getSender()).isEqualTo(SenderType.BOT);
        assertThat(pushedMessages.get(1).getTitle()).isEqualTo("추천 제목");

        verify(redisTemplate).expire(REDIS_KEY, 30, TimeUnit.MINUTES);
    }

    @Test
    @DisplayName("Redis에 저장된 히스토리를 RedisChatMessage 객체로 그대로 읽어 Processor에 전달한다")
    void passesDeserializedHistoryToProcessor() {
        RedisChatMessage previousUserMessage = RedisChatMessage.builder()
                .sender(SenderType.USER)
                .content("이전 질문")
                .build();
        when(listOperations.range(eq(REDIS_KEY), anyLong(), anyLong()))
                .thenReturn(List.of(previousUserMessage));
        when(chatProcessor.process(eq(MEMBER_ID), eq("다음 질문"), anyList()))
                .thenReturn(ChatProcessorResult.builder().title("t").message("m").recipeCards(List.of()).build());

        chatCommandService.processMessage(MEMBER_ID, new ChatPostRequest("다음 질문", SESSION_ID));

        ArgumentCaptor<List<RedisChatMessage>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatProcessor).process(eq(MEMBER_ID), eq("다음 질문"), historyCaptor.capture());
        assertThat(historyCaptor.getValue()).containsExactly(previousUserMessage);
    }

    @Test
    @DisplayName("같은 memberId라도 sessionId가 다르면 서로 다른 Redis 키를 사용한다 (세션 단위 히스토리 격리)")
    void usesDifferentRedisKeyPerSessionForSameMember() {
        String otherSessionId = "session-xyz-999";
        String otherRedisKey = "chat:session:" + otherSessionId + ":messages";

        when(listOperations.range(eq(otherRedisKey), anyLong(), anyLong())).thenReturn(List.of());
        when(chatProcessor.process(eq(MEMBER_ID), eq("새 세션 질문"), anyList()))
                .thenReturn(ChatProcessorResult.builder().title("t").message("m").recipeCards(List.of()).build());

        chatCommandService.processMessage(MEMBER_ID, new ChatPostRequest("새 세션 질문", otherSessionId));

        verify(listOperations).range(eq(otherRedisKey), anyLong(), anyLong());
        verify(listOperations, never()).range(eq(REDIS_KEY), anyLong(), anyLong());
        verify(redisTemplate).expire(otherRedisKey, 30, TimeUnit.MINUTES);
    }

    @Test
    @DisplayName("응답 처리 후 모니터링/분석용 대화 로그를 memberId/sessionId/추천 레시피ID와 함께 비동기 저장 요청한다")
    void savesChatLogWithRecommendedRecipeIds() {
        when(listOperations.range(eq(REDIS_KEY), anyLong(), anyLong())).thenReturn(List.of());
        RecipeCardResponse card = RecipeCardResponse.builder().recipeId(42L).title("김치찌개").build();
        ChatProcessorResult processorResult = ChatProcessorResult.builder()
                .title("추천 제목")
                .message("추천 메시지")
                .recipeCards(List.of(card))
                .build();
        when(chatProcessor.process(eq(MEMBER_ID), eq("냉장고 파먹기"), anyList())).thenReturn(processorResult);

        chatCommandService.processMessage(MEMBER_ID, new ChatPostRequest("냉장고 파먹기", SESSION_ID));

        verify(chatLogService).saveChatLog(
                MEMBER_ID, SESSION_ID, "냉장고 파먹기", "추천 제목", "추천 메시지", List.of(42L));
    }
}
