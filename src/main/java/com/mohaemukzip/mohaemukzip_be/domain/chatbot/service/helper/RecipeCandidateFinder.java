package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.helper;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.IngredientRepository;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.RecipeIngredientRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.Recipe;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.enums.Category;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.CategoryRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 사용자의 자유 텍스트 메시지에서 재료명/조리시간/카테고리 키워드를 가볍게 추출해
 * 구조화된 DB 필터링을 시도하고, 매칭되는 키워드가 없으면 인기순 상위 레시피로 폴백한다.
 * 벡터 임베딩 검색을 대체하는 1차 후보(candidate) 조회 컴포넌트.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeCandidateFinder {

    private static final Pattern COOKING_TIME_PATTERN = Pattern.compile("(\\d+)\\s*분");
    private static final int CANDIDATE_LIMIT = 50;

    private static final Map<String, Category> CUISINE_KEYWORDS = Map.of(
            "한식", Category.KOREAN,
            "중식", Category.CHINESE,
            "중국", Category.CHINESE,
            "일식", Category.JAPANESE,
            "일본", Category.JAPANESE,
            "양식", Category.WESTERN,
            "아시안", Category.ASIAN
    );

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final CategoryRepository categoryRepository;

    /**
     * 메시지에서 추출 가능한 만큼만 구조화 필터링을 적용해 후보 레시피를 반환한다.
     * 어떤 키워드도 매칭되지 않거나, 필터링 결과가 비어있으면 인기순 상위 레시피로 폴백한다.
     */
    public List<Recipe> findCandidates(String message) {
        Set<Long> ingredientMatchedIds = extractIngredientMatchedRecipeIds(message);
        Integer maxCookingTime = extractMaxCookingTime(message);
        Category cuisine = extractCuisineCategory(message);
        Set<Long> situationMatchedIds = extractSituationMatchedRecipeIds(message);

        boolean anyFacetDetected = ingredientMatchedIds != null || maxCookingTime != null
                || cuisine != null || situationMatchedIds != null;

        if (!anyFacetDetected) {
            log.info("[레시피 후보 검색] 매칭되는 키워드 없음 -> 인기순 폴백");
            return recipeRepository.findTop50ByOrderByViewsDesc();
        }

        Set<Long> candidateIds = null;
        candidateIds = intersect(candidateIds, ingredientMatchedIds);
        candidateIds = intersect(candidateIds, situationMatchedIds);
        if (maxCookingTime != null) {
            Set<Long> cookingTimeIds = new HashSet<>(recipeRepository.findIdsByCookingTimeLessThanEqual(maxCookingTime));
            candidateIds = intersect(candidateIds, cookingTimeIds);
        }
        if (cuisine != null) {
            Set<Long> cuisineIds = new HashSet<>(recipeRepository.findIdsByCategory(cuisine));
            candidateIds = intersect(candidateIds, cuisineIds);
        }

        if (candidateIds == null || candidateIds.isEmpty()) {
            log.info("[레시피 후보 검색] 키워드는 감지됐으나 매칭 결과 0건 -> 인기순 폴백");
            return recipeRepository.findTop50ByOrderByViewsDesc();
        }

        List<Recipe> candidates = recipeRepository.findByIdIn(candidateIds);
        if (candidates.size() > CANDIDATE_LIMIT) {
            candidates = candidates.stream()
                    .sorted(Comparator.comparing(Recipe::getViews, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .limit(CANDIDATE_LIMIT)
                    .collect(Collectors.toList());
        }
        return candidates;
    }

    private Set<Long> extractIngredientMatchedRecipeIds(String message) {
        List<Long> ingredientIds = ingredientRepository.findIdsContainedInMessage(message);
        if (ingredientIds.isEmpty()) {
            return null;
        }
        return new HashSet<>(recipeIngredientRepository.findRecipeIdsByIngredientIds(ingredientIds));
    }

    private Integer extractMaxCookingTime(String message) {
        Matcher matcher = COOKING_TIME_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Category extractCuisineCategory(String message) {
        return CUISINE_KEYWORDS.entrySet().stream()
                .filter(entry -> message.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private Set<Long> extractSituationMatchedRecipeIds(String message) {
        List<Long> categoryIds = categoryRepository.findIdsContainedInMessage(message);
        if (categoryIds.isEmpty()) {
            return null;
        }

        Set<Long> recipeIds = new HashSet<>();
        for (Long categoryId : categoryIds) {
            recipeRepository.findRecipesByDishCategoryId(categoryId, Pageable.unpaged())
                    .forEach(recipe -> recipeIds.add(recipe.getId()));
        }
        return recipeIds;
    }

    private Set<Long> intersect(Set<Long> current, Set<Long> next) {
        if (next == null) {
            return current;
        }
        if (current == null) {
            return new HashSet<>(next);
        }
        current.retainAll(next);
        return current;
    }
}
