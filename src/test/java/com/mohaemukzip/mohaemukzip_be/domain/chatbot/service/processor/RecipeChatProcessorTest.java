package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatProcessorResult;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.external.GeminiService;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.helper.ChatContextHelper;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.helper.ChatMonitorLogger;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.helper.RecipeCandidateFinder;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.MemberIngredientRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.Recipe;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.enums.Category;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.CookingRecordRepository;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeChatProcessorTest {

    @Mock
    private RecipeCandidateFinder recipeCandidateFinder;
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

    private RecipeChatProcessor recipeChatProcessor;

    private static final Long MEMBER_ID = 1L;

    @BeforeEach
    void setUp() {
        recipeChatProcessor = new RecipeChatProcessor(
                recipeCandidateFinder,
                memberIngredientRepository,
                cookingRecordRepository,
                geminiService,
                chatContextHelper,
                chatMonitorLogger,
                new ObjectMapper()
        );
    }

    private Recipe recipe(Long id, String title) {
        return Recipe.builder()
                .id(id)
                .title(title)
                .category(Category.KOREAN)
                .cookingTime(20)
                .views(100L)
                .imageUrl("https://img.example.com/" + id + ".jpg")
                .time("10:54")
                .build();
    }

    private void givenEmptyMemberContext() {
        when(memberIngredientRepository.findAllByMemberIdOrderByExpireDateAsc(MEMBER_ID)).thenReturn(List.of());
        when(cookingRecordRepository.findAllByMemberIdAndCreatedAtAfter(eq(MEMBER_ID), any())).thenReturn(List.of());
        when(chatContextHelper.buildHistoryContents(anyList(), eq(12))).thenReturn(new java.util.ArrayList<>());
    }

    @Test
    @DisplayName("후보 레시피가 하나도 없으면 Gemini를 호출하지 않고 즉시 안내 메시지를 반환한다")
    void returnsStaticMessageWhenNoCandidates() {
        when(recipeCandidateFinder.findCandidates(anyString())).thenReturn(List.of());

        ChatProcessorResult result = recipeChatProcessor.process(MEMBER_ID, "아무 말이나", List.of());

        assertThat(result.getRecipeCards()).isEmpty();
        assertThat(result.getMessage()).contains("요선생");
        verify(geminiService, never()).generateChatResponse(anyLong(), anyString(), anyList(), anyMap());
        verify(chatMonitorLogger).logUsage(MEMBER_ID, 0, 0, 0, "FAST_FAIL", "NO_CANDIDATES");
    }

    @Test
    @DisplayName("Gemini가 후보 목록에 있는 recipe_id를 선택하면, 카드의 title/썸네일/영상길이는 서버가 DB 값으로 채운다")
    void buildsCardFromServerDataForSelectedRecipeId() {
        Recipe candidate = recipe(10L, "김치찌개");
        when(recipeCandidateFinder.findCandidates(anyString())).thenReturn(List.of(candidate));
        givenEmptyMemberContext();

        String validJson = "{"
                + "\"main_title\": \"든든한 한 끼\","
                + "\"main_message\": \"김치찌개 어때요?\","
                + "\"recipe_ids\": [10]"
                + "}";
        when(geminiService.generateChatResponse(eq(MEMBER_ID), anyString(), anyList(), anyMap())).thenReturn(validJson);

        ChatProcessorResult result = recipeChatProcessor.process(MEMBER_ID, "든든한 거 뭐 먹지", List.of());

        assertThat(result.getTitle()).isEqualTo("든든한 한 끼");
        assertThat(result.getRecipeCards()).hasSize(1);
        assertThat(result.getRecipeCards().get(0).getRecipeId()).isEqualTo(10L);
        assertThat(result.getRecipeCards().get(0).getTitle()).isEqualTo("김치찌개");
        assertThat(result.getRecipeCards().get(0).getImageUrl()).isEqualTo("https://img.example.com/10.jpg");
        assertThat(result.getRecipeCards().get(0).getVideoTime()).isEqualTo("10:54");
    }

    @Test
    @DisplayName("Gemini가 후보 목록에 없는 recipe_id를 반환하면 검증 단계에서 제거한다 (할루시네이션 방어)")
    void dropsHallucinatedRecipeIdNotInCandidates() {
        when(recipeCandidateFinder.findCandidates(anyString())).thenReturn(List.of(recipe(10L, "김치찌개")));
        givenEmptyMemberContext();

        String jsonWithFakeId = "{"
                + "\"main_title\": \"제목\","
                + "\"main_message\": \"메시지\","
                + "\"recipe_ids\": [999]"
                + "}";
        when(geminiService.generateChatResponse(eq(MEMBER_ID), anyString(), anyList(), anyMap())).thenReturn(jsonWithFakeId);

        ChatProcessorResult result = recipeChatProcessor.process(MEMBER_ID, "뭐 먹지", List.of());

        assertThat(result.getRecipeCards()).isEmpty();
        verify(chatMonitorLogger).logUsage(MEMBER_ID, 0, 0, 0, "WARNING", "HALLUCINATED_RECIPE_ID");
    }

    @Test
    @DisplayName("Gemini 응답이 JSON 형식이 아니면 조회수 상위 후보로 Fallback 카드를 만든다")
    void fallsBackToTopViewedCandidatesWhenParsingFails() {
        Recipe popular = recipe(10L, "인기 레시피");
        Recipe lessPopular = Recipe.builder()
                .id(20L).title("비인기 레시피").category(Category.KOREAN)
                .cookingTime(20).views(1L).imageUrl("img").time("5:00").build();
        when(recipeCandidateFinder.findCandidates(anyString())).thenReturn(List.of(lessPopular, popular));
        givenEmptyMemberContext();

        when(geminiService.generateChatResponse(eq(MEMBER_ID), anyString(), anyList(), anyMap()))
                .thenReturn("이건 JSON이 아닙니다");

        ChatProcessorResult result = recipeChatProcessor.process(MEMBER_ID, "두부로 뭐 해먹지", List.of());

        assertThat(result.getRecipeCards()).hasSize(2);
        assertThat(result.getRecipeCards().get(0).getRecipeId()).isEqualTo(10L);
        verify(chatMonitorLogger).logUsage(MEMBER_ID, 0, 0, 0, "FALLBACK", "PARSE_ERROR");
    }

    @Test
    @DisplayName("GeminiService가 BusinessException을 던지면 200 OK로 감추지 않고 그대로 전파한다")
    void propagatesBusinessExceptionInsteadOfSwallowing() {
        when(recipeCandidateFinder.findCandidates(anyString())).thenReturn(List.of(recipe(10L, "김치찌개")));
        givenEmptyMemberContext();
        when(geminiService.generateChatResponse(eq(MEMBER_ID), anyString(), anyList(), anyMap()))
                .thenThrow(new BusinessException(ErrorStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> recipeChatProcessor.process(MEMBER_ID, "뭐 먹지", List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getBaseCode())
                .isEqualTo(ErrorStatus.SERVICE_UNAVAILABLE);
    }
}
