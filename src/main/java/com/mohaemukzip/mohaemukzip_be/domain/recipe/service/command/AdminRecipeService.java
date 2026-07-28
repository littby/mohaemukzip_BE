package com.mohaemukzip.mohaemukzip_be.domain.recipe.service.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.service.command.RecipeAdminFacade;
import com.mohaemukzip.mohaemukzip_be.global.client.DiscordNotificationClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRecipeService {

    private final RecipeAdminFacade recipeAdminFacade;
    private final DiscordNotificationClient discordNotificationClient;

    @Async
    public void processBulkRecipesAsync(Long dishId, List<String> videoIds) {
        log.info("[관리자] 대량 레시피 비동기 등록 시작 - 요리ID: {}, 총 {}건", dishId, videoIds.size());

        int successCount = 0;
        int failCount = 0;
        List<String> errorDetails = new java.util.ArrayList<>();

        for (String videoId : videoIds) {
            try {
                Long recipeId = recipeAdminFacade.saveRecipeByVideoId(dishId, videoId);
                recipeAdminFacade.createSummary(recipeId);

                successCount++;
                log.info("[관리자] 레시피 및 요약 저장 성공 - videoId: {}", videoId);
            } catch (Exception e) {
                failCount++;
                String errorMsg = String.format("`%s`: %s", videoId, e.getMessage());
                errorDetails.add(errorMsg);
                log.error("[관리자] 레시피 처리 실패 - videoId: {}, 사유: {}", videoId, e.getMessage());
            }
        }

        StringBuilder finalMessage = new StringBuilder();
        finalMessage.append(String.format("🍳 **대량 레시피 등록 완료**\n- 성공: %d건\n- 실패: %d건",
                                            successCount, failCount));

        if (!errorDetails.isEmpty()) {
            finalMessage.append("\n\n⚠️ **실패 상세 내역**\n");
            for (String err : errorDetails) {
                finalMessage.append("- ").append(err).append("\n");
            }
        }

        log.info("[관리자] 대량 레시피 비동기 등록 완료 - 성공: {}건, 실패: {}건", successCount, failCount);

        discordNotificationClient.sendNotification(finalMessage.toString());
    }
}
