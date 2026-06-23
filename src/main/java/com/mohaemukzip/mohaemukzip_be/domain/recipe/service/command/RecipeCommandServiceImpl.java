package com.mohaemukzip.mohaemukzip_be.domain.recipe.service.command;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.Ingredient;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.MemberIngredient;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.RecipeIngredient;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.IngredientRepository;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.MemberIngredientRepository;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.RecipeIngredientRepository;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.MemberRepository;
import com.mohaemukzip.mohaemukzip_be.domain.mission.entity.MemberMission;
import com.mohaemukzip.mohaemukzip_be.domain.mission.entity.enums.MissionStatus;
import com.mohaemukzip.mohaemukzip_be.domain.mission.repository.MemberMissionRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.builder.GeminiPromptBuilder;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.dto.RecipeResponseDTO;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.*;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.converter.GeminiResponseConverter;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.converter.RecipeConverter;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.converter.RecipeIngredientConverter;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.converter.RecipeStepConverter;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.*;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.service.crawler.RecipeCrawler;
import com.mohaemukzip.mohaemukzip_be.global.client.transcript.TranscriptClient;
import com.mohaemukzip.mohaemukzip_be.global.client.transcript.dto.TranscriptSegment;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import com.mohaemukzip.mohaemukzip_be.global.service.LevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeCommandServiceImpl implements RecipeCommandService {

    private static final int MAX_RECIPE_STEPS = 10;
    private static final Duration GEMINI_API_TIMEOUT = Duration.ofSeconds(30);

    @Qualifier("geminiSummaryWebClient")
    private final WebClient geminiSummaryWebClient;
    private final RecipeRepository recipeRepository;
    private final CookingRecordRepository cookingRecordRepository;
    private final IngredientRepository ingredientRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final RecipeStepRepository recipeStepRepository;
    private final MemberMissionRepository memberMissionRepository;
    private final MemberRepository memberRepository;
    private final MemberIngredientRepository memberIngredientRepository;
    private final SummaryRepository summaryRepository;
    private final MemberRecipeRepository memberRecipeRepository;
    private final LevelService levelService;
    private final TranscriptClient transcriptClient;

    private final RecipeConverter recipeConverter;
    private final RecipeIngredientConverter recipeIngredientConverter;
    private final GeminiResponseConverter geminiResponseConverter;
    private final RecipeStepConverter recipeStepConverter;
    private final GeminiPromptBuilder geminiPromptBuilder;
    private final RecipeCrawler recipeCrawler;

    /**
     * videoId로 레시피 저장 (Recipe + RecipeIngredient)
     */
    @Override
    @Transactional
    public Long saveRecipeByVideoId(String videoId) {
        // 중복 방지
        if (recipeRepository.existsByVideoId(videoId)) {
            throw new BusinessException(ErrorStatus.RECIPE_ALREADY_EXISTS);
        }

        // Gemini 프롬프트용 재료 이름 조회
        List<String> ingredientNames = ingredientRepository.findAllNames();

        // 크롤링
        RecipeCrawler.RecipeData data = recipeCrawler.crawlRecipe(videoId, ingredientNames);

        Recipe recipe = saveRecipe(data);
        saveRecipeIngredients(recipe, data.ingredients());
        return recipe.getId();
    }

    @Transactional
    @Override
    public RecipeResponseDTO.SummaryCreateResult createSummary(Long recipeId) {

        //  이미 요약 존재 → 멱등
        Summary existing = summaryRepository.findByRecipeId(recipeId).orElse(null);
        if (existing != null) {
            int count = recipeStepRepository
                    .findAllBySummaryIdOrderByStepNumberAsc(existing.getId())
                    .size();
            return RecipeResponseDTO.SummaryCreateResult.builder()
                    .summaryExists(true)
                    .stepCount(count)
                    .build();
        }

        // Recipe 조회
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.RECIPE_NOT_FOUND));

        // 3자막 추출 (RapidAPI 클라이언트 사용)
        List<TranscriptSegment> transcripts = transcriptClient.fetchTranscript(recipe.getVideoId());

        // Summary 생성
        Summary summary = createSummaryWithRaceConditionHandling(recipe);

        // Gemini → step draft
        List<GeminiResponseConverter.StepDraft> steps =
                generateStepsFromGemini(recipe.getTitle(), transcripts);

        //  Step 저장
        List<RecipeStep> entities = recipeStepConverter.toEntities(summary, steps);

        recipeStepRepository.saveAll(entities);

        return RecipeResponseDTO.SummaryCreateResult.builder()
                .summaryExists(true)
                .stepCount(entities.size())
                .build();
    }

    @Override
    @Transactional
    public RecipeResponseDTO.CookingRecordCreateResponseDTO createCookingRecord(
            Long memberId,
            Long recipeId,
            int rating
    ) {
        if (rating < 1 || rating > 5) {
            throw new BusinessException(ErrorStatus.INVALID_RATING);
        }

        // score, 레벨업 판단을 위해 Member 로드 (member lock)
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));

        int oldScore = member.getScore() == null ? 0 : member.getScore();

        // 레시피 lock + 난이도 갱신
        Recipe recipe = recipeRepository.findByIdForUpdate(recipeId);
        if (recipe == null) throw new BusinessException(ErrorStatus.RECIPE_NOT_FOUND);
        recipe.addRating(rating);

        // CookingRecord 저장
        CookingRecord record = cookingRecordRepository.save(
                CookingRecord.builder()
                        .member(member)
                        .recipe(recipe)
                        .rating(rating)
                        .build()
        );

        // 오늘 첫 요리 여부 판단 (점수 계산용)
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();

        long todayCookingCount = cookingRecordRepository
                .countByMember_IdAndCreatedAtBetween(memberId, startOfToday, startOfTomorrow);

        boolean isFirstCookingToday = (todayCookingCount == 1);


        // 이번 요청에서 얻는 점수 합산
        int rewardScore = 0;

        // 1. 요리 기록 점수: 정책표 기준 +5
        rewardScore += 5;

        // 2. 오늘 미션 매칭 시 완료 보상(예: +20)
        Long dishId = recipe.getDish() != null ? recipe.getDish().getId() : null;
        rewardScore += completeTodayMissionIfMatched(memberId, dishId);

        if (isFirstCookingToday) {
            //  재료 보너스(하루 1회)
            rewardScore += calculateIngredientBonus(memberId, recipeId, today);

            // 연속 요리 보너스(하루 1회)
            rewardScore += calculateStreakBonus(memberId, today);
        }

        // 점수 반영 + 레벨업 판정
        member.addScore(rewardScore);
        int newScore = member.getScore();
        boolean leveledUp = levelService.shouldLevelUp(oldScore, newScore);

        // 재료 차감 (점수 계산 이후)
        deductMemberIngredients(memberId, recipeId);

        return RecipeResponseDTO.CookingRecordCreateResponseDTO.builder()
                .cookingRecordId(record.getId())
                .recipeId(recipe.getId())
                .rating(rating)
                .recipeLevel(recipe.getLevel())
                .ratingCount(recipe.getRatingCount())
                .rewardScore(rewardScore)
                .leveledUp(leveledUp)
                .build();
    }

    @Override
    @Transactional
    public RecipeResponseDTO.BookmarkToggleResult toggleBookmark(Long memberId, Long recipeId) {
        // 1. 삭제 시도 (Bulk Delete)
        int deletedCount = memberRecipeRepository.deleteByMemberIdAndRecipeId(memberId, recipeId);

        if (deletedCount > 0) {
            return RecipeConverter.toBookmarkToggleResult(false);
        }

        // 2. 삭제 실패 = 북마크 추가 필요
        // 레시피 존재 확인 및 조회 (한 번만)
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.RECIPE_NOT_FOUND));

        Member member = memberRepository.getReferenceById(memberId);

        memberRecipeRepository.save(
                MemberRecipe.builder()
                        .member(member)
                        .recipe(recipe)
                        .build()
        );

        return RecipeConverter.toBookmarkToggleResult(true);
    }

    private Recipe saveRecipe(RecipeCrawler.RecipeData data) {
        Recipe recipe = recipeConverter.toEntity(data);

        try {
            return recipeRepository.save(recipe);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorStatus.RECIPE_ALREADY_EXISTS);
        }
    }

    private void saveRecipeIngredients(Recipe recipe, List<RecipeCrawler.IngredientData> ingredientDataList) {
        //모든 재료명 추출
        List<String> ingredientNames = ingredientDataList.stream()
                .map(RecipeCrawler.IngredientData::name)
                .toList();

        //한 번에 조회
        List<Ingredient> foundIngredients = ingredientRepository.findAllByNameIn(ingredientNames);

        Map<String, Ingredient> ingredientMap = foundIngredients.stream()
                .collect(Collectors.toMap(Ingredient::getName, Function.identity()));


        List<RecipeIngredient> recipeIngredients = ingredientDataList.stream()
                .map(data -> {
                    Ingredient ingredient = ingredientMap.get(data.name());
                    if (ingredient == null) {
                        log.warn("재료 매칭 실패 - DB에 없음: {}", data.name());
                        return null;
                    }
                    return recipeIngredientConverter.toEntity(recipe, ingredient, data);
                })
                .filter(Objects::nonNull)
                .toList();

        if (!recipeIngredients.isEmpty()) {
            recipeIngredientRepository.saveAll(recipeIngredients);
        }
    }

    private Summary createSummaryWithRaceConditionHandling(Recipe recipe) {
        try {
            return summaryRepository.save(
                    Summary.builder()
                            .recipe(recipe)
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            // 동시성으로 인해 이미 생성된 경우
            log.info("Summary 동시 생성 감지 - recipeId: {}", recipe.getId());
            return summaryRepository.findByRecipeId(recipe.getId())
                    .orElseThrow(() -> new BusinessException(ErrorStatus.SUMMARY_CREATION_FAILED));
        }
    }

    /**
     * Gemini API를 통해 레시피 단계 생성
     */
    private List<GeminiResponseConverter.StepDraft> generateStepsFromGemini(String recipeTitle, List<TranscriptSegment> transcripts) {
        String prompt = geminiPromptBuilder.buildRecipeStepPrompt(recipeTitle, transcripts);
        String responseBody = callGeminiApi(prompt);
        return geminiResponseConverter.convertToStepDrafts(responseBody, MAX_RECIPE_STEPS);
    }

    /**
     * Gemini API 호출 (에러 처리 포함)
     */
    private String callGeminiApi(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", prompt)
                                )
                        )
                )
        );

        try {
            return geminiSummaryWebClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response -> {
                                log.error("Gemini API 클라이언트 에러 - Status: {}", response.statusCode());
                                return Mono.error(new BusinessException(ErrorStatus.GEMINI_BAD_REQUEST));
                            }
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            response -> {
                                log.error("Gemini API 서버 에러 - Status: {}", response.statusCode());
                                return Mono.error(new BusinessException(ErrorStatus.GEMINI_SERVER_ERROR));
                            }
                    )
                    .bodyToMono(String.class)
                    .timeout(GEMINI_API_TIMEOUT)
                    .block();

        } catch (WebClientException e) {
            log.error("Gemini API 호출 실패 - WebClient 예외", e);
            throw new BusinessException(ErrorStatus.EXTERNAL_API_ERROR);
        } catch (Exception e) {
            log.error("Gemini API 예상치 못한 오류", e);
            throw new BusinessException(ErrorStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private int completeTodayMissionIfMatched(Long memberId, Long dishId) {
        LocalDate today = LocalDate.now();

        MemberMission mm = memberMissionRepository
                .findByMemberIdAndAssignedDateForUpdate(memberId, today)
                .orElse(null);

        if (mm == null) return 0;
        if (mm.getStatus() != MissionStatus.ASSIGNED) return 0;

        Long missionDishId = mm.getMission().getDishId();
        if (missionDishId == null || dishId == null) return 0;

        if (!missionDishId.equals(dishId)) return 0;

        mm.completeToday();

        Integer reward = mm.getMission().getReward();
        return (reward == null) ? 0 : reward;
    }

    private int calculateIngredientBonus(Long memberId, Long recipeId, LocalDate today) {
        List<Long> ingredientIds = recipeIngredientRepository.findIngredientIdsByRecipeId(recipeId);
        if (ingredientIds.isEmpty()) return 0;

        List<MemberIngredient> owned = memberIngredientRepository
                .findAllByMemberIdAndIngredientIdIn(memberId, ingredientIds);

        if (owned.isEmpty()) return 0;

        LocalDate d3 = today.plusDays(3);

        boolean hasD3 = owned.stream()
                .map(MemberIngredient::getExpireDate)
                .filter(Objects::nonNull)
                .anyMatch(exp -> !exp.isBefore(today) && !exp.isAfter(d3)); // today ~ today+3

        return hasD3 ? 10 : 5;
    }

    private int calculateStreakBonus(Long memberId, LocalDate today) {
        // 최근 7일만 보면 충분 (7일 보너스까지만)
        LocalDateTime start = today.minusDays(7).atStartOfDay();
        LocalDateTime end = today.minusDays(1).atTime(23,59,59);

        List<LocalDate> cookedDates = cookingRecordRepository
                .findDistinctCookingDatesBetween(memberId, start, end)
                .stream()
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .toList();
        
        Set<LocalDate> set = new HashSet<>(cookedDates);

        int streakBeforeToday = 0;
        LocalDate d = today.minusDays(1);
        while (set.contains(d)) {
            streakBeforeToday++;
            d = d.minusDays(1);
            if (streakBeforeToday >= 6) break; // 최대 6까지만 필요(오늘 포함 7)
        }

        int streakIncludingToday = streakBeforeToday + 1;

        if (streakIncludingToday >= 7) return 25;
        if (streakIncludingToday >= 3) return 10;
        return 5; // 1일차(오늘 첫 요리)
    }

    /**
     * 요리 완료 시 사용자 재료 차감
     * - 레시피 재료 중 사용자가 보유한 재료만 차감
     * - 차감 후 0 이하면 삭제
     */
    private void deductMemberIngredients(Long memberId, Long recipeId) {
        // 1. 레시피 재료 목록 조회 (ingredient_id, amount)
        List<RecipeIngredient> recipeIngredients = recipeIngredientRepository.findAllByRecipeId(recipeId);

        if (recipeIngredients.isEmpty()) {
            return;
        }

        // 2. 재료 ID 목록 추출
        List<Long> ingredientIds = recipeIngredients.stream()
                .map(ri -> ri.getIngredient().getId())
                .toList();

        // 3. 사용자가 보유한 재료 중 매칭되는 것 조회
        List<MemberIngredient> memberIngredients = memberIngredientRepository
                .findAllByMemberIdAndIngredientIdIn(memberId, ingredientIds);

        if (memberIngredients.isEmpty()) {
            return;
        }

        // 4. ingredient_id -> RecipeIngredient.amount 매핑
        Map<Long, Double> recipeAmountMap = recipeIngredients.stream()
                .collect(Collectors.toMap(
                        ri -> ri.getIngredient().getId(),
                        ri -> ri.getAmount() != null ? ri.getAmount() : 0.0
                ));

        // 5. 차감 및 삭제 대상 분리
        List<MemberIngredient> toDelete = new ArrayList<>();

        for (MemberIngredient mi : memberIngredients) {
            Long ingredientId = mi.getIngredient().getId();
            Double amountToDeduct = recipeAmountMap.get(ingredientId);

            if (amountToDeduct != null && amountToDeduct > 0) {
                mi.subtractWeight(amountToDeduct);

                if (mi.isEmpty()) {
                    toDelete.add(mi);
                }
            }
        }

        // 6. 소진된 재료 삭제
        if (!toDelete.isEmpty()) {
            memberIngredientRepository.deleteAll(toDelete);
        }

        log.debug("재료 차감 완료 - memberId: {}, recipeId: {}, 차감: {}, 삭제: {}",
                memberId, recipeId, memberIngredients.size(), toDelete.size());
    }

}