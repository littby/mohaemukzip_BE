package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.command;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity.ChatLog;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.repository.ChatLogRepository;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatLogServiceTest {

    @Mock
    private ChatLogRepository chatLogRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ChatLogService chatLogService;

    private static final Long MEMBER_ID = 1L;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        chatLogService = new ChatLogService(chatLogRepository, memberRepository,
                new TransactionTemplate(transactionManager));
    }

    @Test
    @DisplayName("정상적으로 ChatLog를 회원/질문/답변/추천 레시피ID와 함께 저장한다")
    void savesChatLogWithAllFields() {
        Member member = Member.builder().id(MEMBER_ID).build();
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        chatLogService.saveChatLog(MEMBER_ID, "질문", "제목", "답변", List.of(10L, 20L));

        ArgumentCaptor<ChatLog> captor = ArgumentCaptor.forClass(ChatLog.class);
        verify(chatLogRepository).save(captor.capture());

        ChatLog saved = captor.getValue();
        assertThat(saved.getMember()).isEqualTo(member);
        assertThat(saved.getUserMessage()).isEqualTo("질문");
        assertThat(saved.getBotTitle()).isEqualTo("제목");
        assertThat(saved.getBotMessage()).isEqualTo("답변");
        assertThat(saved.getRecommendedRecipeIds()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    @DisplayName("저장 중 예외가 발생해도 호출자에게 전파하지 않는다 (부가 기록이므로 챗봇 응답에 영향 없음)")
    void doesNotPropagateExceptionOnSaveFailure() {
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenThrow(new RuntimeException("DB 장애"));

        assertThatCode(() ->
                chatLogService.saveChatLog(MEMBER_ID, "질문", "제목", "답변", List.of())
        ).doesNotThrowAnyException();

        verify(chatLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 삭제된 회원의 비동기 대화 로그는 새로 저장하지 않는다")
    void skipsDeletedMember() {
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.empty());

        chatLogService.saveChatLog(MEMBER_ID, "질문", "제목", "답변", List.of());

        verifyNoInteractions(chatLogRepository);
    }

    @Test
    @DisplayName("비활성 회원의 대화 로그는 새로 저장하지 않는다")
    void skipsInactiveMember() {
        Member member = Member.builder().id(MEMBER_ID).inactiveDate(LocalDate.now()).build();
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        chatLogService.saveChatLog(MEMBER_ID, "질문", "제목", "답변", List.of());

        verifyNoInteractions(chatLogRepository);
    }

    @Test
    @DisplayName("로그 INSERT 실패는 트랜잭션을 롤백하고 호출자에게 전파하지 않는다")
    void rollsBackSaveFailure() {
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(Member.builder().id(MEMBER_ID).build()));
        when(chatLogRepository.save(any())).thenThrow(new RuntimeException("INSERT 실패"));

        assertThatCode(() -> chatLogService.saveChatLog(MEMBER_ID, "질문", "제목", "답변", null))
                .doesNotThrowAnyException();

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    @DisplayName("트랜잭션 커밋 시점의 실패도 호출자에게 전파하지 않는다")
    void doesNotPropagateCommitFailure() {
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(Member.builder().id(MEMBER_ID).build()));
        doThrow(new RuntimeException("커밋 실패")).when(transactionManager).commit(any());

        assertThatCode(() -> chatLogService.saveChatLog(MEMBER_ID, "질문", "제목", "답변", List.of()))
                .doesNotThrowAnyException();

        verify(chatLogRepository).save(any());
        verify(transactionManager).commit(any());
    }
}
