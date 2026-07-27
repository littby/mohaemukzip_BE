package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.RedisChatMessage;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.request.GeminiRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatProcessorResult;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.RecipeCardResponse;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.external.GeminiService;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.helper.ChatContextHelper;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.helper.ChatMonitorLogger;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.MemberIngredient;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.MemberIngredientRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.dto.RecipeSearchResponseDto;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.CookingRecord;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.CookingRecordRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.service.query.RecipeSearchService;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG(Retrieval-Augmented Generation) 기반 챗봇 프로세서.
 *
 * [동작 흐름]
 * 1. [Retrieval] RecipeSearchService를 통해 사용자 질문과 유사한 레시피 상위 3개를 벡터 검색으로 찾음
 * 2. [Context 수집] 사용자의 냉장고 재료(임박 순) + 최근 7일 식사 이력을 조회
 * 3. [Generation] 위 정보를 엄격한 System Prompt와 함께 Gemini에 전달
 * → Gemini는 반드시 JSON 배열 형태로만 응답해야 함 (추천 이유, 재료 매칭률 포함)
 * 4. Gemini 응답 JSON을 파싱하여 RecipeCardResponse 리스트로 반환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagChatProcessor implements ChatProcessor {

    private final RecipeSearchService recipeSearchService;
    private final MemberIngredientRepository memberIngredientRepository;
    private final CookingRecordRepository cookingRecordRepository;
    private final GeminiService geminiService;
    private final ChatContextHelper chatContextHelper;
    private final ChatMonitorLogger chatMonitorLogger;
    private final ObjectMapper objectMapper;

    /**
     * [System Prompt - 엄격 모드]
     * Gemini가 JSON 객체 이외의 텍스트를 절대 출력하지 않도록 강제합니다.
     * 마크다운 코드 블록(```json ... ```)도 붙이지 않도록 명시합니다.
     */
    private static final String SYSTEM_PROMPT = "당신은 요리 레시피 추천 전문 AI입니다.\n" +
            "당신의 유일한 역할은 주어진 레시피 후보와 사용자 상황을 분석하여 아래 JSON 객체 형식으로만 응답하는 것입니다.\n\n" +
            "[엄격한 규칙 - 반드시 준수]\n" +
            "1. 응답은 반드시 순수한 JSON 객체({ ... })로만 시작하고 끝나야 합니다.\n" +
            "2. ```json 같은 마크다운 코드 블록, 설명 문구, 인사말은 절대 포함하지 마세요.\n" +
            "3. JSON 외의 어떤 텍스트도 앞뒤로 절대 붙이지 마세요.\n" +
            "4. 'main_title'은 사용자의 질문 의도를 반영한 톡톡 튀는 짧은 제목입니다. (예: \"그렇다면 이런 요리는 어때요?\")\n" +
            "5. 'main_message'는 레시피들을 추천하는 부드럽고 친절한 안내 멘트입니다. (예: \"비오는 날에 어울리는 얼큰한 집밥 몇 가지를 추천해드릴게요.\")\n" +
            "6. 'ingredients_match_rate'는 사용자의 냉장고 재료와 레시피 재료의 예상 매칭률(0~100 정수)입니다.\n" +
            "7. 'recommend_reason'은 사용자 상황(냉장고 재료, 최근 식사 이력)을 구체적으로 언급하며 2~3문장으로 작성하세요.\n" +
            "8. [예외 처리] 만약 사용자의 질문이 요리, 식재료, 레시피 추천과 전혀 무관하다면(예: 비트코인, 날씨 등), 강제로 레시피를 추천하지 마세요. 이 경우 'recipe_cards' 배열을 비우고, 'main_title'과 'main_message'에 \"저는 요리 추천 챗봇 모해먹집이에요. 음식이나 레시피에 대해 물어봐주세요!\"와 같이 부드럽게 거절하는 멘트를 작성하세요.\n" +
            "9. 반드시 아래 JSON 구조를 정확히 따르세요:\n\n" +
            "{\n" +
            "  \"main_title\": \"챗봇 응답 제목\",\n" +
            "  \"main_message\": \"챗봇 응답 메시지\",\n" +
            "  \"recipe_cards\": [\n" +
            "    {\n" +
            "      \"recipe_id\": 숫자,\n" +
            "      \"title\": \"레시피 제목\",\n" +
            "      \"recommend_reason\": \"추천 이유 2~3문장\",\n" +
            "      \"ingredients_match_rate\": 0~100 사이 정수\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    // 내부 파싱용 DTO
    record GeminiRagResponse(String main_title, String main_message, List<RecipeCardResponse> recipe_cards) {}

    @Override
    public ChatProcessorResult process(Long memberId, String userMessage, List<RedisChatMessage> history) {
        try {
            log.info("[RAG 챗봇] 처리 시작 - memberId: {}, query: {}", memberId, userMessage);

            // ──────────────────────────────────────────
            // 1. [Retrieval] 벡터 검색으로 관련 레시피 TOP 3 조회 (임계값 0.5 적용)
            // ──────────────────────────────────────────
            List<RecipeSearchResponseDto> topRecipes = recipeSearchService.searchTop3ByVector(userMessage);
            log.info("[RAG 챗봇] 벡터 검색 완료 - {}건 발견", topRecipes.size());

            // [Fast-fail] 요리와 완전 무관한 질문이라 유사도가 모두 낮아 검색 결과가 0건인 경우
            if (topRecipes.isEmpty()) {
                log.info("[RAG 챗봇] 검색 결과 0건 (유사도 미달) -> Fast-fail 처리");

                // [모니터링] 엉뚱한 질문 (토큰 소모 없음) 로깅
                chatMonitorLogger.logUsage(memberId, 0, 0, 0, "FAST_FAIL");

                return ChatProcessorResult.builder()
                        .title("레시피를 찾을 수 없어요 \uD83D\uDE22")
                        .message("말씀하신 내용과 어울리는 레시피를 찾지 못했어요. 저는 요리 추천 챗봇 모해먹집이에요. 음식이나 레시피에 대해 물어봐주시면 친절하게 답변해 드릴게요!")
                        .recipeCards(Collections.emptyList())
                        .build();
            }

            // ──────────────────────────────────────────
            // 2. [Context] 냉장고 재료 조회 (유통기한 임박 순 상위 5개)
            // ──────────────────────────────────────────
            List<MemberIngredient> ingredients = memberIngredientRepository
                    .findAllByMemberIdOrderByExpireDateAsc(memberId);
            List<String> fridgeIngredients = ingredients.stream()
                    .limit(5)
                    .map(mi -> mi.getIngredient().getName())
                    .collect(Collectors.toList());

            // ──────────────────────────────────────────
            // 3. [Context] 최근 7일 식사 이력 조회
            // ──────────────────────────────────────────
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            List<CookingRecord> cookingHistory = cookingRecordRepository
                    .findAllByMemberIdAndCreatedAtAfter(memberId, sevenDaysAgo);
            Set<String> recentMeals = cookingHistory.stream()
                    .map(h -> h.getRecipe().getTitle())
                    .collect(Collectors.toSet());

            // ──────────────────────────────────────────
            // 4. [Generation] Gemini 프롬프트 조합 및 호출
            // ──────────────────────────────────────────
            // 이전 대화 기록 주입 (최근 12턴 제한)
            List<GeminiRequestDTO.Content> contents = chatContextHelper.buildHistoryContents(history, 12);

            // 현재 사용자 메시지 + 컨텍스트 조합
            String userPrompt = buildRagPrompt(userMessage, topRecipes, fridgeIngredients, recentMeals);
            contents.add(GeminiRequestDTO.Content.builder()
                    .role("user")
                    .parts(List.of(GeminiRequestDTO.Part.builder().text(userPrompt).build()))
                    .build());

            String aiResponse = geminiService.generateChatResponse(memberId, SYSTEM_PROMPT, contents);
            log.info("[RAG 챗봇] Gemini 응답 수신 완료");

            // ──────────────────────────────────────────
            // 5. JSON 응답 파싱 → RecipeCardResponse 리스트 및 텍스트 변환
            // ──────────────────────────────────────────
            GeminiRagResponse parsedResponse = parseJsonResponse(memberId, aiResponse, topRecipes);

            return ChatProcessorResult.builder()
                    .title(parsedResponse.main_title() != null ? parsedResponse.main_title() : "맞춤 레시피 추천")
                    .message(parsedResponse.main_message() != null ? parsedResponse.main_message() : "회원님의 상황에 맞는 레시피를 찾아봤어요! 아래 카드를 확인해 보세요 🍳")
                    .recipeCards(parsedResponse.recipe_cards() != null ? parsedResponse.recipe_cards() : Collections.emptyList())
                    .build();

        } catch (BusinessException e) {
            // GeminiService(서킷브레이커 포함)가 이미 의미 있는 에러 코드로 던진 예외이므로
            // 200 OK로 감추지 않고 GlobalExceptionHandler에 그대로 위임한다.
            log.error("[RAG 챗봇] 처리 중 비즈니스 예외 발생", e);
            chatMonitorLogger.logUsage(memberId, 0, 0, 0, "ERROR", e.getBaseCode().getCode());
            throw e;
        } catch (Exception e) {
            log.error("[RAG 챗봇] 처리 중 예외 발생", e);
            chatMonitorLogger.logUsage(memberId, 0, 0, 0, "ERROR", "SYSTEM_ERROR");
            throw new BusinessException(ErrorStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Gemini에 전달할 최종 사용자 프롬프트를 조합합니다.
     * 검색된 레시피 정보, 냉장고 재료, 최근 식사 이력을 구체적인 컨텍스트로 제공합니다.
     */
    private String buildRagPrompt(String userMessage,
            List<RecipeSearchResponseDto> topRecipes,
            List<String> fridgeIngredients,
            Set<String> recentMeals) {
        StringBuilder sb = new StringBuilder();

        sb.append("[사용자 질문]\n\"").append(userMessage).append("\"\n\n");

        sb.append("[추천 레시피 후보 (벡터 검색 결과)]\n");
        for (RecipeSearchResponseDto recipe : topRecipes) {
            sb.append(String.format("- ID: %d | 제목: %s | 유사도: %.4f\n",
                    recipe.getId(), recipe.getTitle(), recipe.getSimilarity()));
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

        sb.append("\n위 정보를 바탕으로 추천 레시피 후보 중에서 사용자 상황에 가장 적합한 것들을 선택하여 ")
                .append("지정된 JSON 형식으로만 응답하세요.");

        return sb.toString();
    }

    /**
     * Gemini의 JSON 응답 문자열을 GeminiRagResponse 객체로 파싱합니다.
     * 파싱 실패 시 검색 결과를 기반으로 Fallback 데이터를 생성합니다.
     */
    private GeminiRagResponse parseJsonResponse(Long memberId, String aiResponse,
            List<RecipeSearchResponseDto> fallbackRecipes) {
        if (aiResponse == null || aiResponse.isBlank()) {
            log.warn("[RAG 챗봇] Gemini 응답이 null 또는 비어있음 → Fallback 적용");

            // [모니터링] 구글 API 에러(503 등) 발생 로깅
            chatMonitorLogger.logUsage(memberId, 0, 0, 0, "FALLBACK", "NULL_RESPONSE");

            return createFallbackResponse(fallbackRecipes);
        }

        try {
            // Gemini가 혹시 ```json ... ``` 을 붙일 경우 제거
            String cleaned = aiResponse
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            return objectMapper.readValue(cleaned, GeminiRagResponse.class);
        } catch (Exception e) {
            log.error("[RAG 챗봇] JSON 파싱 실패, Fallback 적용. 응답 길이: {}", aiResponse.length(), e);

            // [모니터링] 응답 형식이 깨진 경우(파싱 에러) 로깅
            chatMonitorLogger.logUsage(memberId, 0, 0, 0, "FALLBACK", "PARSE_ERROR");

            return createFallbackResponse(fallbackRecipes);
        }
    }

    /**
     * Fallback 처리 시 사용자에게 명확한 안내를 포함하는 DTO를 생성합니다.
     */
    private GeminiRagResponse createFallbackResponse(List<RecipeSearchResponseDto> fallbackRecipes) {
        return new GeminiRagResponse(
                "서버 접속자 폭주로 인한 지연 안내 ⏳",
                "현재 AI 추천 서버 접속자가 많아 맞춤 분석이 일시적으로 지연되고 있어요. 대신 질문과 연관성이 높은 레시피를 우선 보여드릴게요! 잠시 후 다시 질문해 주시면 더욱 정확한 맞춤 추천이 가능합니다.",
                buildFallbackCards(fallbackRecipes)
        );
    }

    /**
     * Gemini 응답 파싱 실패 시, 벡터 검색 결과만으로 최소한의 카드를 만들어 반환합니다.
     */
    private List<RecipeCardResponse> buildFallbackCards(List<RecipeSearchResponseDto> recipes) {
        return recipes.stream()
                .map(r -> RecipeCardResponse.builder()
                        .recipeId(r.getId())
                        .title(r.getTitle())
                        .recommendReason("AI 맞춤 분석 지연으로 질문과 가장 연관성이 높은 레시피를 우선 추천해 드려요.")
                        .ingredientsMatchRate(0)
                        .build())
                .collect(Collectors.toList());
    }
}
