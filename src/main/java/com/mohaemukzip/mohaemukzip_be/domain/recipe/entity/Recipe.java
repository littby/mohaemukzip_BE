package com.mohaemukzip.mohaemukzip_be.domain.recipe.entity;

import com.mohaemukzip.mohaemukzip_be.domain.recipe.entity.enums.Category;
import com.mohaemukzip.mohaemukzip_be.global.entity.BaseEntity;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Table(name = "recipes")
public class Recipe extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipe_id")
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "level", nullable = false)
    @Builder.Default
    private Double level = 0.0;

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private Integer ratingCount = 0;

    @Column(name = "time")
    private String time; // "10:54" (영상 길이)

    @Column(name = "cooking_time")
    private Integer cookingTime;  // 15 (조리 시간, 분 단위)

    @Column(name = "channel")
    private String channel;

    @Column(name = "channel_id")
    private String channelId;

    @Column(name = "views")
    private Long views;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private Category category;

    @Column(name = "video_id", nullable = false, unique = true)
    private String videoId;

    @Column(name = "video_url", nullable = false)
    private String videoUrl;

    @Column(name = "channel_profile_image_url",  nullable = false)
    private String channelProfileImageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dish_id")
    private Dish dish;

    public void addRating(int newRating) {
        if (newRating < 1 || newRating > 5) {
            throw new BusinessException(ErrorStatus.INVALID_RATING_VALUE);
        }

        if ( this.ratingCount == null || this.ratingCount == 0) {
            this.level = (double) newRating;
            this.ratingCount = 1;
            return;
        }

        double total = this.level * this.ratingCount;
        this.ratingCount += 1;
        this.level = (total + newRating) / this.ratingCount;
    }

}
