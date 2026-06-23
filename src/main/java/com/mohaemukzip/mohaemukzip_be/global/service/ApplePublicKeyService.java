package com.mohaemukzip.mohaemukzip_be.global.service;


import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;

@Slf4j
@Service
public class ApplePublicKeyService {

    private static final String APPLE_PUBLIC_KEY_URL = "https://appleid.apple.com/auth/keys";

    @Value("${apple.client-id}")
    private String clientId;

    public String extractSubFromIdentityToken(String identityToken) {
        try {
            // 1. identity token 파싱
            SignedJWT signedJWT = SignedJWT.parse(identityToken);
            String kid = signedJWT.getHeader().getKeyID();

            // 2. 애플 공개키 목록 조회
            JWKSet jwkSet = JWKSet.load(new URL(APPLE_PUBLIC_KEY_URL));

            // 3. kid 매칭되는 공개키 찾기
            RSAKey rsaKey = (RSAKey) jwkSet.getKeyByKeyId(kid);
            if (rsaKey == null) {
                throw new BusinessException(ErrorStatus.INVALID_APPLE_TOKEN);
            }

            // 4. 서명 검증
            RSAPublicKey publicKey = rsaKey.toRSAPublicKey();
            JWSVerifier verifier = new RSASSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                throw new BusinessException(ErrorStatus.INVALID_APPLE_TOKEN);
            }

            // 5. claims 꺼내기
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // 6. audience 검증 (client-id 일치 확인)
            if (!claims.getAudience().contains(clientId)) {
                throw new BusinessException(ErrorStatus.INVALID_APPLE_TOKEN);
            }

            // 발급자 검증
            if (!"https://appleid.apple.com".equals(claims.getIssuer())) {
                throw new BusinessException(ErrorStatus.INVALID_APPLE_TOKEN);
            }

            // 만료 시간 검증
            if (claims.getExpirationTime() == null ||
                    claims.getExpirationTime().before(new java.util.Date())) {
                throw new BusinessException(ErrorStatus.INVALID_APPLE_TOKEN);
            }



            // 7. sub 반환 (애플 유저 ID)
            return claims.getSubject();

        } catch (BusinessException e) {
            throw e;
        } catch (java.io.IOException e) {
            log.error("Apple 공개키 조회 실패", e);
            throw new BusinessException(ErrorStatus.APPLE_PUBLIC_KEY_ERROR);
        } catch (Exception e) {
            log.error("Apple identity token 검증 실패", e);
            throw new BusinessException(ErrorStatus.INVALID_APPLE_TOKEN);
        }
    }
}
