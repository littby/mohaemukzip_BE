package com.mohaemukzip.mohaemukzip_be.global.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class RapidApiConfig {

    // 시스템 환경변수 RAPIDAPI_KEY를 주입받습니다. 설정되지 않았다면 빈 문자열("")을 가집니다.
    @Value("${RAPIDAPI_KEY:}")
    private String rapidApiKey;

    private static final String RAPIDAPI_BASE_URL = "https://youtube-transcript3.p.rapidapi.com";
    private static final String RAPIDAPI_HOST = "youtube-transcript3.p.rapidapi.com";

    @Bean(name = "rapidApiWebClient")
    public WebClient rapidApiWebClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)   // 연결 타임아웃 5초
                .responseTimeout(Duration.ofSeconds(10));               // 응답 타임아웃 10초

        return builder
                .baseUrl(RAPIDAPI_BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("X-RapidAPI-Key", rapidApiKey)
                .defaultHeader("X-RapidAPI-Host", RAPIDAPI_HOST)
                .build();
    }
}
