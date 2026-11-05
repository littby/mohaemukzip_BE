package com.mohaemukzip.mohaemukzip_be.domain.member.service.command.auth;

import com.mohaemukzip.mohaemukzip_be.domain.chatbot.repository.ChatLogRepository;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.IngredientRequestRepository;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.MemberFavoriteRepository;
import com.mohaemukzip.mohaemukzip_be.domain.ingredient.repository.MemberIngredientRepository;
import com.mohaemukzip.mohaemukzip_be.domain.member.dto.AuthRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.member.dto.AuthResponseDTO;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.enums.LoginType;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.enums.Role;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.MemberRepository;
import com.mohaemukzip.mohaemukzip_be.domain.member.converter.AuthConverter;
import com.mohaemukzip.mohaemukzip_be.domain.member.event.MemberSignedUpEvent;
import com.mohaemukzip.mohaemukzip_be.domain.member.event.MemberWithdrawnEvent;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.MemberTermRepository;
import com.mohaemukzip.mohaemukzip_be.domain.member.service.command.term.TermCommandService;
import com.mohaemukzip.mohaemukzip_be.domain.mission.repository.MemberMissionRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.CookingRecordRepository;
import com.mohaemukzip.mohaemukzip_be.domain.recipe.repository.MemberRecipeRepository;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.jwt.JwtProvider;
import com.mohaemukzip.mohaemukzip_be.global.jwt.TokenBlacklistService;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import com.mohaemukzip.mohaemukzip_be.global.service.ApplePublicKeyService;
import com.mohaemukzip.mohaemukzip_be.global.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.util.UUID;
import java.util.List;


import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthCommandServiceImpl implements AuthCommandService {
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final TermCommandService termCommandService;
    private final WebClient webClient;
    private final TokenBlacklistService tokenBlacklistService;
    private final ApplePublicKeyService applePublicKeyService;
    private final SocialMemberCreator socialMemberCreator;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;

    private final MemberTermRepository memberTermRepository;
    private final MemberMissionRepository memberMissionRepository;
    private final MemberFavoriteRepository memberFavoriteRepository;
    private final MemberIngredientRepository memberIngredientRepository;
    private final MemberRecipeRepository memberRecipeRepository;
    private final CookingRecordRepository cookingRecordRepository;
    private final IngredientRequestRepository ingredientRequestRepository;
    private final ChatLogRepository chatLogRepository;

    private static final String KAKAO_USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private static final String REFRESH_TOKEN_PREFIX = "RT:";
    private static final String PASSWORD_RESET_VERIFIED_PREFIX = "password-reset:verified:";
    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(10);

    @Transactional
    public AuthResponseDTO.GetUserDTO signup(AuthRequestDTO.SignUpRequest request) {

        if (memberRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorStatus.DUPLICATE_LOGIN_ID);
        }

        Member member = Member.builder()
                .nickname(request.nickname())
                .loginId(request.loginId())
                .email(request.loginId())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ROLE_USER)
                .loginType(LoginType.GENERAL)
                .score(0)
                .build();
        Member savedMember = memberRepository.save(member);
        termCommandService.createMemberTerms(savedMember, request.termAgreements());
        savedMember.agreeToTerms();

        publishSignedUpEvent(savedMember);
        return generateAndSaveTokens(member, true);
    }

    public AuthResponseDTO.GetUserDTO login(AuthRequestDTO.LoginRequest request) {
        Member member = memberRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));

        if (member.isInactive()) {
            throw new BusinessException(ErrorStatus.ALREADY_WITHDRAWN_MEMBER);
        }

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorStatus.INVALID_PASSWORD);
        }
        return generateAndSaveTokens(member, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthResponseDTO.GetUserDTO kakaoLogin(AuthRequestDTO.KakaoLoginRequest kakaoLoginRequest){
        AuthResponseDTO.GetKakaoUserInfoDTO kakaoUserInfo = getKakaoUserInfo(kakaoLoginRequest.kakaoAccessToken());
        String kakaoId = kakaoUserInfo.getId();

        SocialMemberResult result = findOrCreateSocialMember(LoginType.KAKAO, kakaoId,
                () -> buildKakaoMember(kakaoId, extractNickname(kakaoUserInfo),
                        kakaoUserInfo.getKakaoAccount().getEmail()));

        return completeSocialLogin(result);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthResponseDTO.GetUserDTO appleLogin(AuthRequestDTO.AppleLoginRequest appleLoginRequest) {
        String appleId = applePublicKeyService.extractSubFromIdentityToken(appleLoginRequest.identityToken());

        SocialMemberResult result = findOrCreateSocialMember(LoginType.APPLE, appleId,
                () -> buildAppleMember(appleId));

        return completeSocialLogin(result);
    }

    private record SocialMemberResult(Member member, boolean isNewMember) {}

    // 동시 로그인으로 다른 요청이 먼저 가입시킨 경우, 재조회에서 그 회원이 보이도록 READ_COMMITTED에서 호출한다.
    private SocialMemberResult findOrCreateSocialMember(LoginType loginType, String oauthId,
                                                        Supplier<Member> newMember) {
        Optional<Member> existing = memberRepository.findByLoginTypeAndOauthId(loginType, oauthId);
        if (existing.isPresent()) {
            return new SocialMemberResult(existing.get(), false);
        }

        try {
            return new SocialMemberResult(socialMemberCreator.create(newMember.get()), true);
        } catch (DataIntegrityViolationException e) {
            log.warn("[소셜 로그인] 동시 가입 충돌 - loginType: {}, 기존 회원 재조회", loginType);
            Member member = memberRepository.findByLoginTypeAndOauthId(loginType, oauthId)
                    .orElseThrow(() -> new BusinessException(ErrorStatus.KAKAO_API_ERROR));
            return new SocialMemberResult(member, false);
        }
    }

    private AuthResponseDTO.GetUserDTO completeSocialLogin(SocialMemberResult result) {
        Member member = result.member();

        if (member.isInactive()) {
            member.reactivate();
        }

        if (result.isNewMember()) {
            publishSignedUpEvent(member);
        }

        return generateAndSaveTokens(member, result.isNewMember());
    }

    private void publishSignedUpEvent(Member member) {
        eventPublisher.publishEvent(
                new MemberSignedUpEvent(member.getId(), member.getNickname(), member.getLoginType()));
    }

    private Member buildAppleMember(String appleId) {
        return Member.builder()
                .oauthId(appleId)
                .nickname("애플 사용자_" + (appleId.length() >= 6 ? appleId.substring(0, 6) : appleId))
                .loginType(LoginType.APPLE)
                .role(Role.ROLE_USER)
                .score(0)
                .build();
    }

    private String extractNickname(AuthResponseDTO.GetKakaoUserInfoDTO kakaoUserInfo) {
        if (kakaoUserInfo.getKakaoAccount() != null
                && kakaoUserInfo.getKakaoAccount().getProfile() != null
                && kakaoUserInfo.getKakaoAccount().getProfile().getNickname() != null) {
            return kakaoUserInfo.getKakaoAccount().getProfile().getNickname();
        }

        return "카카오 사용자_" + kakaoUserInfo.getId();
    }

    private Member buildKakaoMember(String kakaoId, String nickname, String email) {
        return Member.builder()
                .oauthId(kakaoId)
                .nickname(nickname)
                .email(email)
                .loginType(LoginType.KAKAO)
                .role(Role.ROLE_USER)
                .score(0)
                .build();
    }

    private AuthResponseDTO.GetUserDTO generateAndSaveTokens(Member member, boolean isNewUser) {

        String accessToken = jwtProvider.generateAccessToken(member);
        String refreshToken = jwtProvider.generateRefreshToken(member);

        saveRefreshTokenToRedis(member.getId().toString(), refreshToken);

        return AuthConverter.toSignupResponseDTO(member, accessToken, refreshToken, isNewUser);
    }
    /**
     * Refresh Token을 Redis에 저장
     */
    private void saveRefreshTokenToRedis(String userId, String refreshToken) {

        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + userId,
                refreshToken,
                JwtProvider.REFRESH_TOKEN_VALIDITY_MS,
                TimeUnit.MILLISECONDS
        );

    }

    private String getUserIdFromToken(String token) {
        try {
            return jwtProvider.getUserIdFromToken(token);
        } catch (BusinessException e) {
            throw new BusinessException(ErrorStatus.INVALID_TOKEN);
        }
    }

    private void validateRefreshToken(String memberId, String clientRefreshToken) {
        String key = REFRESH_TOKEN_PREFIX + memberId;

        String storedRefreshToken = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + memberId);

        if (storedRefreshToken == null) {
            throw new BusinessException(ErrorStatus.REFRESH_TOKEN_NOT_FOUND);
        }

        if (!storedRefreshToken.equals(clientRefreshToken)) {
            // 토큰 불일치 시 탈취로 간주하고 즉시 삭제
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + memberId);
            throw new BusinessException(ErrorStatus.REFRESH_TOKEN_MISMATCH);
        }

        // RT 자체의 만료 시간 검증
        if (!jwtProvider.validateToken(clientRefreshToken)) {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + memberId);
            throw new BusinessException(ErrorStatus.REFRESH_TOKEN_EXPIRED);
        }
    }

    @Transactional
    public AuthResponseDTO.TokenResponse reissueToken(String refreshToken) {
        String userId = getUserIdFromToken(refreshToken);
        validateRefreshToken(userId, refreshToken);

        Member member = memberRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));

        String newAccessToken = jwtProvider.generateAccessToken(member);
        String newRefreshToken = jwtProvider.generateRefreshToken(member);
        saveRefreshTokenToRedis(userId, newRefreshToken);
        return AuthConverter.toTokenResponseDTO(newAccessToken, newRefreshToken);
    }

    @Transactional
    public AuthResponseDTO.LogoutResponse logout(String accessToken) {
        String userId = getUserIdFromToken(accessToken);

        // Redis에서 Refresh Token 삭제
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);

        // Access Token을 블랙리스트에 추가 (만료 시간만큼 유지)
        long remainingExpiration = jwtProvider.getRemainingExpiration(accessToken);
        tokenBlacklistService.addToBlacklist(accessToken, remainingExpiration);

        return AuthConverter.toLogoutResponseDTO();
    }

    @Transactional
    public AuthResponseDTO.WithdrawalResponse withdrawal(Long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));

        if (member.isInactive()) {
            throw new BusinessException(ErrorStatus.ALREADY_WITHDRAWN_MEMBER);
        }


        // 개인 활동 데이터는 삭제하고, 분석용 대화 기록은 회원 연결만 해제한다.
        memberTermRepository.deleteAllByMember(member);
        memberMissionRepository.deleteAllByMember_Id(memberId);
        memberFavoriteRepository.deleteAllByMember_Id(memberId);
        memberIngredientRepository.deleteAllByMember_Id(memberId);
        memberRecipeRepository.deleteAllByMember_Id(memberId);
        cookingRecordRepository.deleteAllByMember_Id(memberId);
        ingredientRequestRepository.deleteAllByMember_Id(memberId);
        chatLogRepository.detachMember(memberId);

        // 회원 자체 삭제
        memberRepository.delete(member);
        memberRepository.flush();
        deleteWithdrawalCachesAfterCommit(memberId);

        eventPublisher.publishEvent(
                new MemberWithdrawnEvent(memberId, member.getNickname(), member.getLoginType()));

        return AuthConverter.toWithdrawalResponseDTO();
    }

    private void deleteWithdrawalCachesAfterCommit(Long memberId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    redisTemplate.delete(List.of(
                            REFRESH_TOKEN_PREFIX + memberId,
                            "chat:room:" + memberId + ":messages"));
                } catch (Exception e) {
                    // 회원 삭제는 이미 커밋됐다. 실패 응답으로 되돌리지 않고 운영 확인 대상으로 남긴다.
                    log.error("[회원 탈퇴] DB 삭제 완료 후 Redis 정리 실패 - memberId: {}", memberId, e);
                }
            }
        });
    }

    // 비밀번호 변경
    @Transactional
    public AuthResponseDTO.ResetPasswordResponse resetPassword(AuthRequestDTO.ResetPasswordRequest request) {
        String verifiedKey = PASSWORD_RESET_VERIFIED_PREFIX + request.email();
        String savedToken = redisTemplate.opsForValue().get(verifiedKey);

        if (savedToken == null || !savedToken.equals(request.resetToken())) {
            throw new BusinessException(ErrorStatus.PASSWORD_RESET_NOT_VERIFIED);
        }

        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));

        member.updatePassword(passwordEncoder.encode(request.newPassword()));

        redisTemplate.delete(REFRESH_TOKEN_PREFIX + member.getId());
        redisTemplate.delete(verifiedKey); // 1회용 — 재사용 방지

        return new AuthResponseDTO.ResetPasswordResponse("비밀번호가 변경되었습니다.");
    }

    public AuthResponseDTO.SendAuthCodeResponse sendAuthCode(AuthRequestDTO.SendAuthCodeRequest request) {
        // 이메일 중복 확인
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorStatus.DUPLICATE_EMAIL);
        }

        emailService.sendAuthCode(request.email());

        return new AuthResponseDTO.SendAuthCodeResponse("인증번호가 발송되었습니다.");
    }


    @Transactional(readOnly = true)
    public AuthResponseDTO.SendAuthCodeResponse sendResetPasswordAuthCode(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));

        emailService.sendAuthCode(member.getEmail());

        return new AuthResponseDTO.SendAuthCodeResponse("인증번호가 발송되었습니다.");
    }

    public AuthResponseDTO.VerifyAuthCodeResponse verifyAuthCode(AuthRequestDTO.VerifyAuthCodeRequest request) {
        boolean verified = emailService.verifyAuthCode(request.email(), request.authCode());

        if (!verified) {
            return new AuthResponseDTO.VerifyAuthCodeResponse(false, "인증번호가 일치하지 않거나 만료되었습니다.", null);
        }

        String resetToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                PASSWORD_RESET_VERIFIED_PREFIX + request.email(),
                resetToken,
                PASSWORD_RESET_TOKEN_TTL
        );

        return new AuthResponseDTO.VerifyAuthCodeResponse(true, "인증이 완료되었습니다.", resetToken);
    }

    private AuthResponseDTO.GetKakaoUserInfoDTO getKakaoUserInfo(String accessToken) {
        try {
            log.info("Kakao API 호출 시작");

            AuthResponseDTO.GetKakaoUserInfoDTO userInfo = webClient.get()
                    .uri(KAKAO_USER_INFO_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(AuthResponseDTO.GetKakaoUserInfoDTO.class)
                    .block(Duration.ofSeconds(10));

            if (userInfo == null || userInfo.getId() == null) {
                log.error("Kakao UserInfo null - userInfo: {}", userInfo);
                throw new BusinessException(ErrorStatus.KAKAO_API_ERROR);
            }

            return userInfo;

        } catch (WebClientResponseException e) {
            log.error("Kakao API WebClient Error - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 401) {
                throw new BusinessException(ErrorStatus.INVALID_KAKAO_TOKEN);
            }
            throw new BusinessException(ErrorStatus.KAKAO_API_ERROR);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Kakao API Unexpected Error", e);
            throw new BusinessException(ErrorStatus.KAKAO_API_ERROR);
        }
    }

    public AuthResponseDTO.SendAuthCodeResponse sendResetPasswordAuthCode(AuthRequestDTO.SendResetPasswordAuthCodeRequest request) {
        if (!memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorStatus.MEMBER_NOT_FOUND_BY_EMAIL);
        }

        emailService.sendAuthCode(request.email());

        return new AuthResponseDTO.SendAuthCodeResponse("인증번호가 발송되었습니다.");
    }

    public AuthResponseDTO.SendAuthCodeResponse sendFindPasswordAuthCode(AuthRequestDTO.SendFindPasswordAuthCodeRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND_BY_EMAIL));

        if (member.getLoginType() == LoginType.APPLE) {
            throw new BusinessException(ErrorStatus.MEMBER_NOT_FOUND_BY_EMAIL);
        }

        if (member.getLoginType() == LoginType.KAKAO) {
            throw new BusinessException(ErrorStatus.KAKAO_ACCOUNT_EXISTS);
        }

        emailService.sendAuthCode(request.email());

        return new AuthResponseDTO.SendAuthCodeResponse("인증번호가 발송되었습니다.");
    }
}
