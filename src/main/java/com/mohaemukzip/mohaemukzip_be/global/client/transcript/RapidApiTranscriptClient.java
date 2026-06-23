package com.mohaemukzip.mohaemukzip_be.global.client.transcript;

import com.mohaemukzip.mohaemukzip_be.global.client.transcript.dto.RapidApiTranscriptResponse;
import com.mohaemukzip.mohaemukzip_be.global.client.transcript.dto.TranscriptSegment;
import com.mohaemukzip.mohaemukzip_be.global.exception.BusinessException;
import com.mohaemukzip.mohaemukzip_be.global.response.code.status.ErrorStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class RapidApiTranscriptClient implements TranscriptClient {

    private final WebClient webClient;

    public RapidApiTranscriptClient(@Qualifier("rapidApiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public List<TranscriptSegment> fetchTranscript(String videoId) {
        log.info("RapidAPI 자막 조회 시작 - videoId: {}", videoId);

        // 책임 1+2: HTTP 호출 및 상태코드 → 예외 변환을 reactive chain 내에서 처리
        RapidApiTranscriptResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/transcript")
                        .queryParam("videoId", videoId)
                        .queryParam("lang", "ko") // 한국어 요청 복구
                        .build())
                .retrieve()
                .onStatus(this::isAuthOrEndpointError, this::handleAuthOrEndpointError)
                .onStatus(HttpStatusCode::isError, this::handleGenericHttpError)
                .bodyToMono(RapidApiTranscriptResponse.class)
                // BusinessException은 그대로 전파, 나머지는 INTERNAL_SERVER_ERROR로 변환
                .onErrorMap(e -> !(e instanceof BusinessException),
                        e -> {
                            log.error("자막 추출 중 예기치 못한 오류 - videoId: {}", videoId, e);
                            return new BusinessException(ErrorStatus.INTERNAL_SERVER_ERROR);
                        })
                .block(Duration.ofSeconds(12)); // HttpClient responseTimeout보다 약간 크게 설정하여 경합 조건 방지

        // 책임 3: 응답 데이터 검증
        return validateTranscript(response, videoId);
    }

    // HTTP 상태코드 분류 (책임 2 보조)
    private boolean isAuthOrEndpointError(HttpStatusCode status) {
        return status == HttpStatus.NOT_FOUND
                || status == HttpStatus.FORBIDDEN
                || status == HttpStatus.UNAUTHORIZED;
    }

    private Mono<? extends Throwable> handleAuthOrEndpointError(ClientResponse clientResponse) {
        return clientResponse.bodyToMono(String.class)
                .defaultIfEmpty("(empty body)")
                .doOnNext(body -> log.error("RapidAPI 인증/엔드포인트 오류 - status: {}, body: {}", clientResponse.statusCode(), body))
                .thenReturn(new BusinessException(ErrorStatus.EXTERNAL_API_ERROR));
    }

    private Mono<? extends Throwable> handleGenericHttpError(ClientResponse clientResponse) {
        return clientResponse.bodyToMono(String.class)
                .defaultIfEmpty("(empty body)")
                .doOnNext(body -> log.error("RapidAPI 통신 오류 - status: {}, body: {}", clientResponse.statusCode(), body))
                .thenReturn(new BusinessException(ErrorStatus.TRANSCRIPT_NOT_AVAILABLE));
    }

    // 응답 검증 (책임 3)
    private List<TranscriptSegment> validateTranscript(RapidApiTranscriptResponse response, String videoId) {
        if (response == null || !response.success()
                || response.transcript() == null || response.transcript().isEmpty()) {
            log.warn("자막 데이터 없음 또는 API 응답 실패 - videoId: {}", videoId);
            throw new BusinessException(ErrorStatus.TRANSCRIPT_NOT_AVAILABLE);
        }
        return response.transcript();
    }
}
