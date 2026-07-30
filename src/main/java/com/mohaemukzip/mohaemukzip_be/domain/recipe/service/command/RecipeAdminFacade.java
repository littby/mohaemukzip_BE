package com.mohaemukzip.mohaemukzip_be.domain.recipe.service.command;

import com.mohaemukzip.mohaemukzip_be.domain.recipe.dto.RecipeResponseDTO;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.Recipe;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.Summary;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.builder.GeminiPromptBuilder;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.converter.GeminiResponseConverter;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.RecipeRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.service.crawler.RecipeCrawler;
import com.mohaemukzip.mohaemukzip_be.global.client.transcript.TranscriptClient;
import com.mohaemukzip.mohaemukzip_be.global.client.transcript.dto.TranscriptSegment;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeAdminFacade {

    private static final int MAX_RECIPE_STEPS = 10;
    private static final Duration GEMINI_API_TIMEOUT = Duration.ofSeconds(30);

    private final RecipeCommandService recipeCommandService;
    private final RecipeRepository recipeRepository;
    private final RecipeCrawler recipeCrawler;
    private final TranscriptClient transcriptClient;
    private final GeminiPromptBuilder geminiPromptBuilder;
    private final GeminiResponseConverter geminiResponseConverter;

    @Qualifier("geminiSummaryWebClient")
    private final WebClient geminiSummaryWebClient;

    /**
     * 외부 API(크롤러) 통신 후 DB 트랜잭션 서비스 호출
     */
    public Long saveRecipeByVideoId(Long dishId, String videoId) {
        log.info("[Facade] 유튜브 레시피 등록 시작 - videoId: {}", videoId);

        // 1. 필요한 재료 이름만 DB에서 조회 (빠름)
        List<String> ingredientNames = recipeCommandService.getAllIngredientNames();

        // 2. 외부 크롤링 실행 (느림)
        RecipeCrawler.RecipeData data = recipeCrawler.crawlRecipe(videoId, ingredientNames);

        // 3. DB 저장 로직 수행 (트랜잭션 위임)
        return recipeCommandService.saveRecipeAndIngredients(dishId, videoId, data);
    }

    /**
     * 동시성 제어 및 외부 API(Gemini, 자막) 통신 후 DB 트랜잭션 서비스 호출
     */
    public RecipeResponseDTO.SummaryCreateResult createSummary(Long recipeId) {
        log.info("[Facade] 요약 생성 시작 - recipeId: {}", recipeId);

        // 1. DB 동시성 제어: 빈 Summary 선점 시도
        Summary summary = recipeCommandService.tryCreateSummary(recipeId);

        // 이미 생성 중이거나 완료된 경우
        if (summary == null) {
            log.info("[Facade] 요약 이미 존재함 - recipeId: {}", recipeId);
            return recipeCommandService.getExistingSummaryResult(recipeId);
        }

        try {
            // 2. 외부 API 호출 (느림, 트랜잭션 범위 밖)
            Recipe recipe = recipeCommandService.getRecipeForSummary(recipeId);
            List<TranscriptSegment> transcripts = transcriptClient.fetchTranscript(recipe.getVideoId());

            String prompt = geminiPromptBuilder.buildRecipeStepPrompt(recipe.getTitle(), transcripts);
            String responseBody = callGeminiApi(prompt);
            List<GeminiResponseConverter.StepDraft> steps = geminiResponseConverter.convertToStepDrafts(responseBody, MAX_RECIPE_STEPS);

            // 3. 추출된 스텝들 DB 저장 (트랜잭션 위임)
            return recipeCommandService.saveSummarySteps(summary.getId(), steps);

        } catch (Exception e) {
            log.error("[Facade] 요약 생성 중 외부 API 통신 실패 - recipeId: {}, 오류: {}", recipeId, e.getMessage());
            // 실패 시 선점한 Summary를 지울 것인지 고민해 볼 수 있으나, 
            // 현재는 Exception이 발생하면 빈 Summary만 남게 되어 추후 재시도를 위해 삭제하는 로직이 필요할 수 있습니다.
            // 일단 기존 설계의 안정성을 위해 던집니다.
            recipeCommandService.deleteSummary(summary.getId());
            throw e;
        }
    }

    /**
     * Summary가 하나도 생성되지 않은(=시도조차 안 됐거나 실패해서 정리된) 레시피 목록을 조회한다.
     * dishId를 주면 해당 요리로만 좁혀서 조회한다.
     */
    public List<RecipeResponseDTO.MissingSummaryItem> getRecipesWithoutSummary(Long dishId) {
        return recipeRepository.findAllWithoutSummary(dishId).stream()
                .map(recipe -> RecipeResponseDTO.MissingSummaryItem.builder()
                        .recipeId(recipe.getId())
                        .title(recipe.getTitle())
                        .dishId(recipe.getDish() != null ? recipe.getDish().getId() : null)
                        .videoId(recipe.getVideoId())
                        .build())
                .collect(Collectors.toList());
    }

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

        } catch (BusinessException e) {
            throw e; // 의도적으로 발생시킨 도메인 예외는 그대로 패스
        } catch (WebClientException e) {
            log.error("Gemini API 호출 실패 - WebClient 예외", e);
            throw new BusinessException(ErrorStatus.EXTERNAL_API_ERROR);
        } catch (Exception e) {
            log.error("Gemini API 예상치 못한 오류", e);
            throw new BusinessException(ErrorStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
