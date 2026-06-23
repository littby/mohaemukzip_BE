package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.processor;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.RedisChatMessage;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response.ChatProcessorResult;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BasicChatProcessor implements ChatProcessor {

    private final RecipeRepository recipeRepository;

    @Override
    public ChatProcessorResult process(Long memberId, String userMessage, List<RedisChatMessage> history) {
        // BasicChatProcessor는 단순 에코 또는 기본 응답만 수행
        
        String message = "안녕하세요! 기본 챗봇입니다. (RecommendChatProcessor가 활성화되어야 합니다)";
        
        return ChatProcessorResult.builder()
                .title("기본 응답")
                .message(message)
                .recipes(Collections.emptyList())
                .build();
    }
}
