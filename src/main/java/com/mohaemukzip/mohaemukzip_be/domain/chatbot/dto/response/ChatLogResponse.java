package com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

public class ChatLogResponse {

    @Getter
    @Builder
    @Schema(description = "챗봇 대화 로그 페이지 응답")
    public static class PageResponse {
        private List<Detail> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;

        public static PageResponse from(Page<Detail> page) {
            return PageResponse.builder()
                    .content(page.getContent())
                    .page(page.getNumber())
                    .size(page.getSize())
                    .totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .first(page.isFirst())
                    .last(page.isLast())
                    .build();
        }
    }

    @Getter
    @Builder
    @Schema(description = "챗봇 대화 로그 상세")
    public static class Detail {
        @Schema(description = "로그 ID")
        private Long id;

        @Schema(description = "회원 ID")
        private Long memberId;

        @Schema(description = "대화 세션 ID")
        private String sessionId;

        @Schema(description = "사용자 질문 원문")
        private String userMessage;

        @Schema(description = "봇 응답 제목")
        private String botTitle;

        @Schema(description = "봇 응답 본문")
        private String botMessage;

        @Schema(description = "함께 추천된 레시피 ID 목록")
        private List<Long> recommendedRecipeIds;

        @Schema(description = "생성 시각")
        private LocalDateTime createdAt;
    }
}
