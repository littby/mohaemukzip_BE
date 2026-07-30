package com.mohaemukzip.mohaemukzip_be.domain.chatbot.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatProcessorResult {
    private String title;
    private String message;
    private List<RecipeCardResponse> recipeCards;
}

