package com.mohaemukzip.mohaemukzip_be.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public class AuthRequestDTO {
    @Schema(description = "로그인 요청")
    public record LoginRequest(
            @Schema(description = "로그인 ID", example = "admin")
            @NotBlank
            String loginId,

            @Schema(description = "비밀번호", example = "mohaemukzip123!")
            @NotBlank
            String password
    ) { }

    public record SignUpRequest(
            @Schema(description = "닉네임", example = "뭐해먹집")
            @Pattern(regexp = "^.{1,15}$")
            @NotBlank(message = "닉네임은 필수입니다")
            String nickname,

            @Schema(description = "로그인 ID", example = "test01")
            @NotBlank(message = "아이디는 필수입니다")
            @Pattern(regexp = "^(?=.*[A-Za-z])[A-Za-z\\d]{4,20}$", message = "아이디는 영문과 숫자를 사용해 4자 이상 입력해주세요.")
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
            @Schema(description = "아이디", example = "test01")
            @NotBlank(message = "아이디를 입력해주세요.")
            @Pattern(regexp = "^(?=.*[A-Za-z])[A-Za-z\\d]{4,20}$",
                    message = "아이디는 영문, 숫자 4-20자로 입력해주세요.")
            String loginId
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
}
