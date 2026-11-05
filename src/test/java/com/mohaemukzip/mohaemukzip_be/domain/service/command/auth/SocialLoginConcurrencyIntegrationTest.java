package com.mohaemukzip.mohaemukzip_be.domain.service.command.auth;


import com.mohaemukzip.mohaemukzip_be.domain.member.dto.AuthRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.enums.LoginType;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.enums.Role;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.MemberRepository;
import com.mohaemukzip.mohaemukzip_be.domain.member.service.command.auth.AuthCommandService;
import com.mohaemukzip.mohaemukzip_be.domain.member.service.command.auth.AuthCommandServiceImpl;
import com.mohaemukzip.mohaemukzip_be.domain.member.service.command.auth.SocialMemberCreator;
import com.mohaemukzip.mohaemukzip_be.global.jwt.JwtProvider;
import com.mohaemukzip.mohaemukzip_be.global.service.ApplePublicKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = SocialLoginConcurrencyIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.name=social-login-isolated",
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:social_login;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "logging.level.root=ERROR"
})
class SocialLoginConcurrencyIntegrationTest {

    private static final String APPLE_ID = "001234.test-apple-id.0001";

    // 설정값(@Value) 주입을 피하려고 스프링 빈으로 등록하지 않고 직접 생성자에 넘김
    private static final ApplicationEventPublisher EVENTS = mock(ApplicationEventPublisher.class);
    private static final JwtProvider JWT = mock(JwtProvider.class);
    private static final ApplePublicKeyService APPLE = mock(ApplePublicKeyService.class);

    @Autowired private AuthCommandService auth;
    @Autowired private MemberRepository members;
    @Autowired private RacingSocialMemberCreator creator;
    @Autowired private RedisTemplate<String, String> redis;
    @Autowired private PlatformTransactionManager transactionManager;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    @BeforeEach
    void setUp() {
        members.deleteAll();
        reset(redis, JWT, APPLE, EVENTS);
        creator.beforeCreate(() -> {});
        when(redis.opsForValue()).thenReturn(valueOps);
        when(APPLE.extractSubFromIdentityToken(any())).thenReturn(APPLE_ID);
        when(JWT.generateAccessToken(any())).thenReturn("access-token");
        when(JWT.generateRefreshToken(any())).thenReturn("refresh-token");
    }

    @Test
    @DisplayName("신규 애플 회원은 한 명만 생성되고 가입 이벤트가 발행된다")
    void createsNewAppleMember() {
        auth.appleLogin(appleRequest());

        assertThat(members.count()).isEqualTo(1);
        verify(EVENTS, times(1)).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("이미 가입된 애플 회원은 새로 생성하지 않고 가입 이벤트도 발행하지 않는다")
    void reusesExistingAppleMember() {
        Member existing = members.saveAndFlush(appleMember());

        auth.appleLogin(appleRequest());

        assertThat(members.count()).isEqualTo(1);
        verify(EVENTS, never()).publishEvent(any(Object.class));
        verify(valueOps).set(eq("RT:" + existing.getId()), anyString(), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("동시 요청으로 다른 요청이 먼저 가입시키면 500 없이 기존 회원으로 로그인한다")
    void concurrentSignupFallsBackToExistingMember() {
        TransactionTemplate otherRequest = new TransactionTemplate(transactionManager);
        otherRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        AtomicReference<Long> winnerId = new AtomicReference<>();

        // 저장 직전에, 다른 요청이 같은 애플 계정으로 먼저 가입한 상황 재현
        creator.beforeCreate(() -> otherRequest.executeWithoutResult(status ->
                winnerId.set(members.saveAndFlush(appleMember()).getId())));

        assertThatCode(() -> auth.appleLogin(appleRequest())).doesNotThrowAnyException();

        assertThat(members.count()).isEqualTo(1);
        verify(EVENTS, never()).publishEvent(any(Object.class));
        verify(valueOps).set(eq("RT:" + winnerId.get()), anyString(), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    private AuthRequestDTO.AppleLoginRequest appleRequest() {
        return new AuthRequestDTO.AppleLoginRequest("test-identity-token");
    }

    private Member appleMember() {
        return Member.builder().oauthId(APPLE_ID).nickname("애플 사용자_test")
                .loginType(LoginType.APPLE).role(Role.ROLE_USER).score(0).build();
    }

    // 저장 직전에 끼어들 수 있는 테스트용 SocialMemberCreator
    static class RacingSocialMemberCreator extends SocialMemberCreator {
        private Runnable beforeCreate = () -> {};

        RacingSocialMemberCreator(MemberRepository memberRepository) {
            super(memberRepository);
        }

        public void beforeCreate(Runnable hook) {
            this.beforeCreate = hook;
        }

        @Override
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public Member create(Member newMember) {
            beforeCreate.run();
            return super.create(newMember);
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
        RacingSocialMemberCreator socialMemberCreator(MemberRepository members) {
            return new RacingSocialMemberCreator(members);
        }

        @Bean
        AuthCommandService auth(MemberRepository members, RedisTemplate<String, String> redis,
                                SocialMemberCreator creator) {
            // 애플 로그인 경로만 사용, 나머지 의존성은 연결하지 않음
            return new AuthCommandServiceImpl(members, JWT, redis, null, null, null, null,
                    APPLE, creator, null, EVENTS,
                    null, null, null, null, null, null, null);
        }
    }
}