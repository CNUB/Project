package com.financescope.financescope.dto.prediction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class PredictionDTO {

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionRequest {
        @NotBlank(message = "예측 대상 지표를 선택해주세요")
        private String indicator;

        @NotBlank(message = "예측 모델을 선택해주세요")
        private String model; // ARIMA, PROPHET, LSTM, XGBOOST, ENSEMBLE

        @Positive(message = "예측 기간은 양수여야 합니다")
        @Builder.Default
        private Integer predictionDays = 7;

        @Builder.Default
        private Boolean useSummary = true;
        private List<Long> newsIds;
        private List<Long> analysisIds;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionResponse {
        private Long id;
        private String indicator;
        private String model;
        private LocalDateTime predictionDate;
        private Integer predictionDays;
        private List<ForecastPoint> forecastData;
        private List<ImportantEvent> importantDates;
        private List<String> keywords;
        private String sentiment;
        private Double confidence;
        private String trend;
        private ModelPerformance modelPerformance;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastPoint {
        private String date;
        private Double value;
        private Boolean isActual;
        private Double upper;
        private Double lower;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportantEvent {
        private String date;
        private String event;
        private String type; // PREDICTION, NEWS, ECONOMIC_EVENT
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelPerformance {
        private Double accuracy;
        private Double mape;
        private Double rmse;
        private Double rSquared;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionStatusResponse {
        private Long id;
        private String status;
        private Integer progress;
        private String message;
        private Long processingTimeMs;
        private String error;
    }
}