package com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity;

import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

/**
 * 회원별 챗봇 질문/답변을 영구 기록하는 로그.
 * Redis(chat:room:{memberId}:messages)는 Gemini 프롬프트에 넣을 실시간 대화 컨텍스트용으로
 * 30분 TTL이 지나면 사라지는 반면, 이 테이블은 모니터링/분석을 위해 한 번 쓰고 다시 읽지 않는
 * 별도의 영속 기록이다.
 */
@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Table(name = "chat_logs")
public class ChatLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "user_message", columnDefinition = "TEXT", nullable = false)
    private String userMessage;

    @Column(name = "bot_title")
    private String botTitle;

    @Column(name = "bot_message", columnDefinition = "TEXT")
    private String botMessage;

    // 추천 순서는 의미가 없으므로(Gemini가 고른 순서를 보존할 필요 없음) 순서를 보장하지 않는 Set으로 관리한다.
    @ElementCollection
    @CollectionTable(name = "chat_log_recipe_ids", joinColumns = @JoinColumn(name = "chat_log_id"))
    @Column(name = "recipe_id")
    @Builder.Default
    private Set<Long> recommendedRecipeIds = Set.of();
}
