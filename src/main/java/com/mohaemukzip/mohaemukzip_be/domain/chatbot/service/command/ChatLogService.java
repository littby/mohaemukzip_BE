package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.command;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity.ChatLog;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.repository.ChatLogRepository;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 회원별 챗봇 질문/답변을 DB에 비동기로 영구 기록한다 (관리자 모니터링/분석용).
 * 이 로그는 사용자에게 노출되지 않는 부가 기록이므로, 저장이 실패해도 챗봇 응답 자체에는
 * 영향을 주지 않도록 예외를 흡수하고 로그만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatLogService {

    private final ChatLogRepository chatLogRepository;
    private final MemberRepository memberRepository;
    private final TransactionTemplate transactionTemplate;

    @Async
    public void saveChatLog(Long memberId, String userMessage,
                             String botTitle, String botMessage, List<Long> recommendedRecipeIds) {
        try {
            // 탈퇴와 같은 회원 잠금을 사용한다. 외부 AI 호출은 이 트랜잭션에 포함하지 않는다.
            transactionTemplate.executeWithoutResult(status -> {
                Member member = memberRepository.findByIdForUpdate(memberId).orElse(null);
                if (member == null || member.isInactive()) {
                    return;
                }

                ChatLog chatLog = ChatLog.builder()
                        .member(member)
                        .userMessage(userMessage)
                        .botTitle(botTitle)
                        .botMessage(botMessage)
                        .recommendedRecipeIds(recommendedRecipeIds != null ? new HashSet<>(recommendedRecipeIds) : Set.of())
                        .build();
                chatLogRepository.save(chatLog);
            });
        } catch (Exception e) {
            // execute가 커밋까지 완료한 뒤 반환하므로 커밋 시점의 실패도 여기서 처리한다.
            log.error("[챗봇 로그] 저장 실패 - memberId: {}", memberId, e);
        }
    }
}
