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
@Table(name = "news", indexes = {
    @Index(name = "idx_news_published_date", columnList = "publishedDate"),
    @Index(name = "idx_news_source", columnList = "source"),
    @Index(name = "idx_news_keyword", columnList = "keyword"),
    @Index(name = "idx_news_category", columnList = "category")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "original_url", nullable = false, length = 2000)
    private String originalUrl;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String keyword;

    private String category;

    @Column(name = "published_date", nullable = false)
    private LocalDateTime publishedDate;

    @Column(name = "crawled_date", nullable = false)
    private LocalDateTime crawledDate;

    // 감성 분석 결과
    @Column(name = "sentiment_score")
    private Double sentimentScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "sentiment_label")
    private SentimentLabel sentimentLabel;

    // 경제 지표 관련성
    @ElementCollection
    @CollectionTable(name = "news_indicators", joinColumns = @JoinColumn(name = "news_id"))
    @Column(name = "indicator")
    @Builder.Default
    private List<String> relatedIndicators = new ArrayList<>();

    // 키워드 추출 결과
    @Column(name = "extracted_keywords", columnDefinition = "JSON")
    private String extractedKeywords; // JSON 배열 형태로 저장

    // 뉴스 품질 점수
    @Column(name = "quality_score")
    private Double qualityScore;

    // 중복 체크를 위한 해시
    @Column(name = "content_hash", unique = true)
    private String contentHash;

    // 처리 상태
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ProcessingStatus processingStatus = ProcessingStatus.RAW;

    // 언어
    @Builder.Default
    private String language = "ko";

    // 뉴스 수집 작업 ID
    @Column(name = "crawl_job_id")
    private String crawlJobId;

    // 통계 정보
    @Column(name = "view_count")
    @Builder.Default
    private Long viewCount = 0L;

    @Column(name = "analysis_count")
    @Builder.Default
    private Long analysisCount = 0L;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 관계 매핑
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collected_by_user_id")
    private User collectedByUser;

    @OneToMany(mappedBy = "news", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NewsAnalysis> analyses = new ArrayList<>();

    // 편의 메서드
    public void incrementViewCount() {
        this.viewCount++;
    }

    public void incrementAnalysisCount() {
        this.analysisCount++;
    }

    public boolean isProcessed() {
        return processingStatus == ProcessingStatus.COMPLETED;
    }

    public boolean hasSummary() {
        return summary != null && !summary.trim().isEmpty();
    }

    public boolean hasSentimentAnalysis() {
        return sentimentScore != null && sentimentLabel != null;
    }

    // Enum 정의
    public enum SentimentLabel {
        POSITIVE, NEGATIVE, NEUTRAL
    }

    public enum ProcessingStatus {
        RAW,           // 원본 수집 완료
        CONTENT_EXTRACTED, // 본문 추출 완료
        SUMMARIZED,    // 요약 완료
        ANALYZED,      // 감성 분석 완료
        COMPLETED,     // 모든 처리 완료
        FAILED         // 처리 실패
    }
}