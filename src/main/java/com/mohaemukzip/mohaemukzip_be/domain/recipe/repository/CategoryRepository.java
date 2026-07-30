package com.mohaemukzip.mohaemukzip_be.domain.recipe.repository;

import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // 챗봇 메시지에 카테고리명이 부분 문자열로 포함되는지 DB 레벨에서 매칭 (전체 조회 후 메모리 필터링 방지)
    // 빈 문자열 이름이 있으면 LIKE '%%'가 전부 매칭되므로 공백 이름은 제외
    @Query("SELECT c.id FROM Category c WHERE TRIM(c.name) <> '' AND :message LIKE CONCAT('%', c.name, '%')")
    List<Long> findIdsContainedInMessage(@Param("message") String message);
}
