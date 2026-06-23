package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.processor;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.RedisChatMessage;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.request.GeminiRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatProcessorResult;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity.enums.SenderType;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.external.GeminiService;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.MemberIngredient;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.MemberIngredientRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.CookingRecord;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.CookingRecordRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.Recipe;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class RecommendChatProcessor implements ChatProcessor {

    private final MemberIngredientRepository memberIngredientRepository;
    private final CookingRecordRepository cookingRecordRepository;
    private final RecipeRepository recipeRepository;
    private final GeminiService geminiService;

    private static final String SYSTEM_PROMPT = 
            "너는 자취생을 위한 다정한 요리 도우미 '요선생'이야.\n" +
            "너의 역할은 사용자의 상황과 대화 맥락을 파악하여 최적의 레시피를 추천하는 거야.\n\n" +
            "[규칙]\n" +
            "1. 친절하고, 이모티콘을 적절히 사용하며, 3문장 이내로 간결하게 답변해.\n" +
            "2. 답변은 항상 `제목 ||| 본문` 형식이야. (예: 제육볶음 황금 레시피! ||| 먼저 고기를...)\n" +
            "3. 사용자가 일상 대화(예: '오늘 날씨 좋다')를 하더라도, 음식이나 요리와 연관지어 자연스럽게 대화를 이끌어가.\n" +
            "4. 사용자 정보([임박 재료], [추천 후보 리스트])가 주어지면, 이를 반드시 활용해서 추천해야 해.\n" +
            "5. [추천 후보 리스트]에 없는 메뉴는 절대 지어내거나 언급하지 마.\n" +
            "6. 요리, 레시피, 식재료와 전혀 관련 없는 질문(정치, 코딩, 연애 등)에는 '저는 요리 이야기만 할 수 있어요 \uD83C\uDF73'라고만 답변해.";

    @Override
    public ChatProcessorResult process(Long memberId, String userMessage, List<RedisChatMessage> history) {
        try {
            log.info("ChatProcessor 처리 시작 - UserMessage Length: {}", (userMessage != null ? userMessage.length() : 0));

            Set<Recipe> candidateSet = new HashSet<>();

            String[] keywords = userMessage.split("\\s+");
            for (String keyword : keywords) {
                if (keyword.length() > 1) {
                    candidateSet.addAll(recipeRepository.findByTitleContaining(keyword));
                }
            }

            List<MemberIngredient> myIngredients = memberIngredientRepository.findAllByMemberIdOrderByExpireDateAsc(memberId);
            List<String> urgentIngredientNames = new ArrayList<>();

            if (!myIngredients.isEmpty()) {
                urgentIngredientNames = myIngredients.stream()
                        .limit(3)
                        .map(mi -> mi.getIngredient().getName())
                        .collect(Collectors.toList());

                for (String ingredientName : urgentIngredientNames) {
                    candidateSet.addAll(recipeRepository.findByTitleContaining(ingredientName));
                }
            }

            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            List<CookingRecord> histories = cookingRecordRepository.findAllByMemberIdAndCreatedAtAfter(memberId, sevenDaysAgo);
            Set<Long> cookedRecipeIds = histories.stream().map(h -> h.getRecipe().getId()).collect(Collectors.toSet());

            List<Recipe> filteredRecipes = candidateSet.stream()
                    .filter(r -> !cookedRecipeIds.contains(r.getId()))
                    .collect(Collectors.toList());

            int neededCount = 5 - filteredRecipes.size();
            if (neededCount > 0) {
                List<Recipe> randomRecipes = recipeRepository.findRandomRecipes(neededCount);
                
                for (Recipe r : randomRecipes) {
                    if (!cookedRecipeIds.contains(r.getId()) && !filteredRecipes.contains(r)) {
                        filteredRecipes.add(r);
                    }
                }
            }

            List<Recipe> finalRecipes = filteredRecipes.stream().limit(5).collect(Collectors.toList());

            List<GeminiRequestDTO.Content> contents = new ArrayList<>();
            
            // 이전 대화 기록 추가
            for (RedisChatMessage msg : history) {
                String role = msg.getSender() == SenderType.USER ? "user" : "model";
                String text = msg.getSender() == SenderType.USER ? msg.getContent() : 
                              (msg.getTitle() != null ? msg.getTitle() + " ||| " + msg.getContent() : msg.getContent());
                
                contents.add(GeminiRequestDTO.Content.builder()
                        .role(role)
                        .parts(List.of(GeminiRequestDTO.Part.builder().text(text).build()))
                        .build());
            }

            // 현재 메시지와 컨텍스트 추가
            String finalUserPrompt = buildFinalUserPrompt(userMessage, urgentIngredientNames, finalRecipes);
            contents.add(GeminiRequestDTO.Content.builder()
                    .role("user")
                    .parts(List.of(GeminiRequestDTO.Part.builder().text(finalUserPrompt).build()))
                    .build());

            String aiResponse = geminiService.generateChatResponse(SYSTEM_PROMPT, contents);

            return parseResponse(aiResponse, "오늘의 추천 메뉴", finalRecipes);

        } catch (Exception e) {
            log.error("ChatProcessor 처리 중 예외 발생", e);
            return ChatProcessorResult.builder()
                    .title("일시적 오류")
                    .message("죄송해요, 처리 중 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")
                    .recipes(Collections.emptyList())
                    .build();
        }
    }

    private ChatProcessorResult parseResponse(String aiResponse, String defaultTitle, List<Recipe> recipes) {
        String title = defaultTitle;
        String message = aiResponse;

        if (aiResponse == null) {
            log.warn("AI 응답 실패 -> Fallback 메시지 반환");
            if (!recipes.isEmpty()) {
                String mainRecipe = recipes.get(0).getTitle();
                message = String.format("죄송해요, 잠시 연결이 불안정해요 \uD83D\uDE22 하지만 지금 상황에 딱 맞는 **%s** 레시피를 찾아왔어요!", mainRecipe);
            } else {
                message = "죄송해요, 잠시 연결이 불안정해요 \uD83D\uDE22 '냉장고 파먹기'나 '메뉴 추천'이라고 말씀해 주시면 레시피를 찾아드릴게요!";
            }
        } else {
            String[] parts = aiResponse.split("\\|\\|\\|");
            if (parts.length >= 2) {
                title = parts[0].trim();
                message = parts[1].trim();
            }
        }

        return ChatProcessorResult.builder()
                .title(title)
                .message(message)
                .recipes(recipes)
                .build();
    }

    private String buildFinalUserPrompt(String userMessage, List<String> ingredients, List<Recipe> recipes) {
        String ingredientStr = ingredients.isEmpty() ? "없음" : String.join(", ", ingredients);
        String recipeStr = recipes.stream().map(Recipe::getTitle).collect(Collectors.joining(", "));

        return String.format(
                "[사용자 정보]\n" +
                "- 임박한 재료: [%s]\n" +
                "- 추천 후보 리스트: [%s]\n\n" +
                "[사용자 메시지]\n" +
                "\"%s\"",
                ingredientStr, recipeStr, userMessage
        );
    }
}
