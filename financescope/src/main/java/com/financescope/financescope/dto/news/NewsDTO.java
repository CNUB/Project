package com.financescope.financescope.dto.news;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class NewsDTO {

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrawlRequest {
        @NotEmpty(message = "키워드는 최소 1개 이상 필요합니다")
        private List<String> keywords;

        private LocalDateTime startDate;
        private LocalDateTime endDate;

        @NotBlank(message = "뉴스 소스는 필수입니다")
        private String source; // naver, google, etc.

        @Builder.Default
        private String sortBy = "relevance"; // relevance, latest, popularity

        @Positive(message = "최대 결과 수는 양수여야 합니다")
        @Builder.Default
        private Integer maxResults = 100;

        private List<String> categories;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrawlResponse {
        private Boolean success;
        private String message;
        private String jobId;
        private Integer estimatedTime;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrawlStatusResponse {
        private String jobId;
        private String status;
        private Integer progress;
        private String message;
        private Integer newsCount;
        private Boolean completed;
        private String error;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewsResponse {
        private Long id;
        private String title;
        private String summary;
        private String originalUrl;
        private String source;
        private String keyword;
        private String category;
        private LocalDateTime publishedDate;
        private Double sentimentScore;
        private String sentimentLabel;
        private List<String> relatedIndicators;
        private List<String> extractedKeywords;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummarizeRequest {
        @NotEmpty(message = "요약할 뉴스가 없습니다")
        private List<Long> newsIds;

        @Builder.Default
        private String model = "KoBART"; // KoBART, KoBERT, T5
        @Builder.Default
        private Integer maxLength = 150;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryResponse {
        private Long newsId;
        private String title;
        private String summary;
        private String originalSummary;
        private String source;
        private LocalDateTime date;
        private String keyword;
    }
}