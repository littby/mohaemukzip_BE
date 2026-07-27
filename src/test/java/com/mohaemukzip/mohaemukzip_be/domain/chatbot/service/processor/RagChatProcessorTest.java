package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatProcessorResult;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.external.GeminiService;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.helper.ChatContextHelper;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.helper.ChatMonitorLogger;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.MemberIngredientRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.dto.RecipeSearchResponseDto;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.CookingRecordRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.service.query.RecipeSearchService;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagChatProcessorTest {

    @Mock
    private RecipeSearchService recipeSearchService;
    @Mock
    private MemberIngredientRepository memberIngredientRepository;
    @Mock
    private CookingRecordRepository cookingRecordRepository;
    @Mock
    private GeminiService geminiService;
    @Mock
    private ChatContextHelper chatContextHelper;
    @Mock
    private ChatMonitorLogger chatMonitorLogger;

    private RagChatProcessor ragChatProcessor;

    private static final Long MEMBER_ID = 1L;

    @BeforeEach
    void setUp() {
        ragChatProcessor = new RagChatProcessor(
                recipeSearchService,
                memberIngredientRepository,
                cookingRecordRepository,
                geminiService,
                chatContextHelper,
                chatMonitorLogger,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("벡터 검색 결과가 없으면 Gemini를 호출하지 않고 Fast-fail 응답을 반환한다")
    void fastFailWhenNoSearchResults() {
        when(recipeSearchService.searchTop3ByVector(anyString())).thenReturn(List.of());

        ChatProcessorResult result = ragChatProcessor.process(MEMBER_ID, "비트코인 시세 알려줘", List.of());

        assertThat(result.getRecipeCards()).isEmpty();
        assertThat(result.getMessage()).contains("모해먹집");
        verify(geminiService, never()).generateChatResponse(anyLong(), anyString(), anyList());
        verify(chatMonitorLogger).logUsage(MEMBER_ID, 0, 0, 0, "FAST_FAIL");
    }

    @Test
    @DisplayName("Gemini가 올바른 JSON을 반환하면 그대로 파싱하여 응답한다")
    void parsesValidJsonResponse() {
        givenSearchResultsAndEmptyContext();
        String validJson = "{"
                + "\"main_title\": \"비 오는 날 추천\","
                + "\"main_message\": \"얼큰한 김치찌개 어때요?\","
                + "\"recipe_cards\": [{"
                + "  \"recipe_id\": 10,"
                + "  \"title\": \"김치찌개\","
                + "  \"recommend_reason\": \"냉장고 재료와 잘 맞아요\","
                + "  \"ingredients_match_rate\": 90"
                + "}]"
                + "}";
        when(geminiService.generateChatResponse(eq(MEMBER_ID), anyString(), anyList())).thenReturn(validJson);

        ChatProcessorResult result = ragChatProcessor.process(MEMBER_ID, "비 오는 날 뭐 먹지", List.of());

        assertThat(result.getTitle()).isEqualTo("비 오는 날 추천");
        assertThat(result.getMessage()).isEqualTo("얼큰한 김치찌개 어때요?");
        assertThat(result.getRecipeCards()).hasSize(1);
        assertThat(result.getRecipeCards().get(0).getRecipeId()).isEqualTo(10L);
        verify(chatMonitorLogger, never()).logUsage(anyLong(), anyInt(), anyInt(), anyInt(), eq("FALLBACK"), anyString());
    }

    @Test
    @DisplayName("Gemini 응답이 JSON 형식이 아니면 검색 결과 기반 Fallback 카드로 대체한다")
    void fallsBackWhenJsonParsingFails() {
        givenSearchResultsAndEmptyContext();
        when(geminiService.generateChatResponse(eq(MEMBER_ID), anyString(), anyList()))
                .thenReturn("이건 JSON이 아니라 그냥 텍스트입니다");

        ChatProcessorResult result = ragChatProcessor.process(MEMBER_ID, "비 오는 날 뭐 먹지", List.of());

        assertThat(result.getRecipeCards()).hasSize(1);
        assertThat(result.getRecipeCards().get(0).getRecipeId()).isEqualTo(100L);
        verify(chatMonitorLogger).logUsage(MEMBER_ID, 0, 0, 0, "FALLBACK", "PARSE_ERROR");
    }

    @Test
    @DisplayName("GeminiService가 BusinessException을 던지면 200 OK로 감추지 않고 그대로 전파한다")
    void propagatesBusinessExceptionInsteadOfSwallowing() {
        givenSearchResultsAndEmptyContext();
        when(geminiService.generateChatResponse(eq(MEMBER_ID), anyString(), anyList()))
                .thenThrow(new BusinessException(ErrorStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> ragChatProcessor.process(MEMBER_ID, "비 오는 날 뭐 먹지", List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getBaseCode())
                .isEqualTo(ErrorStatus.SERVICE_UNAVAILABLE);

        verify(chatMonitorLogger).logUsage(MEMBER_ID, 0, 0, 0, "ERROR", ErrorStatus.SERVICE_UNAVAILABLE.getCode());
    }

    private void givenSearchResultsAndEmptyContext() {
        when(recipeSearchService.searchTop3ByVector(anyString())).thenReturn(List.of(
                RecipeSearchResponseDto.builder().id(100L).title("김치찌개").similarity(0.9).build()
        ));
        when(memberIngredientRepository.findAllByMemberIdOrderByExpireDateAsc(MEMBER_ID)).thenReturn(List.of());
        when(cookingRecordRepository.findAllByMemberIdAndCreatedAtAfter(eq(MEMBER_ID), any())).thenReturn(List.of());
        when(chatContextHelper.buildHistoryContents(anyList(), eq(12))).thenReturn(new ArrayList<>());
    }
}
