package com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.Ingredient;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.enums.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    // 모든 재료 이름 조회 (Gemini 프롬프트용)
    @Query("SELECT i.name FROM Ingredient i")
    List<String> findAllNames();

    // 재료명으로 조회 (매칭용)
    List<Ingredient> findAllByNameIn(List<String> names);

    // 챗봇 메시지에 재료명이 부분 문자열로 포함되는지 DB 레벨에서 매칭 (전체 조회 후 메모리 필터링 방지)
    // 빈 문자열 이름이 있으면 LIKE '%%'가 전부 매칭되므로 공백 이름은 제외
    @Query("SELECT i.id FROM Ingredient i WHERE TRIM(i.name) <> '' AND :message LIKE CONCAT('%', i.name, '%')")
    List<Long> findIdsContainedInMessage(@Param("message") String message);

    @Query("""
SELECT i FROM Ingredient i
WHERE (:keyword IS NULL OR REPLACE(i.name, ' ', '') LIKE CONCAT('%', REPLACE(:keyword, ' ', ''), '%'))
AND (:category IS NULL OR i.category = :category)
""")
    Page<Ingredient> findByKeywordAndCategory(
            @Param("keyword") String keyword,
            @Param("category") Category category,
            Pageable pageable
    );
}
