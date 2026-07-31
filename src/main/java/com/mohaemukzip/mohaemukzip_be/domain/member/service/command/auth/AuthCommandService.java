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
    AuthResponseDTO.ResetPasswordResponse resetPassword(AuthRequestDTO.ResetPasswordRequest request);
    AuthResponseDTO.SendAuthCodeResponse sendAuthCode(AuthRequestDTO.SendAuthCodeRequest request);
    AuthResponseDTO.VerifyAuthCodeResponse verifyAuthCode(AuthRequestDTO.VerifyAuthCodeRequest request);
    AuthResponseDTO.SendAuthCodeResponse sendResetPasswordAuthCode(Long memberId);
    AuthResponseDTO.SendAuthCodeResponse sendResetPasswordAuthCode(AuthRequestDTO.SendResetPasswordAuthCodeRequest request);
    AuthResponseDTO.SendAuthCodeResponse sendFindPasswordAuthCode(AuthRequestDTO.SendFindPasswordAuthCodeRequest request);
}
