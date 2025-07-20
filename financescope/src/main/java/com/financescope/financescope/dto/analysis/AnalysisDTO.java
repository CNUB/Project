package com.financescope.financescope.dto.analysis;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class AnalysisDTO {

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndicatorAnalysisRequest {
        @NotEmpty(message = "분석할 지표를 선택해주세요")
        private List<String> indicators;

        @NotEmpty(message = "분석할 뉴스가 없습니다")
        private List<Long> newsIds;

        @Builder.Default
        private Boolean useSentiment = true;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndicatorAnalysisResponse {
        private String indicator;
        private Integer totalMentions;
        private Map<String, List<DailyAnalysis>> groupedByDate;
        private String trend;
        private Double confidence;
        private Double averageSentiment;
        private Map<String, Integer> sourceDistribution;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyAnalysis {
        private String title;
        private String keyword;
        private Double sentiment;
        private String summary;
        private String source;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentAnalysisRequest {
        @NotEmpty(message = "분석할 뉴스가 없습니다")
        private List<Long> newsIds;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentAnalysisResponse {
        private Long newsId;
        private String title;
        private LocalDateTime date;
        private Double sentimentScore;
        private String sentimentLabel;
        private String originalSummary;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusteringRequest {
        @NotEmpty(message = "클러스터링할 뉴스가 없습니다")
        private List<Long> newsIds;

        @Builder.Default
        private Integer clusterCount = 5;
        @Builder.Default
        private String method = "kmeans"; // kmeans, hierarchical
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusteringResponse {
        private String id;
        private String name;
        private List<String> keywords;
        private Integer count;
        private String sentiment;
        private List<NewsClusterItem> news;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewsClusterItem {
        private Long id;
        private String title;
        private String summary;
        private LocalDateTime date;
        private String source;
    }
}