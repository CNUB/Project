package com.financescope.financescope.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ReportDTO {

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportGenerationRequest {
        @NotBlank(message = "리포트 유형을 선택해주세요")
        private String reportType; // summary, detailed, executive, presentation

        @NotBlank(message = "다운로드 형식을 선택해주세요")
        private String format; // pdf, docx, pptx, xlsx, csv

        @NotEmpty(message = "포함할 섹션을 선택해주세요")
        private List<String> includeSections; // news_summary, indicator_analysis, prediction, clustering, network

        private List<Long> newsIds;
        private List<Long> analysisIds;
        private List<Long> predictionIds;
        
        private String title;
        private String description;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportGenerationResponse {
        private Boolean success;
        private String message;
        private String reportId;
        private String downloadUrl;
        private LocalDateTime expiresAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkMapRequest {
        private List<Long> analysisIds;
        private List<Long> newsIds;

        @Builder.Default
        private Integer maxNodes = 100;

        @Builder.Default
        private Double minConnectionStrength = 0.1;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkMapResponse {
        private List<NetworkNode> nodes;
        private List<NetworkLink> links;
        private Map<String, Object> statistics;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkNode {
        private String id;
        private String type; // indicator, news
        private String name;
        private Double value;
        private String category;
        private Map<String, Object> properties;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkLink {
        private String source;
        private String target;
        private Double value;
        private String type;
        private Double sentiment;
    }
}