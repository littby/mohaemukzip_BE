package com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GeminiRequestDTO {
    
    private SystemInstruction systemInstruction;
    private List<Content> contents;

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
}
