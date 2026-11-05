package com.mohaemukzip.mohaemukzip_be.domain.member.service.command.auth;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.controller.AdminChatController;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.entity.ChatLog;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.repository.ChatLogRepository;
import com.mohaemukzip.mohaemukzip_be.domain.chatbot.service.command.ChatLogService;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.*;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.enums.LoginType;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.enums.Role;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.MemberRepository;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.MemberTermRepository;
import com.mohaemukzip.mohaemukzip_be.domain.mission.repository.MemberMissionRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.CookingRecordRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.MemberRecipeRepository;
import com.mohaemukzip.mohaemukzip_be.global.exception.GlobalExceptionHandler;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(classes = MemberWithdrawalIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.name=member-withdrawal-isolated",
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:member_withdrawal;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "logging.level.root=ERROR"
})
class MemberWithdrawalIntegrationTest {
    @Autowired private AuthCommandService auth;
    @Autowired private ChatLogService chatLogService;
    @Autowired private MemberRepository members;
    @Autowired private ChatLogRepository logs;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private RedisTemplate<String, String> redis;

    @BeforeEach
    void resetData() {
        jdbc.execute("drop table if exists withdrawal_guard");
        logs.deleteAll();
        members.deleteAll();
        reset(redis);
    }

    @Test
    @DisplayName("옛 조리 기록 테이블이 없는 DB에서도 새 회원 탈퇴가 성공한다")
    void withdrawsWithoutLegacyTable() {
        Member member = member("fresh");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables " +
                "where table_name = 'member_cook_histories'", Long.class)).isZero();

        assertThat(auth.withdrawal(member.getId()).message()).isEqualTo("회원 탈퇴가 완료되었습니다.");

        assertThat(members.existsById(member.getId())).isFalse();
        verify(redis).delete(cacheKeys(member.getId()));
    }

    @Test
    @DisplayName("탈퇴 회원의 대화와 추천 ID는 보관하고 다른 회원 연결은 유지한다")
    void retainsChatDataAndAdminCanReadIt() throws Exception {
        Member withdrawn = member("withdrawn");
        Member other = member("other");
        ChatLog retained = log(withdrawn, Set.of(20L, 21L));
        ChatLog unaffected = log(other, Set.of(30L));

        auth.withdrawal(withdrawn.getId());

        transactionTemplate.executeWithoutResult(status -> {
            ChatLog result = logs.findById(retained.getId()).orElseThrow();
            assertThat(result.getMember()).isNull();
            assertThat(result.getUserMessage()).isEqualTo("분석용 질문");
            assertThat(result.getBotMessage()).isEqualTo("분석용 답변");
            assertThat(result.getRecommendedRecipeIds()).containsExactlyInAnyOrder(20L, 21L);
            assertThat(logs.findById(unaffected.getId()).orElseThrow().getMember().getId())
                    .isEqualTo(other.getId());
        });
        assertThat(members.existsById(other.getId())).isTrue();
        assertThat(logs.count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from chat_log_recipe_ids", Long.class)).isEqualTo(3);

        OpenEntityManagerInViewInterceptor view = new OpenEntityManagerInViewInterceptor();
        view.setEntityManagerFactory(entityManagerFactory);
        var mvc = MockMvcBuilders.standaloneSetup(new AdminChatController(logs))
                .addInterceptors(new WebRequestHandlerInterceptorAdapter(view))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var response = mvc.perform(get("/admin/chats")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"memberId\":null", "분석용 질문");
    }

    @Test
    @DisplayName("회원 삭제가 실패하면 대화 연결도 롤백되고 Redis는 변경하지 않는다")
    void rollbackKeepsMemberAndChatLink() {
        Member member = member("guarded");
        ChatLog chatLog = log(member, Set.of(20L));
        jdbc.execute("create table withdrawal_guard (member_id bigint references members(id))");
        jdbc.update("insert into withdrawal_guard(member_id) values (?)", member.getId());

        assertThatThrownBy(() -> auth.withdrawal(member.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(members.existsById(member.getId())).isTrue();
        transactionTemplate.executeWithoutResult(status ->
                assertThat(logs.findById(chatLog.getId()).orElseThrow().getMember().getId())
                        .isEqualTo(member.getId()));
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("외부 트랜잭션이 롤백되면 Redis 정리 콜백은 실행되지 않는다")
    void doesNotDeleteCachesBeforeCommit() {
        Member member = member("rollback");

        transactionTemplate.executeWithoutResult(status -> {
            auth.withdrawal(member.getId());
            verifyNoInteractions(redis);
            status.setRollbackOnly();
        });

        assertThat(members.existsById(member.getId())).isTrue();
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("커밋 후 Redis 장애가 나도 완료된 회원 삭제를 실패 응답으로 바꾸지 않는다")
    void cacheFailureDoesNotUndoWithdrawal() {
        Member member = member("redis-failure");
        when(redis.delete(anyCollection())).thenThrow(new RedisConnectionFailureException("테스트 장애"));

        assertThatCode(() -> auth.withdrawal(member.getId())).doesNotThrowAnyException();

        assertThat(members.existsById(member.getId())).isFalse();
        verify(redis).delete(cacheKeys(member.getId()));
    }

    @Test
    @DisplayName("로그 저장이 먼저 실행되면 커밋 후 탈퇴가 그 로그의 연결을 해제한다")
    void concurrentSaveThenWithdrawal() throws Exception {
        Member member = member("save-first");
        CountDownLatch savedWithLock = new CountDownLatch(1);
        CountDownLatch withdrawalStarted = new CountDownLatch(1);
        CountDownLatch releaseSave = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var save = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                chatLogService.saveChatLog(member.getId(), "질문", "제목", "답변", List.of(20L));
                savedWithLock.countDown();
                await(releaseSave);
            }));
            await(savedWithLock);
            var withdrawal = executor.submit(() -> {
                withdrawalStarted.countDown();
                return auth.withdrawal(member.getId());
            });
            await(withdrawalStarted);
            assertThatThrownBy(() -> withdrawal.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            releaseSave.countDown();
            save.get(10, TimeUnit.SECONDS);
            withdrawal.get(10, TimeUnit.SECONDS);

            assertThat(members.existsById(member.getId())).isFalse();
            assertThat(logs.count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from chat_logs where member_id is null", Long.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from chat_log_recipe_ids", Long.class)).isEqualTo(1);
        } finally {
            releaseSave.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("탈퇴가 먼저 실행되면 뒤늦게 실행된 로그 저장은 삭제된 회원을 다시 참조하지 않는다")
    void concurrentWithdrawalThenSave() throws Exception {
        Member member = member("withdrawal-first");
        CountDownLatch deletedWithLock = new CountDownLatch(1);
        CountDownLatch saveStarted = new CountDownLatch(1);
        CountDownLatch releaseWithdrawal = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var withdrawal = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                auth.withdrawal(member.getId());
                deletedWithLock.countDown();
                await(releaseWithdrawal);
            }));
            await(deletedWithLock);
            var save = executor.submit(() -> {
                saveStarted.countDown();
                chatLogService.saveChatLog(member.getId(), "질문", "제목", "답변", List.of(20L));
            });
            await(saveStarted);
            assertThatThrownBy(() -> save.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            releaseWithdrawal.countDown();
            withdrawal.get(10, TimeUnit.SECONDS);
            save.get(10, TimeUnit.SECONDS);

            assertThat(members.existsById(member.getId())).isFalse();
            assertThat(logs.count()).isZero();
        } finally {
            releaseWithdrawal.countDown();
            executor.shutdownNow();
        }
    }

    private Member member(String name) {
        return members.saveAndFlush(Member.builder().nickname(name).loginId(name + "@example.invalid")
                .email(name + "@example.invalid").role(Role.ROLE_USER).loginType(LoginType.GENERAL).build());
    }

    private ChatLog log(Member member, Set<Long> recipeIds) {
        return logs.saveAndFlush(ChatLog.builder().member(member).userMessage("분석용 질문")
                .botMessage("분석용 답변").recommendedRecipeIds(new HashSet<>(recipeIds)).build());
    }

    private List<String> cacheKeys(Long memberId) {
        return List.of("RT:" + memberId, "chat:room:" + memberId + ":messages");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("동시 실행 테스트 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
    @EntityScan("com.mohaemukzip.mohaemukzip_be.domain")
    @EnableJpaRepositories("com.mohaemukzip.mohaemukzip_be.domain")
    @EnableJpaAuditing
    static class Config {
        @Bean
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate() {
            return mock(RedisTemplate.class);
        }

        @Bean
        AuthCommandService auth(MemberRepository members, RedisTemplate<String, String> redis,
                MemberTermRepository terms, MemberMissionRepository missions,
                MemberFavoriteRepository favorites, MemberIngredientRepository ingredients,
                MemberRecipeRepository recipes, CookingRecordRepository cooking,
                IngredientRequestRepository requests, ChatLogRepository logs) {
            // 이 테스트는 탈퇴 경로만 사용하며 외부 로그인·메일·AI 의존성은 연결하지 않는다.
            return new AuthCommandServiceImpl(members, null, redis, null, null, null, null, null, null,
                    terms, missions, favorites, ingredients, recipes, cooking, requests, logs);
        }

        @Bean
        ChatLogService chatLogService(ChatLogRepository logs, MemberRepository members,
                TransactionTemplate transactionTemplate) {
            return new ChatLogService(logs, members, transactionTemplate);
        }
    }
}
