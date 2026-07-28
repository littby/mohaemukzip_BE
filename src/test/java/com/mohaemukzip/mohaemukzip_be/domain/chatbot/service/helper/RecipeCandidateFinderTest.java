package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.helper;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.Ingredient;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.IngredientRepository;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.RecipeIngredientRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.Recipe;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.enums.Category;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.CategoryRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeCandidateFinderTest {

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private IngredientRepository ingredientRepository;
    @Mock
    private RecipeIngredientRepository recipeIngredientRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private RecipeCandidateFinder recipeCandidateFinder;

    @BeforeEach
    void setUp() {
        recipeCandidateFinder = new RecipeCandidateFinder(
                recipeRepository, ingredientRepository, recipeIngredientRepository, categoryRepository);
        lenient().when(ingredientRepository.findAll()).thenReturn(List.of());
        lenient().when(categoryRepository.findAll()).thenReturn(List.of());
    }

    private Recipe recipe(Long id) {
        return Recipe.builder().id(id).title("레시피" + id).views(0L).build();
    }

    @Test
    @DisplayName("아무 키워드도 매칭되지 않으면 인기순 폴백 목록을 반환한다")
    void fallsBackToPopularWhenNoKeywordMatched() {
        List<Recipe> popular = List.of(recipe(1L), recipe(2L));
        when(recipeRepository.findTop50ByOrderByViewsDesc()).thenReturn(popular);

        List<Recipe> result = recipeCandidateFinder.findCandidates("얼큰한 거 땡겨");

        assertThat(result).isEqualTo(popular);
        verify(recipeRepository, never()).findByIdIn(anyCollection());
    }

    @Test
    @DisplayName("메시지에 등록된 재료명이 포함되면 해당 재료를 쓰는 레시피로 필터링한다")
    void filtersByMatchedIngredientName() {
        Ingredient tofu = Ingredient.builder().id(100L).name("두부").build();
        when(ingredientRepository.findAll()).thenReturn(List.of(tofu));
        when(recipeIngredientRepository.findRecipeIdsByIngredientIds(List.of(100L))).thenReturn(List.of(5L));
        when(recipeRepository.findByIdIn(anyCollection())).thenReturn(List.of(recipe(5L)));

        List<Recipe> result = recipeCandidateFinder.findCandidates("두부로 뭐 해먹지");

        assertThat(result).extracting(Recipe::getId).containsExactly(5L);
        verify(recipeRepository, never()).findTop50ByOrderByViewsDesc();
    }

    @Test
    @DisplayName("N분 이내 같은 조리시간 표현이 있으면 조리시간 이하 레시피로 필터링한다")
    void filtersByCookingTimeExpression() {
        when(recipeRepository.findIdsByCookingTimeLessThanEqual(20)).thenReturn(List.of(7L));
        when(recipeRepository.findByIdIn(anyCollection())).thenReturn(List.of(recipe(7L)));

        List<Recipe> result = recipeCandidateFinder.findCandidates("20분 안에 만들 수 있는 요리 추천해줘");

        assertThat(result).extracting(Recipe::getId).containsExactly(7L);
    }

    @Test
    @DisplayName("한식/중식 등 요리 종류 키워드가 있으면 해당 카테고리로 필터링한다")
    void filtersByCuisineKeyword() {
        when(recipeRepository.findIdsByCategory(Category.KOREAN)).thenReturn(List.of(3L));
        when(recipeRepository.findByIdIn(anyCollection())).thenReturn(List.of(recipe(3L)));

        List<Recipe> result = recipeCandidateFinder.findCandidates("한식 요리 추천해줘");

        assertThat(result).extracting(Recipe::getId).containsExactly(3L);
    }

    @Test
    @DisplayName("키워드는 감지되지만 교집합 결과가 없으면 인기순 폴백으로 전환한다")
    void fallsBackToPopularWhenIntersectionIsEmpty() {
        Ingredient tofu = Ingredient.builder().id(100L).name("두부").build();
        when(ingredientRepository.findAll()).thenReturn(List.of(tofu));
        when(recipeIngredientRepository.findRecipeIdsByIngredientIds(List.of(100L))).thenReturn(List.of());

        List<Recipe> popular = List.of(recipe(9L));
        when(recipeRepository.findTop50ByOrderByViewsDesc()).thenReturn(popular);

        List<Recipe> result = recipeCandidateFinder.findCandidates("두부 요리 추천해줘");

        assertThat(result).isEqualTo(popular);
    }
}
