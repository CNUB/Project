package com.financescope.financescope.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_history", indexes = {
    @Index(name = "idx_analysis_history_user", columnList = "user_id"),
    @Index(name = "idx_analysis_history_date", columnList = "createdAt")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AnalysisHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name; // 분석 이름

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type")
    private AnalysisType analysisType;

    @Column(name = "indicators", columnDefinition = "JSON")
    private String indicators; // 분석한 지표들

    @Column(name = "news_count")
    private Integer newsCount;

    @Column(name = "accuracy_score")
    private Double accuracyScore;

    @Column(name = "config_data", columnDefinition = "JSON")
    private String configData; // 분석 설정 정보

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    // 관련 데이터 참조
    @Column(name = "prediction_id")
    private Long predictionId;

    @Column(name = "indicator_analysis_ids", columnDefinition = "JSON")
    private String indicatorAnalysisIds;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum AnalysisType {
        NEWS_ANALYSIS, INDICATOR_ANALYSIS, PREDICTION, COMPREHENSIVE
    }
}