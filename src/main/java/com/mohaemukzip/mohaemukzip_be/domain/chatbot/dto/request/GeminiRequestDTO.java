package com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class GeminiRequestDTO {

    private SystemInstruction systemInstruction;
    private List<Content> contents;
    private GenerationConfig generationConfig;

    @Getter
    @Builder
    public static class SystemInstruction {
        private List<Part> parts;
    }

    @Getter
    @Builder
    public static class Content {
        private String role; // 추가: user 또는 model
        private List<Part> parts;
    }

    @Getter
    @Builder
    public static class Part {
        private String text;
    }

    /**
     * Gemini structured output 설정.
     * responseSchema를 지정하면 모델이 스키마를 벗어난 텍스트(마크다운 코드블록, 설명 문구 등)를
     * 절대 반환하지 않도록 API 레벨에서 강제한다.
     */
    @Getter
    @Builder
    public static class GenerationConfig {
        @JsonProperty("responseMimeType")
        private String responseMimeType;
        @JsonProperty("responseSchema")
        private Map<String, Object> responseSchema;
    }
}
