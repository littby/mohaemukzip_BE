package com.mohaemukzip.mohaemukzip_be.domain.recipe.builder;

import com.mohaemukzip.mohaemukzip_be.global.client.transcript.dto.TranscriptSegment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeminiPromptBuilder {

    public String buildRecipeStepPrompt(String recipeTitle, List<TranscriptSegment> transcripts) {
        StringBuilder transcriptText = new StringBuilder();
        for (TranscriptSegment segment : transcripts) {
            if (segment.offset() != null && segment.text() != null) {
                int timeInSeconds = segment.offset().intValue();
                transcriptText.append(String.format("[%d초] %s\n", timeInSeconds, segment.text()));
            }
        }

        return String.format("""
    너는 요리 레시피 요약 어시스턴트다.
    
    레시피 제목: %s
    
    다음은 유튜브 영상의 자막과 해당 자막이 나오는 시간(초 단위)이다:
    %s
    
    자막을 기반으로 '요약 레시피 STEP 목록'을 만들어라.
    
    규칙:
    - steps는 시간 순으로 정렬
    - stepNumber는 1부터 연속 정수
    - 각 step은 title(짧고 행동 중심), description(자막 기반 요약) 포함
    - videoTime은 해당 step이 시작되는 정확한 시점을 '초' 단위 정수로 넣어라.
      반드시 제공된 자막의 '[N초]' 시간 정보를 바탕으로 가장 적절한 시작 시간을 판단해서 넣어라.
      정말 판단 불가능하면 null.
    - JSON만 출력(마크다운/코드블럭/설명 금지)
    - step은 최대 10개까지만 생성하라. 자막이 길더라도 가장 중요한 단계 10개만 선택하라.
    
    출력 스키마:
    {
      "steps": [
        {"stepNumber": 1, "title": "고기와 기본 재료 준비하기", "description": "…", "videoTime": 304}
      ]
    }
    """, recipeTitle, transcriptText.toString());
    }
}
