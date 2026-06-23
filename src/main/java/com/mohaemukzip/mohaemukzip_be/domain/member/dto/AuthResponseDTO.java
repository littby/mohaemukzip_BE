package com.mohaemukzip.mohaemukzip_be.domain.member.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.Member;
import com.mohaemukzip.mohaemukzip_be.domain.member.entity.enums.LoginType;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class AuthResponseDTO {

    public record GetUserDTO(
            Long id,
            String accessToken,
            String refreshToken,
            boolean isNewUser,
            boolean isInactive,
            LoginType loginType
    ) {
        public static GetUserDTO of(Member member, String accessToken, String refreshToken , boolean isNewUser) {
            return new GetUserDTO(
                    member.getId(),
                    accessToken,
                    refreshToken,
                    isNewUser,
                    member.isInactive(),
                    member.getLoginType()
            );
        }
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken
    ) {
        @Builder
        public TokenResponse {}
    }

    public record CheckLoginIdResponse(
            boolean available,
            String message
    ) {
        public static CheckLoginIdResponse ofAvailable() {
            return new CheckLoginIdResponse(true, "사용 가능한 아이디예요.");
        }

        public static CheckLoginIdResponse ofNotAvailable() {
            return new CheckLoginIdResponse(false, "이미 사용중인 아이디예요. 다른 아이디를 입력해 주세요.");
        }
    }

    public record LogoutResponse(
            String message
    ) { }

    public record WithdrawalResponse(
            String message
    ) { }

    @Getter
    @NoArgsConstructor
    public static class GetKakaoUserInfoDTO {
        private String id;

        @JsonProperty("kakao_account")
        private KakaoAccount kakaoAccount;

        @Getter @NoArgsConstructor
        public static class KakaoAccount {
            private Profile profile;
        }

        @Getter @NoArgsConstructor
        public static class Profile {
            private String nickname;
        }
    }

    @Getter
    @NoArgsConstructor
    public static class GetAppleUserInfoDTO {
        private String sub;    // 애플 유저 ID
        private String email;  // 이메일 (최초 로그인 시에만 옴)
    }
}
