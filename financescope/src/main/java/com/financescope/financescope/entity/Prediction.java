package com.financescope.financescope.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "predictions", indexes = {
    @Index(name = "idx_prediction_date", columnList = "predictionDate"),
    @Index(name = "idx_prediction_indicator", columnList = "indicator"),
    @Index(name = "idx_prediction_user", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String indicator; // 예측 대상 지표

    @Enumerated(EnumType.STRING)
    @Column(name = "model_type", nullable = false)
    private ModelType modelType;

    @Column(name = "prediction_date", nullable = false)
    private LocalDateTime predictionDate;

    @Column(name = "prediction_period_days")
    private Integer predictionPeriodDays;

    @Column(name = "prediction_period_end")
    private LocalDateTime predictionPeriodEnd;

    // 예측 결과 데이터 (JSON 형태)
    @Column(name = "forecast_data", columnDefinition = "JSON")
    private String forecastData; // 시계열 예측 결과

    @Column(name = "confidence_intervals", columnDefinition = "JSON")
    private String confidenceIntervals; // 신뢰구간 데이터

    @Column(name = "important_events", columnDefinition = "JSON")
    private String importantEvents; // 중요 이벤트/날짜

    // 모델 성능 지표
    @Column(name = "model_accuracy")
    private Double modelAccuracy;

    @Column(name = "mape") // Mean Absolute Percentage Error
    private Double mape;

    @Column(name = "rmse") // Root Mean Square Error
    private Double rmse;

    @Column(name = "r_squared")
    private Double rSquared;

    // 예측 신뢰도
    @Column(name = "overall_confidence")
    private Double overallConfidence;

    @Enumerated(EnumType.STRING)
    private TrendDirection predictedTrend;

    @Column(name = "sentiment_influence")
    private Double sentimentInfluence; // 뉴스 감성이 예측에 미친 영향도

    // 사용된 뉴스 데이터
    @Column(name = "news_count")
    private Integer newsCount;

    @Column(name = "news_date_range_start")
    private LocalDateTime newsDateRangeStart;

    @Column(name = "news_date_range_end")
    private LocalDateTime newsDateRangeEnd;

    // 예측 실행 정보
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PredictionStatus status = PredictionStatus.PENDING;

    @Column(name = "error_message")
    private String errorMessage;

    // 실제 결과 (예측 후 검증용)
    @Column(name = "actual_values", columnDefinition = "JSON")
    private String actualValues; // 실제 발생한 값들

    @Column(name = "accuracy_score")
    private Double accuracyScore; // 예측 정확도 점수

    @Column(name = "validation_date")
    private LocalDateTime validationDate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 관련 분석들
    @ManyToMany
    @JoinTable(
        name = "prediction_indicator_analyses",
        joinColumns = @JoinColumn(name = "prediction_id"),
        inverseJoinColumns = @JoinColumn(name = "analysis_id")
    )
    @Builder.Default
    private List<IndicatorAnalysis> usedAnalyses = new ArrayList<>();

    public enum ModelType {
        ARIMA, PROPHET, LSTM, XGBOOST, ENSEMBLE
    }

    public enum TrendDirection {
        RISING, FALLING, STABLE, VOLATILE
    }

    public enum PredictionStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }
}
