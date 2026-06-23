package com.mohaemukzip.mohaemukzip_be.domain.member.entity;

import com.mohaemukzip.mohaemukzip_be.domain.member.entity.enums.LoginType;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.enums.Role;
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
        name = "members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_login_id",
                        columnNames = {"login_id"}
                ),
                @UniqueConstraint(
                        name = "uk_provider_oauth_id",
                        columnNames = {"login_type", "oauth_id"}
                )
        }
)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nickname")
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "inactive_date")
    private LocalDate inactiveDate;

    @Column(name = "login_id", unique = true)
    private String loginId;

    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_type", nullable = false)
    private LoginType loginType;

    @Column(name = "oauth_id")
    private String oauthId;

    @Column(name = "score")
    private Integer score;

    @Column(name = "profile_image_key", length = 500)
    private String profileImageKey;

    @Column(name = "fridge_score")
    @Builder.Default
    private Integer fridgeScore = 100;

    public void deactivate() {
        if (this.inactiveDate == null) {
            this.inactiveDate = LocalDate.now();
        }
    }
    public void updateProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }

    public void reactivate() {
        this.inactiveDate = null;
    }

    public boolean isInactive() { return this.inactiveDate != null; }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void addScore(int reward) {
        if (this.score == null) this.score = 0;
        if (reward <= 0) return;
        this.score += reward;
    }

    public void updateFridgeScore(int delta) {
        if (this.fridgeScore == null) this.fridgeScore = 100;
        this.fridgeScore = Math.max(0, Math.min(100, this.fridgeScore + delta));
    }
}
