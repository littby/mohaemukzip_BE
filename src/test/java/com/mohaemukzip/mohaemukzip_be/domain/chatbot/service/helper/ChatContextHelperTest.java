package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.helper;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.RedisChatMessage;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.request.GeminiRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity.enums.SenderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatContextHelperTest {

    private final ChatContextHelper chatContextHelper = new ChatContextHelper();

    private RedisChatMessage userMessage(String content) {
        return RedisChatMessage.builder().sender(SenderType.USER).content(content).build();
    }

    private RedisChatMessage botMessage(String title, String content) {
        return RedisChatMessage.builder().sender(SenderType.BOT).title(title).content(content).build();
    }

    @Test
    @DisplayName("history가 null이면 빈 리스트를 반환한다")
    void nullHistoryReturnsEmptyList() {
        List<GeminiRequestDTO.Content> result = chatContextHelper.buildHistoryContents(null, 12);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("history가 비어있으면 빈 리스트를 반환한다")
    void emptyHistoryReturnsEmptyList() {
        List<GeminiRequestDTO.Content> result = chatContextHelper.buildHistoryContents(new ArrayList<>(), 12);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("maxTurns가 0이면 빈 리스트를 반환한다")
    void zeroMaxTurnsReturnsEmptyList() {
        List<RedisChatMessage> history = List.of(userMessage("안녕"));

        List<GeminiRequestDTO.Content> result = chatContextHelper.buildHistoryContents(history, 0);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("maxTurns가 음수이면 빈 리스트를 반환한다")
    void negativeMaxTurnsReturnsEmptyList() {
        List<RedisChatMessage> history = List.of(userMessage("안녕"));

        List<GeminiRequestDTO.Content> result = chatContextHelper.buildHistoryContents(history, -1);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("maxTurns가 history 크기보다 크면 전체 history를 사용한다")
    void maxTurnsLargerThanHistoryUsesFullHistory() {
        List<RedisChatMessage> history = List.of(userMessage("첫 질문"), botMessage("제목", "답변"));

        List<GeminiRequestDTO.Content> result = chatContextHelper.buildHistoryContents(history, 12);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRole()).isEqualTo("user");
        assertThat(result.get(0).getParts().get(0).getText()).isEqualTo("첫 질문");
        assertThat(result.get(1).getRole()).isEqualTo("model");
        assertThat(result.get(1).getParts().get(0).getText()).isEqualTo("제목 ||| 답변");
    }

    @Test
    @DisplayName("history가 maxTurns보다 많으면 가장 최근 maxTurns개만 사용한다")
    void limitsToRecentMaxTurns() {
        List<RedisChatMessage> history = List.of(
                userMessage("오래된 질문"),
                botMessage("오래된 제목", "오래된 답변"),
                userMessage("최근 질문")
        );

        List<GeminiRequestDTO.Content> result = chatContextHelper.buildHistoryContents(history, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getParts().get(0).getText()).isEqualTo("최근 질문");
    }
}
