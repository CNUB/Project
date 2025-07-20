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
@Table(name = "indicator_analysis", indexes = {
    @Index(name = "idx_indicator_analysis_date", columnList = "analysisDate"),
    @Index(name = "idx_indicator_analysis_indicator", columnList = "indicator"),
    @Index(name = "idx_indicator_analysis_user", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class IndicatorAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String indicator; // interest_rate, exchange_rate, kospi 등

    @Column(name = "analysis_date", nullable = false)
    private LocalDateTime analysisDate;

    @Column(name = "date_range_start")
    private LocalDateTime dateRangeStart;

    @Column(name = "date_range_end")
    private LocalDateTime dateRangeEnd;

    @Column(name = "total_mentions")
    private Integer totalMentions;

    @Column(name = "positive_mentions")
    private Integer positiveMentions;

    @Column(name = "negative_mentions")
    private Integer negativeMentions;

    @Column(name = "neutral_mentions")
    private Integer neutralMentions;

    @Column(name = "average_sentiment")
    private Double averageSentiment;

    @Enumerated(EnumType.STRING)
    private TrendDirection trend;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    // 상세 분석 결과 (JSON 형태)
    @Column(name = "daily_data", columnDefinition = "JSON")
    private String dailyData; // 날짜별 상세 데이터

    @Column(name = "keywords_data", columnDefinition = "JSON")
    private String keywordsData; // 키워드별 분석 데이터

    @Column(name = "source_distribution", columnDefinition = "JSON")
    private String sourceDistribution; // 뉴스 소스별 분포

    // 관련 뉴스들
    @ManyToMany
    @JoinTable(
        name = "indicator_analysis_news",
        joinColumns = @JoinColumn(name = "analysis_id"),
        inverseJoinColumns = @JoinColumn(name = "news_id")
    )
    @Builder.Default
    private List<News> relatedNews = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum TrendDirection {
        RISING, FALLING, STABLE, VOLATILE
    }
}
