package com.financescope.financescope.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    /**
     * WebClient.Builder 빈 등록 (기본 설정)
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(configurer -> 
                    configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) // 10MB
                );
    }

    /**
     * 뉴스 크롤링용 WebClient
     */
    @Bean("newsCrawlerWebClient")
    public WebClient newsCrawlerWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl("https://api.example.com") // 실제 뉴스 API URL로 변경
                .defaultHeader("User-Agent", "FinanceScope-Crawler/1.0")
                .build();
    }

    /**
     * 감성 분석용 WebClient
     */
    @Bean("sentimentAnalysisWebClient")
    public WebClient sentimentAnalysisWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl("https://sentiment-api.example.com") // 실제 감성 분석 API URL로 변경
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 요약 서비스용 WebClient
     */
    @Bean("summarizationWebClient")
    public WebClient summarizationWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl("https://summarization-api.example.com") // 실제 요약 API URL로 변경
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}