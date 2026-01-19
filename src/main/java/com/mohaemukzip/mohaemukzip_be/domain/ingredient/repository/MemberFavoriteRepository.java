package com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.Ingredient;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.MemberFavorite;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberFavoriteRepository extends JpaRepository<MemberFavorite, Long> {

    boolean existsByMemberAndIngredient(Member member, Ingredient ingredient);

    @Query("SELECT mf FROM MemberFavorite mf " +
            "JOIN FETCH mf.ingredient " +
            "WHERE mf.member = :member")
    List<MemberFavorite> findAllByMember(@Param("member") Member member);
}
