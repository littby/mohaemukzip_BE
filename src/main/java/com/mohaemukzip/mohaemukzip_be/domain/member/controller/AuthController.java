package com.mohaemukzip.mohaemukzip_be.domain.member.controller;

import com.mohaemukzip.mohaemukzip_be.domain.member.dto.AuthResponseDTO;
import com.mohaemukzip.mohaemukzip_be.domain.member.dto.AuthRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.member.dto.TermResponseDTO;
import com.mohaemukzip.mohaemukzip_be.domain.member.service.command.auth.AuthCommandService;
import com.mohaemukzip.mohaemukzip_be.domain.member.service.query.auth.AuthQueryService;
import com.mohaemukzip.mohaemukzip_be.domain.member.service.command.term.TermCommandService;
import com.mohaemukzip.mohaemukzip_be.domain.member.service.query.term.TermQueryService;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.jwt.JwtProvider;
import com.mohaemukzip.mohaemukzip_be.global.response.ApiResponse;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import com.mohaemukzip.mohaemukzip_be.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Tag(name = "Auth" , description = "인증 관련 API")
@Validated
public class AuthController {

    private final AuthCommandService authCommandService;
    private final AuthQueryService authQueryService;
    private final JwtProvider jwtProvider;
    private final TermQueryService termQueryService;
    private final TermCommandService termCommandService;

    @Operation(summary = "회원가입 (일반)")
    @PostMapping("/signup")
    public ApiResponse<AuthResponseDTO.GetUserDTO> signup(@Valid @RequestBody AuthRequestDTO.SignUpRequest request) {

        AuthResponseDTO.GetUserDTO response = authCommandService.signup(request);

        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "로그인 (일반)")
    @PostMapping("/login")
    public ApiResponse<AuthResponseDTO.GetUserDTO> login(@Valid @RequestBody AuthRequestDTO.LoginRequest request) {

        AuthResponseDTO.GetUserDTO response = authCommandService.login(request);

        return ApiResponse.onSuccess(response);
    }
    @Operation(summary = "로그인 (카카오)")
    @PostMapping("/login/kakao")
    public ApiResponse<AuthResponseDTO.GetUserDTO> kakaoLogin(
            @Valid @RequestBody AuthRequestDTO.KakaoLoginRequest request) {

        AuthResponseDTO.GetUserDTO response =  authCommandService.kakaoLogin(request);

        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "로그인 (애플)")
    @PostMapping("/login/apple")
    public ApiResponse<AuthResponseDTO.GetUserDTO> appleLogin(
            @Valid @RequestBody AuthRequestDTO.AppleLoginRequest request) {

        AuthResponseDTO.GetUserDTO response = authCommandService.appleLogin(request);

        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "토큰 재발급")
    @PostMapping("/reissue")
    public ApiResponse<AuthResponseDTO.TokenResponse> reissueToken(
            @RequestHeader("Authorization") String expiredAccessToken,
            @RequestHeader("X-Refresh-Token") String refreshToken) {
        String oldAt = expiredAccessToken.substring(7); // "Bearer " 제거
        AuthResponseDTO.TokenResponse response = authCommandService.reissueToken(refreshToken);
        return ApiResponse.onSuccess(response);
    }
  
    @Operation(summary = "아이디 중복 확인")
    @PostMapping("/check-loginid")
    public ApiResponse<AuthResponseDTO.CheckLoginIdResponse> checkLoginId(
            @Valid @RequestBody AuthRequestDTO.CheckLoginIdRequest request) {

        boolean isDuplicate = authQueryService.checkLoginIdDuplicate(request.loginId());

        AuthResponseDTO.CheckLoginIdResponse response = isDuplicate
                ? AuthResponseDTO.CheckLoginIdResponse.ofNotAvailable()
                : AuthResponseDTO.CheckLoginIdResponse.ofAvailable();

        return ApiResponse.onSuccess(response);
    }
    @Operation(summary="약관 목록 조회")
    @GetMapping("/terms")
    public ApiResponse<TermResponseDTO.TermListResponse> getTerms() {
        TermResponseDTO.TermListResponse response = termQueryService.getTerms();
        return ApiResponse.onSuccess(response);
    }

//    @Operation(summary = "소셜로그인 후 약관 동의",
//            description = "[\n" +
//                    " { \"termId\": 1, \"isAgreed\": true },\n" +
//                    " { \"termId\": 2, \"isAgreed\": true },\n" +
//                    " { \"termId\": 3, \"isAgreed\": true },\n" +
//                    " { \"termId\": 4, \"isAgreed\": false }\n" +
//                    " ]")
//
//    @PostMapping("/terms/agree")
//    public ApiResponse<Void> agreeTermsAfterSocialLogin(
//            @AuthenticationPrincipal CustomUserDetails userDetails,
//            @Valid @RequestBody List<TermRequestDTO.TermAgreementRequest> terms) {
//        if (userDetails == null) {
//            throw new BusinessException(ErrorStatus.TOKEN_MISSING);
//        }
//        Long memberId = userDetails.getMember().getId();
//        termCommandService.updateMemberTerms(memberId, terms);
//        return ApiResponse.onSuccess(null);
//    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ApiResponse<AuthResponseDTO.LogoutResponse> logout(
            @RequestHeader("Authorization") String accessToken) {
        String token = accessToken.substring(7);
        AuthResponseDTO.LogoutResponse response = authCommandService.logout(token);
        return ApiResponse.onSuccess(response);
    }

    @Operation(summary = "회원탈퇴")
    @DeleteMapping("/withdrawal")
    public ApiResponse<AuthResponseDTO.WithdrawalResponse> withdrawal(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new BusinessException(ErrorStatus.TOKEN_MISSING);
        }
        Long memberId = userDetails.getMember().getId();
        AuthResponseDTO.WithdrawalResponse response = authCommandService.withdrawal(memberId);
        return ApiResponse.onSuccess(response);
    }
}
