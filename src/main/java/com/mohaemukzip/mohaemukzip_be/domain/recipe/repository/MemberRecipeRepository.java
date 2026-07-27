package com.mohaemukzip.mohaemukzip_be.domain.recipe.repository;

import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.MemberRecipe;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MemberRecipeRepository extends JpaRepository<MemberRecipe,Long> {

    //레시피 1건에 대해서 사용자가 저장한 레시피인지 여부 판단
    boolean existsByMember_IdAndRecipe_Id(Long memberId, Long recipeId);

    // 북마크 토글(삭제)을 위해 엔티티 조회
    Optional<MemberRecipe> findByMemberAndRecipe(Member member, Recipe recipe);

    // 최적화된 삭제 로직 (Bulk Delete)
    @Modifying
    @Query("DELETE FROM MemberRecipe mr WHERE mr.member.id = :memberId AND mr.recipe.id = :recipeId")
    int deleteByMemberIdAndRecipeId(@Param("memberId") Long memberId, @Param("recipeId") Long recipeId);

    @Query("SELECT mr.recipe.id FROM MemberRecipe mr WHERE mr.member.id = :memberId AND mr.recipe.id IN :recipeIds")
    Set<Long> findBookmarkedRecipeIdsByMemberId(@Param("memberId") Long memberId, @Param("recipeIds") List<Long> recipeIds);

    @Query( value = "SELECT mr FROM MemberRecipe mr " +
            "JOIN FETCH mr.recipe r " +
            "WHERE mr.member.id = :memberId " +
            "ORDER BY mr.createdAt DESC",
            countQuery = "SELECT count(mr) FROM MemberRecipe mr WHERE mr.member.id = :memberId")
    Page<MemberRecipe> findByMemberIdOrderByCreatedAtDesc(@Param("memberId") Long memberId, Pageable pageable);

    void deleteAllByMember_Id(Long memberId);
}
