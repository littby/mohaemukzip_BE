package com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository;


import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.IngredientRequest;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IngredientRequestRepository extends JpaRepository<IngredientRequest, Long> {


    Optional<IngredientRequest> findByMemberAndIngredientName(Member member, String ingredientName);

    List<IngredientRequest> findAllByOrderByCreatedAtDesc();

    void deleteAllByMember_Id(Long memberId);
}