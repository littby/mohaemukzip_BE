package com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.Ingredient;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.MemberFavorite;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MemberFavoriteRepository extends JpaRepository<MemberFavorite, Long> {

    Optional<MemberFavorite> findByMemberAndIngredient(Member member, Ingredient ingredient);

    @Query("SELECT mf.ingredient.id " +
            "FROM MemberFavorite mf " +
            "WHERE mf.member.id = :memberId")
    Set<Long> findIngredientIdsByMemberId(@Param("memberId") Long memberId);

    @Query("SELECT mf FROM MemberFavorite mf " +
            "JOIN FETCH mf.ingredient " +
            "WHERE mf.member.id = :memberId")
    List<MemberFavorite> findAllByMemberId(@Param("memberId") Long memberId);

    void deleteAllByMember_Id(Long memberId);

}
