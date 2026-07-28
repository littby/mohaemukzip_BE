package com.mohaemukzip.mohaemukzip_be.domain.recipe.repository;

import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.Recipe;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.enums.Category;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    // 랜덤 레시피 조회 (MySQL 전용)
    @Query(value = "SELECT * FROM recipes ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<Recipe> findRandomRecipes(@Param("limit") int limit);

    // 기존 메서드 유지 (하위 호환성)
    @Query("SELECT r FROM Recipe r WHERE REPLACE(r.title, ' ', '') LIKE CONCAT('%', :keyword, '%')")
    List<Recipe> findByTitleContaining(@Param("keyword") String keyword);

    // [New] Projection + Pagination 적용된 검색 메서드
    @Query("SELECT r.id as id, r.title as title FROM Recipe r WHERE REPLACE(r.title, ' ', '') LIKE CONCAT('%', :keyword, '%')")
    Page<RecipeProjection> findProjectedByTitleContaining(@Param("keyword") String keyword, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // 2명이상 사용자가 한 레시피 동시 조회해서 addRating 하는거 방지용
    @Query("select r from Recipe r where r.id = :recipeId")
    Recipe findByIdForUpdate(@Param("recipeId") Long recipeId);

    // videoId 중복 저장 방지용
    boolean existsByVideoId(String videoId);

    // 조회수(views) 내림차순으로 상위 5개 조회 (HomeService 사용)
    List<Recipe> findTop5ByOrderByViewsDesc();

    // ===== 홈 화면 추천 레시피용 메서드 =====

    // 특정 ID 목록에 해당하는 레시피 조회
    List<Recipe> findByIdIn(Collection<Long> ids);

    // 특정 카테고리의 레시피 조회
    List<Recipe> findByCategory(Category category);

    // 카테고리별 레시피 ID만 조회 (추천 레시피용)
    @Query("SELECT r.id FROM Recipe r WHERE r.category = :category")
    List<Long> findIdsByCategory(@Param("category") Category category);

    // DishCategory를 통해 카테고리별 레시피 조회
    @Query("SELECT r FROM Recipe r " +
            "JOIN DishCategory dc ON r.dish = dc.dish " +
            "WHERE dc.category.id = :categoryId")
    Page<Recipe> findRecipesByDishCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

    // Dish ID로 레시피 조회
    Page<Recipe> findByDishId(Long dishId, Pageable pageable);
}
