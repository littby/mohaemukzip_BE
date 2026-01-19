package com.mohaemukzip.mohaemukzip_be.domain.ingredient.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum IngredientStatus {

    NORMAL("정상", "GREEN"),
    IMMINENT("임박", "ORANGE"),
    EXPIRED("만료", "RED");

    private final String description;
    private final String color;
}
