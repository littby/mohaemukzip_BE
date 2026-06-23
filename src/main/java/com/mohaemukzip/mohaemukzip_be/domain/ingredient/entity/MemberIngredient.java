package com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity;

import com.mohaemukzip.mohaemukzip_be.domain.ingredient.entity.enums.StorageType;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Table(
        name = "member_ingredients",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_member_ingredient",
                        columnNames = {"member_id", "ingredient_id"}
                )
        }
)
public class MemberIngredient extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_ingredient_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "expire_date")
    private LocalDate expireDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type")
    private StorageType storageType;

    @Column(name = "weight")
    private Double weight;

    /**
     * 재료 사용량 차감
     */
    public void subtractWeight(Double amount) {
        if (amount == null || amount <= 0) return;
        if (this.weight == null) {
            this.weight = 0.0;
            return;
        }
        this.weight = this.weight - amount;
    }

    /**
     * 재료가 소진되었는지 확인 (0 이하)
     */
    public boolean isEmpty() {
        return this.weight == null || this.weight <= 0;
    }

    // 기존 냉장고 재료 갱신
    public void addQuantityAndRenewExpireDate(Double additionalWeight, LocalDate newExpireDate) {
        if (additionalWeight == null || additionalWeight <= 0) return;
        if (this.weight == null) this.weight = 0.0;
        this.weight += additionalWeight; // 무게 갱신
        this.expireDate = newExpireDate; // 날짜 갱신

        if (newExpireDate != null) {
            this.expireDate = newExpireDate;
        }
    }

    // 재료 수정
    public void update(StorageType storageType, LocalDate expireDate, Double weight) {
        this.storageType = storageType;
        this.expireDate = expireDate;
        this.weight = weight;
    }
}
