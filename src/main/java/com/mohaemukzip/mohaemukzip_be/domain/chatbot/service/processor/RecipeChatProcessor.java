package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.RedisChatMessage;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.request.GeminiRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatProcessorResult;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.RecipeCardResponse;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.external.GeminiService;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.helper.ChatContextHelper;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.helper.ChatMonitorLogger;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.helper.RecipeCandidateFinder;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.MemberIngredient;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.MemberIngredientRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.CookingRecord;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.Recipe;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.CookingRecordRepository;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 레시피 추천 챗봇 프로세서.
 *
 * [동작 흐름]
 * 1. [후보 조회] RecipeCandidateFinder가 사용자 메시지에서 재료/조리시간/카테고리 키워드를 추출해
 *    구조화 DB 필터링을 시도하고, 매칭되지 않으면 인기순 레시피로 폴백한다 (벡터 임베딩 미사용).
 * 2. [컨텍스트 수집] 사용자의 냉장고 재료(임박 순) + 최근 7일 식사 이력을 조회한다.
 * 3. [Generation] 후보 목록 + 컨텍스트를 프롬프트로 조합해 Gemini에 1회 호출.
 *    Gemini는 카드별 텍스트를 만들지 않고 추천할 recipe_id만 선택하며(main_message에 이유를 녹여 작성),
 *    structured output(responseSchema)으로 JSON 형식을 API 레벨에서 강제한다.
 * 4. Gemini가 선택한 recipe_id가 실제 후보 목록에 있는지 검증하고(할루시네이션 방어),
 *    카드에 노출할 title/썸네일/영상길이는 Gemini의 텍스트를 신뢰하지 않고 서버가 DB 값으로 직접 채운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeChatProcessor implements ChatProcessor {

    private final RecipeCandidateFinder recipeCandidateFinder;
    private final MemberIngredientRepository memberIngredientRepository;
    private final CookingRecordRepository cookingRecordRepository;
    private final GeminiService geminiService;
    private final ChatContextHelper chatContextHelper;
    private final ChatMonitorLogger chatMonitorLogger;
    private final ObjectMapper objectMapper;

    private static final int MAX_RECOMMENDATIONS = 3;

    private static final String SYSTEM_PROMPT = "당신은 자취생을 위한 다정한 요리 추천 챗봇 '요선생'입니다.\n" +
            "당신에게는 DB에서 미리 선별된 [후보 레시피 목록]이 주어집니다.\n\n" +
            "[엄격한 규칙 - 반드시 준수]\n" +
            "1. 응답은 반드시 지정된 JSON 스키마 형식으로만 작성하세요.\n" +
            "2. 'recipe_ids'는 반드시 [후보 레시피 목록]에 실제로 있는 recipe_id만 사용하세요. 목록에 없는 id를 절대 지어내지 마세요. 제목/썸네일 등 다른 정보는 서버가 채우므로 recipe_id만 정확히 고르면 됩니다.\n" +
            "3. 'main_title'은 사용자의 질문 의도를 반영한 톡톡 튀는 짧은 제목입니다. (예: \"그렇다면 이런 요리는 어때요?\")\n" +
            "4. 'main_message'는 추천하는 레시피들을 한번에 소개하는 안내 멘트입니다. 사용자 상황(냉장고 재료, 최근 식사 이력, 질문 맥락)을 구체적으로 언급하며 왜 이 레시피들을 골랐는지 이유를 자연스럽게 녹여 2~4문장으로 작성하세요. 카드별로 이유를 따로 쓰지 말고 전체를 아우르는 하나의 안내 멘트로 작성하세요.\n" +
            "5. 후보 목록 중 사용자 질문/상황에 가장 적합한 것을 최대 " + MAX_RECOMMENDATIONS + "개까지 선택하세요.\n" +
            "6. [예외 처리] 사용자의 질문이 요리, 식재료, 레시피 추천과 전혀 무관하다면(예: 비트코인, 날씨, 정치 등), 후보 목록에서 억지로 추천하지 마세요. 이 경우 'recipe_ids'를 빈 배열로 두고, 'main_title'과 'main_message'에 \"저는 요리 추천 챗봇 요선생이에요. 음식이나 레시피에 대해 물어봐주세요!\"와 같이 부드럽게 거절하는 멘트를 작성하세요.\n" +
            "7. 후보 목록에 사용자 조건에 정확히 맞는 레시피가 없다면, 가장 비슷한 대안을 제시하고 그 사실을 'main_message'에 솔직히 언급하세요. 없는 레시피를 지어내지 마세요.";

    private static final Map<String, Object> RESPONSE_SCHEMA = buildResponseSchema();

    // 내부 파싱용 DTO
    record GeminiChatResponse(String main_title, String main_message, List<Long> recipe_ids) {}

    @Override
    public ChatProcessorResult process(Long memberId, String userMessage, List<RedisChatMessage> history) {
        try {
            log.info("[레시피 챗봇] 처리 시작 - memberId: {}, query: {}", memberId, userMessage);

            // 1. 후보 레시피 조회 (키워드 매칭 우선, 없으면 인기순 폴백)
            List<Recipe> candidates = recipeCandidateFinder.findCandidates(userMessage);
            log.info("[레시피 챗봇] 후보 조회 완료 - {}건", candidates.size());

            if (candidates.isEmpty()) {
                chatMonitorLogger.logUsage(memberId, 0, 0, 0, "FAST_FAIL", "NO_CANDIDATES");
                return ChatProcessorResult.builder()
                        .title("레시피를 찾을 수 없어요 😢")
                        .message("조건에 맞는 레시피를 찾지 못했어요. 저는 요리 추천 챗봇 요선생이에요. 음식이나 레시피에 대해 물어봐주시면 친절하게 답변해 드릴게요!")
                        .recipeCards(Collections.emptyList())
                        .build();
            }

            Map<Long, Recipe> candidatesById = candidates.stream()
                    .collect(Collectors.toMap(Recipe::getId, r -> r, (a, b) -> a, LinkedHashMap::new));

            // 2. 냉장고 재료(임박 순) + 최근 7일 식사 이력 조회
            List<MemberIngredient> ingredients = memberIngredientRepository
                    .findAllByMemberIdOrderByExpireDateAsc(memberId);
            List<String> fridgeIngredientNames = ingredients.stream()
                    .limit(5)
                    .map(mi -> mi.getIngredient().getName())
                    .collect(Collectors.toList());

            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            List<CookingRecord> cookingHistory = cookingRecordRepository
                    .findAllByMemberIdAndCreatedAtAfter(memberId, sevenDaysAgo);
            Set<String> recentMeals = cookingHistory.stream()
                    .map(h -> h.getRecipe().getTitle())
                    .collect(Collectors.toSet());

            // 3. Gemini 프롬프트 조합 및 1회 호출
            List<GeminiRequestDTO.Content> contents = chatContextHelper.buildHistoryContents(history, 12);

            String userPrompt = buildUserPrompt(userMessage, candidates, fridgeIngredientNames, recentMeals);
            contents.add(GeminiRequestDTO.Content.builder()
                    .role("user")
                    .parts(List.of(GeminiRequestDTO.Part.builder().text(userPrompt).build()))
                    .build());

            String aiResponse = geminiService.generateChatResponse(memberId, SYSTEM_PROMPT, contents, RESPONSE_SCHEMA);
            log.info("[레시피 챗봇] Gemini 응답 수신 완료");

            // 4. 응답 파싱 + recipe_id 할루시네이션 검증 + 카드 데이터는 서버가 직접 구성
            GeminiChatResponse parsedResponse = parseResponse(memberId, aiResponse, candidates);
            List<RecipeCardResponse> cards = toValidatedCards(parsedResponse.recipe_ids(), candidatesById, memberId);

            return ChatProcessorResult.builder()
                    .title(parsedResponse.main_title() != null ? parsedResponse.main_title() : "맞춤 레시피 추천")
                    .message(parsedResponse.main_message() != null ? parsedResponse.main_message() : "회원님의 상황에 맞는 레시피를 찾아봤어요! 아래 영상을 확인해 보세요 🍳")
                    .recipeCards(cards)
                    .build();

        } catch (BusinessException e) {
            // GeminiService(서킷브레이커 포함)가 이미 의미 있는 에러 코드로 던진 예외이므로
            // 200 OK로 감추지 않고 GlobalExceptionHandler에 그대로 위임한다.
            log.error("[레시피 챗봇] 처리 중 비즈니스 예외 발생", e);
            chatMonitorLogger.logUsage(memberId, 0, 0, 0, "ERROR", e.getBaseCode().getCode());
            throw e;
        } catch (Exception e) {
            log.error("[레시피 챗봇] 처리 중 예외 발생", e);
            chatMonitorLogger.logUsage(memberId, 0, 0, 0, "ERROR", "SYSTEM_ERROR");
            throw new BusinessException(ErrorStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildUserPrompt(String userMessage, List<Recipe> candidates,
                                    List<String> fridgeIngredients, Set<String> recentMeals) {
        StringBuilder sb = new StringBuilder();

        sb.append("[사용자 질문]\n\"").append(userMessage).append("\"\n\n");

        sb.append("[후보 레시피 목록]\n");
        for (Recipe recipe : candidates) {
            sb.append(String.format("- recipe_id: %d | 제목: %s | 카테고리: %s | 조리시간: %s분\n",
                    recipe.getId(),
                    recipe.getTitle(),
                    recipe.getCategory() != null ? recipe.getCategory() : "미상",
                    recipe.getCookingTime() != null ? recipe.getCookingTime().toString() : "미상"));
        }

        sb.append("\n[사용자 냉장고 재료 (유통기한 임박 순)]\n");
        if (fridgeIngredients.isEmpty()) {
            sb.append("- 등록된 재료 없음\n");
        } else {
            fridgeIngredients.forEach(i -> sb.append("- ").append(i).append("\n"));
        }

        sb.append("\n[최근 7일 식사 이력]\n");
        if (recentMeals.isEmpty()) {
            sb.append("- 식사 이력 없음\n");
        } else {
            recentMeals.forEach(m -> sb.append("- ").append(m).append("\n"));
        }

        sb.append("\n위 후보 목록 중에서 사용자 질문/상황에 가장 적합한 레시피를 최대 ")
                .append(MAX_RECOMMENDATIONS)
                .append("개 선택해, 지정된 JSON 스키마로만 응답하세요.");

        return sb.toString();
    }

    private GeminiChatResponse parseResponse(Long memberId, String aiResponse, List<Recipe> candidates) {
        if (aiResponse == null || aiResponse.isBlank()) {
            log.warn("[레시피 챗봇] Gemini 응답이 null 또는 비어있음 → Fallback 적용");
            chatMonitorLogger.logUsage(memberId, 0, 0, 0, "FALLBACK", "NULL_RESPONSE");
            return createFallbackResponse(candidates);
        }

        try {
            // structured output을 쓰지만, 안전망으로 마크다운 코드블록 제거 로직은 유지
            String cleaned = aiResponse
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            return objectMapper.readValue(cleaned, GeminiChatResponse.class);
        } catch (Exception e) {
            log.error("[레시피 챗봇] JSON 파싱 실패, Fallback 적용. 응답 길이: {}", aiResponse.length(), e);
            chatMonitorLogger.logUsage(memberId, 0, 0, 0, "FALLBACK", "PARSE_ERROR");
            return createFallbackResponse(candidates);
        }
    }

    private GeminiChatResponse createFallbackResponse(List<Recipe> candidates) {
        List<Long> fallbackIds = candidates.stream()
                .sorted(Comparator.comparing(Recipe::getViews, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(MAX_RECOMMENDATIONS)
                .map(Recipe::getId)
                .collect(Collectors.toList());

        return new GeminiChatResponse(
                "서버 접속자 폭주로 인한 지연 안내 ⏳",
                "현재 AI 추천 서버 접속자가 많아 맞춤 분석이 일시적으로 지연되고 있어요. 대신 조건에 맞는 레시피를 우선 보여드릴게요! 잠시 후 다시 질문해 주시면 더욱 정확한 맞춤 추천이 가능합니다.",
                fallbackIds
        );
    }

    /**
     * Gemini가 반환한 recipe_id가 실제 후보 목록에 있었는지 검증해 없는 id(할루시네이션)는 제거하고,
     * 살아남은 id에 대해 title/썸네일/영상길이를 서버가 보유한 실제 Recipe 데이터로 채운 카드를 만든다.
     */
    private List<RecipeCardResponse> toValidatedCards(List<Long> recipeIds, Map<Long, Recipe> candidatesById, Long memberId) {
        if (recipeIds == null || recipeIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<RecipeCardResponse> cards = new ArrayList<>();
        int droppedCount = 0;
        for (Long recipeId : recipeIds) {
            Recipe recipe = recipeId != null ? candidatesById.get(recipeId) : null;
            if (recipe == null) {
                droppedCount++;
                continue;
            }
            cards.add(RecipeCardResponse.builder()
                    .recipeId(recipe.getId())
                    .title(recipe.getTitle())
                    .imageUrl(recipe.getImageUrl())
                    .videoTime(recipe.getTime())
                    .build());
        }

        if (droppedCount > 0) {
            log.warn("[레시피 챗봇] 후보 목록에 없는 recipe_id를 {}건 반환하여 제거함", droppedCount);
            chatMonitorLogger.logUsage(memberId, 0, 0, 0, "WARNING", "HALLUCINATED_RECIPE_ID");
        }

        return cards;
    }

    private static Map<String, Object> buildResponseSchema() {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "main_title", Map.of("type", "STRING"),
                        "main_message", Map.of("type", "STRING"),
                        "recipe_ids", Map.of(
                                "type", "ARRAY",
                                "items", Map.of("type", "INTEGER")
                        )
                ),
                "required", List.of("main_title", "main_message", "recipe_ids")
        );
    }
}
