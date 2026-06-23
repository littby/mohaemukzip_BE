package com.mohaemukzip.mohaemukzip_be.domain.ingredient.service.command;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.dto.IngredientRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.dto.IngredientResponseDTO;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.*;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.*;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.service.recent.RecentSearchService;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.MemberRepository;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class IngredientCommandServiceImpl implements IngredientCommandService {

    private final IngredientRequestRepository  ingredientRequestRepository;
    private final IngredientRepository ingredientRepository;
    private final MemberRepository memberRepository;
    private final RecentSearchService recentSearchService;
    private final MemberIngredientRepository memberIngredientRepository;
    private final MemberFavoriteRepository memberFavoriteRepository;

    private static final int MAX_RECENT_SEARCH_COUNT = 10;

    @Override
    public IngredientResponseDTO.AddFridgeResult addFridgeIngredient(Long memberId, IngredientRequestDTO.AddFridge request) {

        // 회원 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));

        // 재료 조회
        Ingredient ingredient = ingredientRepository.findById(request.getIngredientId())
                .orElseThrow(() -> new BusinessException(ErrorStatus.INGREDIENT_NOT_FOUND));

        // 이미 냉장고에 있는 재료인지 확인
        Optional<MemberIngredient> existingMemberIngredient =
                memberIngredientRepository.findByMemberAndIngredient(member, ingredient);

        MemberIngredient saved;
        if (existingMemberIngredient.isPresent()) {
            saved = existingMemberIngredient.get();
            saved.addQuantityAndRenewExpireDate(request.getWeight(), request.getExpireDate());
        } else {
            // 새로운 엔티티 생성
            MemberIngredient newMemberIngredient = MemberIngredient.builder()
                    .member(member)
                    .ingredient(ingredient)
                    .storageType(request.getStorageType())
                    .expireDate(request.getExpireDate())
                    .weight(request.getWeight())
                    .build();

            saved = memberIngredientRepository.save(newMemberIngredient);
        }

        return IngredientResponseDTO.AddFridgeResult.builder()
                .memberIngredientId(saved.getId())
                .build();
    }

    @Override
    public IngredientResponseDTO.DeleteFridgeIngredient deleteIngredient(Long memberIngredientId, Long memberId) {

        MemberIngredient memberIngredient = memberIngredientRepository.findByIdAndMemberId(memberIngredientId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.INGREDIENT_NOT_FOUND));

        memberIngredientRepository.delete(memberIngredient);

        return new IngredientResponseDTO.DeleteFridgeIngredient(memberIngredient.getId());
    }

    @Override
    @Transactional
    public IngredientResponseDTO.ToggleFavorite toggleFavorite(Long memberId, Long ingredientId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));

        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.INGREDIENT_NOT_FOUND));

        Optional<MemberFavorite> existingFavorite =
                memberFavoriteRepository.findByMemberAndIngredient(member, ingredient);

        if (existingFavorite.isPresent()) {
            // 이미 존재하면 삭제
            MemberFavorite favorite = existingFavorite.get();
            memberFavoriteRepository.delete(favorite);

            return IngredientResponseDTO.ToggleFavorite.builder()
                    .memberFavoriteId(favorite.getId())
                    .ingredientId(ingredientId)
                    .isFavorite(false)
                    .build();
        } else {
            // 존재하지 않으면 등록
            MemberFavorite memberFavorite = MemberFavorite.builder()
                    .member(member)
                    .ingredient(ingredient)
                    .build();

            MemberFavorite saved = memberFavoriteRepository.save(memberFavorite);

            return IngredientResponseDTO.ToggleFavorite.builder()
                    .memberFavoriteId(saved.getId())
                    .ingredientId(ingredientId)
                    .isFavorite(true)
                    .build();
        }
    }




    @Override
    @Transactional
    public void createIngredientRequest(Long memberId, IngredientRequestDTO.IngredientReq request) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));

        Optional<IngredientRequest> existingRequest =
                ingredientRequestRepository.findByMemberAndIngredientName(member, request.getIngredientName());

        if (existingRequest.isPresent()) {
            throw new BusinessException(ErrorStatus.INGREDIENT_ALREADY_REQUESTED);
        }

        IngredientRequest newRequest = IngredientRequest.builder()
                .member(member)
                .ingredientName(request.getIngredientName())
                .build();

        ingredientRequestRepository.save(newRequest);
    }

    //냉장고 재료 수정
    @Override
    @Transactional
    public IngredientResponseDTO.UpdateFridgeResult updateFridgeIngredient(
            Long memberId,
            Long memberIngredientId,
            IngredientRequestDTO.UpdateFridge request
    ) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));


        MemberIngredient memberIngredient = memberIngredientRepository.findById(memberIngredientId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_INGREDIENT_NOT_FOUND));

        // 본인의 재료인지 확인
        if (!memberIngredient.getMember().getId().equals(member.getId())) {
            throw new BusinessException(ErrorStatus.MEMBER_FORBIDDEN);
        }

        memberIngredient.update(
                request.getStorageType(),
                request.getExpireDate(),
                request.getWeight()
        );


        return IngredientResponseDTO.UpdateFridgeResult.builder()
                .memberIngredientId(memberIngredient.getId())
                .build();
    }

}
