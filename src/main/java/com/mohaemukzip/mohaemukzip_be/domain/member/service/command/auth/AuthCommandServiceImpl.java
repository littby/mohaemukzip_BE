package com.mohaemukzip.mohaemukzip_be.domain.member.service.command.auth;

import com.mohaemukzip.mohaemukzip_be.domain.member.dto.AuthRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.member.dto.AuthResponseDTO;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.enums.LoginType;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.enums.Role;
import com.mohaemukzip.mohaemukzip_be.domain.member.repository.MemberRepository;
import com.mohaemukzip.mohaemukzip_be.domain.member.converter.AuthConverter;
import com.mohaemukzip.mohaemukzip_be.domain.member.service.command.term.TermCommandService;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.jwt.JwtProvider;
import com.mohaemukzip.mohaemukzip_be.global.jwt.TokenBlacklistService;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import com.mohaemukzip.mohaemukzip_be.global.service.ApplePublicKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;


import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static final String KAKAO_USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private static final String REFRESH_TOKEN_PREFIX = "RT:";

    @Transactional
    public AuthResponseDTO.GetUserDTO signup(AuthRequestDTO.SignUpRequest request) {

        if (memberRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorStatus.DUPLICATE_LOGIN_ID);
        }

        Member member = Member.builder()
                .nickname(request.nickname())
                .loginId(request.loginId())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ROLE_USER)
                .loginType(LoginType.GENERAL)
                .score(0)
                .build();
        Member savedMember = memberRepository.save(member);
        termCommandService.createMemberTerms(savedMember, request.termAgreements());

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

    @Transactional
    public AuthResponseDTO.GetUserDTO kakaoLogin(AuthRequestDTO.KakaoLoginRequest kakaoLoginRequest){
        AuthResponseDTO.GetKakaoUserInfoDTO kakaoUserInfo = getKakaoUserInfo(kakaoLoginRequest.kakaoAccessToken());

        String kakaoId = kakaoUserInfo.getId();
        String nickname = extractNickname(kakaoUserInfo);

        AtomicBoolean isNewMember = new AtomicBoolean(false);


        Member member;
        try {
            member = memberRepository.findByLoginTypeAndOauthId(LoginType.KAKAO, kakaoId)
                    .orElseGet(() -> {
                        isNewMember.set(true);
                        return createKakaoMember(kakaoId, nickname);
                    });
        } catch (DataIntegrityViolationException e) {
            member = memberRepository.findByLoginTypeAndOauthId(LoginType.KAKAO, kakaoId)
                    .orElseThrow(() -> new BusinessException(ErrorStatus.KAKAO_API_ERROR));
        }

        if (member.isInactive()) {
            throw new BusinessException(ErrorStatus.ALREADY_WITHDRAWN_MEMBER);
        }

        return generateAndSaveTokens(member, isNewMember.get());
    }

    @Transactional
    public AuthResponseDTO.GetUserDTO appleLogin(AuthRequestDTO.AppleLoginRequest appleLoginRequest) {

        String appleId = applePublicKeyService.extractSubFromIdentityToken(appleLoginRequest.identityToken());
        AtomicBoolean isNewMember = new AtomicBoolean(false);

        Member member;
        try {
            member = memberRepository.findByLoginTypeAndOauthId(LoginType.APPLE, appleId)
                    .orElseGet(() -> {
                        isNewMember.set(true);
                        return createAppleMember(appleId);
                    });
        } catch (DataIntegrityViolationException e) {

            member = memberRepository.findByLoginTypeAndOauthId(LoginType.APPLE, appleId)
                    .orElseThrow(() -> new BusinessException(ErrorStatus.KAKAO_API_ERROR));
        }

        if (member.isInactive()) {
            throw new BusinessException(ErrorStatus.ALREADY_WITHDRAWN_MEMBER);
        }

        return generateAndSaveTokens(member, isNewMember.get());
    }

    private Member createAppleMember(String appleId) {
        Member newMember = Member.builder()
                .oauthId(appleId)
                .nickname("애플 사용자_" + (appleId.length() >= 6 ? appleId.substring(0, 6) : appleId))
                .loginType(LoginType.APPLE)
                .role(Role.ROLE_USER)
                .score(0)
                .build();
        return memberRepository.save(newMember);
    }

    private String extractNickname(AuthResponseDTO.GetKakaoUserInfoDTO kakaoUserInfo) {
        if (kakaoUserInfo.getKakaoAccount() != null
                && kakaoUserInfo.getKakaoAccount().getProfile() != null
                && kakaoUserInfo.getKakaoAccount().getProfile().getNickname() != null) {
            return kakaoUserInfo.getKakaoAccount().getProfile().getNickname();
        }

        return "카카오 사용자_" + kakaoUserInfo.getId();
    }

    private Member createKakaoMember(String kakaoId, String nickname) {
        Member newMember = Member.builder()
                .oauthId(kakaoId)
                .nickname(nickname)
                .loginType(LoginType.KAKAO)
                .role(Role.ROLE_USER)
                .score(0)
                .build();

        Member savedMember = memberRepository.save(newMember);

        return savedMember;
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
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));

        if (member.isInactive()) {
            throw new BusinessException(ErrorStatus.ALREADY_WITHDRAWN_MEMBER);
        }
        member.deactivate();

        // Redis에서 Refresh Token 삭제
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + memberId);

        return AuthConverter.toWithdrawalResponseDTO();
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
}