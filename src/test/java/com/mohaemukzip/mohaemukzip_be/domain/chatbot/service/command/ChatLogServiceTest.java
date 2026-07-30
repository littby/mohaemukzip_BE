package com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.command;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity.ChatLog;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.repository.ChatLogRepository;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatLogServiceTest {

    @Mock
    private ChatLogRepository chatLogRepository;

    @Mock
    private MemberRepository memberRepository;

    private ChatLogService chatLogService;

    private static final Long MEMBER_ID = 1L;

    @Test
    @DisplayName("정상적으로 ChatLog를 회원/질문/답변/추천 레시피ID와 함께 저장한다")
    void savesChatLogWithAllFields() {
        chatLogService = new ChatLogService(chatLogRepository, memberRepository);
        Member member = Member.builder().id(MEMBER_ID).build();
        when(memberRepository.getReferenceById(MEMBER_ID)).thenReturn(member);

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
        chatLogService = new ChatLogService(chatLogRepository, memberRepository);
        when(memberRepository.getReferenceById(MEMBER_ID)).thenThrow(new RuntimeException("DB 장애"));

        assertThatCode(() ->
                chatLogService.saveChatLog(MEMBER_ID, "질문", "제목", "답변", List.of())
        ).doesNotThrowAnyException();

        verify(chatLogRepository, never()).save(any());
    }
}
