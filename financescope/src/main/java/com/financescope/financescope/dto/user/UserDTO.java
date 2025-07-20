package com.financescope.financescope.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class UserDTO {

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfileResponse {
        private Long id;
        private String email;
        private String name;
        private String organization;
        private String jobTitle;
        private String profileImage;
        private String role;
        private String status;
        private String subscriptionPlan;
        private LocalDateTime subscriptionExpiresAt;
        private LocalDateTime createdAt;
        private LocalDateTime lastLogin;
        private Long loginCount;
        private UserPreferences preferences;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserPreferences {
        private List<String> defaultIndicators;
        private String defaultModel;
        private Boolean emailNotifications;
        private String theme;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateProfileRequest {
        @Size(max = 100, message = "이름은 100자를 초과할 수 없습니다")
        private String name;

        @Size(max = 200, message = "소속 조직은 200자를 초과할 수 없습니다")
        private String organization;

        @Size(max = 100, message = "직위는 100자를 초과할 수 없습니다")
        private String jobTitle;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisHistoryResponse {
        private Long id;
        private String name;
        private LocalDateTime date;
        private List<String> indicators;
        private Integer newsCount;
        private Double accuracy;
        private String analysisType;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelPerformanceStatsResponse {
        private Integer predictionsTotal;
        private Double averageAccuracy;
        private Map<String, ModelStats> models;
        private List<RecentPrediction> recentPredictions;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelStats {
        private Integer count;
        private Double accuracy;
        private Map<String, Double> indicators;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentPrediction {
        private Long id;
        private LocalDateTime date;
        private String indicator;
        private String model;
        private Double accuracy;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SavedSettingsResponse {
        private Long id;
        private String name;
        private LocalDateTime created;
        private Boolean isDefault;
        private NewsConfig newsConfig;
        private IndicatorConfig indicatorConfig;
        private PredictionConfig predictionConfig;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewsConfig {
        private String period;
        private List<String> keywords;
        private List<String> sources;
        private Integer limit;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndicatorConfig {
        private List<String> indicators;
        private Boolean useSentiment;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionConfig {
        private String model;
        private String period;
    }
}
