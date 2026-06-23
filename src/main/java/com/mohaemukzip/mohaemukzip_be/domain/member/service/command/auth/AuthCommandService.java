package com.mohaemukzip.mohaemukzip_be.domain.member.service.command.auth;

import com.mohaemukzip.mohaemukzip_be.domain.member.dto.AuthRequestDTO;
import com.mohaemukzip.mohaemukzip_be.domain.member.dto.AuthResponseDTO;

public interface AuthCommandService {
    AuthResponseDTO.GetUserDTO signup(AuthRequestDTO.SignUpRequest signUpRequest);
    AuthResponseDTO.GetUserDTO login(AuthRequestDTO.LoginRequest loginRequest);
    AuthResponseDTO.GetUserDTO kakaoLogin(AuthRequestDTO.KakaoLoginRequest kakaoLoginRequest);
    AuthResponseDTO.GetUserDTO appleLogin(AuthRequestDTO.AppleLoginRequest appleLoginRequest);
    AuthResponseDTO.TokenResponse reissueToken(String refreshToken);
    AuthResponseDTO.LogoutResponse logout(String accessToken);
    AuthResponseDTO.WithdrawalResponse withdrawal(Long memberId);
}
