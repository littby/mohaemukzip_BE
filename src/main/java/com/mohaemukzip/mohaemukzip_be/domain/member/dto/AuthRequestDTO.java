package com.mohaemukzip.mohaemukzip_be.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public class AuthRequestDTO {
    @Schema(description = "로그인 요청")
    public record LoginRequest(

            @Schema(description = "로그인 ID", example = "test@naver.com")
            @NotBlank
            @Email(message = "이메일 형식이 아닙니다.")
            String loginId,

            @Schema(description = "비밀번호", example = "Test1234!")
            @NotBlank
            String password
    ) { }

    public record SignUpRequest(
            @Schema(description = "닉네임", example = "뭐해먹집")
            @Pattern(regexp = "^.{1,15}$")
            @NotBlank(message = "닉네임은 필수입니다")
            String nickname,

            @Schema(description = "이메일", example = "test@naver.com")
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "이메일 형식이 아닙니다.")
            String loginId,

            @Schema(description = "비밀번호", example = "Test1234!")
            @NotBlank(message = "비밀번호는 필수입니다")
            @Pattern(
                    regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{10,}$",
                    message = "비밀번호는 영문과 숫자를 포함해 10자 이상 입력해 주세요."
            )
            String password,
            @Schema(description = "약관 동의 목록",
            example = """
                [
                  { "termId": 1, "isAgreed": true },
                  { "termId": 2, "isAgreed": true },
                  { "termId": 3, "isAgreed": true },
                  { "termId": 4, "isAgreed": false }
                ]
                """)
            @NotEmpty(message = "약관 동의 여부는 필수입니다")
            @Valid
            List<TermRequestDTO.TermAgreementRequest> termAgreements
    ) { }

    public record CheckLoginIdRequest(
            @Schema(description = "이메일", example = "test@naver.com")
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "이메일 형식이 아닙니다.")
            String loginId
    ) {}

    public record ResetPasswordRequest(
            @Schema(description = "이메일", example = "test@naver.com")
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "이메일 형식이 아닙니다.")
            String email,

            @Schema(description = "새 비밀번호", example = "NewPassword123!")
            @NotBlank(message = "새 비밀번호는 필수입니다.")
            @Pattern(
                    regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{10,}$",
                    message = "비밀번호는 영문과 숫자를 포함해 10자 이상 입력해 주세요."
            )
            String newPassword
    ) {}

    public record KakaoLoginRequest(
            @Schema(description = "카카오 액세스 토큰", example = "kakaoAccessToken")
            @NotBlank(message = "카카오 액세스 토큰은 필수입니다.")
            String kakaoAccessToken
    ) {}

    public record AppleLoginRequest(
            @Schema(description = "애플 identity token")
            @NotBlank(message = "애플 identity token은 필수입니다.")
            String identityToken
    ) {}

    public record SendAuthCodeRequest(
            @Schema(description = "이메일", example = "test@naver.com")
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "이메일 형식이 아닙니다.")
            String email
    ) {}

    public record VerifyAuthCodeRequest(
            @Schema(description = "이메일", example = "test@naver.com")
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "이메일 형식이 아닙니다.")
            String email,

            @Schema(description = "인증번호", example = "123456")
            @NotBlank(message = "인증번호를 입력해주세요.")
            @Pattern(regexp = "^\\d{6}$", message = "인증번호는 6자리 숫자입니다.")
            String authCode
    ) {}

    public record SendResetPasswordAuthCodeRequest(
            @Schema(description = "이메일", example = "test@naver.com")
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "이메일 형식이 아닙니다.")
            String email
    ) {}

    public record SendFindPasswordAuthCodeRequest(
            @Schema(description = "이메일", example = "test@naver.com")
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "이메일 형식이 아닙니다.")
            String email
    ) {}
}
