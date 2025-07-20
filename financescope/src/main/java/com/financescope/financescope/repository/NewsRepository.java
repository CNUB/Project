package com.financescope.financescope.repository;

import com.financescope.financescope.entity.News;
import com.financescope.financescope.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NewsRepository extends JpaRepository<News, Long> {
    
    // ✅ 수정: 사용자별 뉴스 조회 (필드명 수정)
    List<News> findByCollectedByUser_IdOrderByCreatedAtDesc(Long userId);
    
    // ✅ 키워드별 뉴스 조회 (정상)
    List<News> findByKeywordContainingIgnoreCase(String keyword);
    
    // ✅ 출처별 뉴스 조회 (정상)
    List<News> findBySource(String source);
    
    // ✅ 카테고리별 뉴스 조회 (정상)
    List<News> findByCategory(String category);
    
    // ✅ 감성별 뉴스 조회 (정상)
    List<News> findBySentimentLabel(News.SentimentLabel sentimentLabel);
    
    // ✅ 수정: 특정 기간 내 뉴스 조회 (publishedAt → publishedDate)
    @Query("SELECT n FROM News n WHERE n.publishedDate BETWEEN :startDate AND :endDate ORDER BY n.publishedDate DESC")
    List<News> findByPublishedDateBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
    
    // ✅ 수정: 사용자별 특정 키워드 뉴스 조회 (user → collectedByUser)
    @Query("SELECT n FROM News n WHERE n.collectedByUser.id = :userId AND n.keyword LIKE %:keyword% ORDER BY n.createdAt DESC")
    List<News> findByUserIdAndKeywordContaining(
            @Param("userId") Long userId,
            @Param("keyword") String keyword
    );
    
    // ✅ 감성 점수 범위별 뉴스 조회 (정상)
    @Query("SELECT n FROM News n WHERE n.sentimentScore BETWEEN :minScore AND :maxScore")
    List<News> findBySentimentScoreBetween(
            @Param("minScore") Double minScore,
            @Param("maxScore") Double maxScore
    );
    
    // ✅ 수정: 사용자별 뉴스 통계 (user → collectedByUser)
    @Query("SELECT COUNT(n), AVG(n.sentimentScore) FROM News n WHERE n.collectedByUser.id = :userId")
    Object[] getNewsStatsByUserId(@Param("userId") Long userId);
    
    // ✅ 수정: 중복 URL 확인 (url → originalUrl)
    boolean existsByOriginalUrl(String originalUrl);
    
    // ✅ 제목으로 유사 뉴스 검색 (정상)
    @Query("SELECT n FROM News n WHERE LOWER(n.title) LIKE LOWER(CONCAT('%', :title, '%'))")
    List<News> findSimilarNewsByTitle(@Param("title") String title);
    
    // ✅ 최근 요약된 뉴스 조회 (정상)
    @Query("SELECT n FROM News n WHERE n.summary IS NOT NULL ORDER BY n.createdAt DESC")
    List<News> findRecentSummarizedNews();
    
    // ✅ 수정: 다양한 필터 조건으로 뉴스 검색 (user → collectedByUser, publishedAt → publishedDate)
    @Query("SELECT n FROM News n WHERE " +
           "(:user IS NULL OR n.collectedByUser = :user) AND " +
           "(:keyword IS NULL OR LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(n.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:source IS NULL OR n.source = :source) AND " +
           "(:category IS NULL OR n.category = :category) AND " +
           "(:startDate IS NULL OR n.publishedDate >= :startDate) AND " +
           "(:endDate IS NULL OR n.publishedDate <= :endDate) " +
           "ORDER BY n.publishedDate DESC")
    Page<News> findNewsByFilters(@Param("user") User user,
                                @Param("keyword") String keyword,
                                @Param("source") String source,
                                @Param("category") String category,
                                @Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate,
                                Pageable pageable);
    
    // ✅ 수정: 특정 날짜 이후 사용자별 뉴스 개수 조회 (user → collectedByUser, publishedAt → publishedDate)
    @Query("SELECT COUNT(n) FROM News n WHERE n.collectedByUser = :user AND n.publishedDate >= :startDate")
    Long countNewsAfterDate(@Param("user") User user, @Param("startDate") LocalDateTime startDate);
    
    // ✅ 수정: 사용자별 고유 뉴스 출처 목록 조회 (user → collectedByUser)
    @Query("SELECT DISTINCT n.source FROM News n WHERE n.collectedByUser = :user AND n.source IS NOT NULL ORDER BY n.source")
    List<String> findDistinctSourcesByUser(@Param("user") User user);
    
    // ✅ 수정: 사용자별 고유 뉴스 카테고리 목록 조회 (user → collectedByUser)
    @Query("SELECT DISTINCT n.category FROM News n WHERE n.collectedByUser = :user AND n.category IS NOT NULL ORDER BY n.category")
    List<String> findDistinctCategoriesByUser(@Param("user") User user);
    
    // ✅ 수정: 사용자가 수집한 뉴스 조회 (user → collectedByUser)
    @Query("SELECT n FROM News n WHERE n.collectedByUser = :user ORDER BY n.createdAt DESC")
    Page<News> findByCollectedByUser(@Param("user") User user, Pageable pageable);
    
    // ✅ 수정: 특정 날짜 범위와 사용자로 뉴스 조회 (publishedAt → publishedDate, user → collectedByUser)
    @Query("SELECT n FROM News n WHERE n.publishedDate BETWEEN :startDate AND :endDate AND n.collectedByUser = :user ORDER BY n.publishedDate DESC")
    List<News> findByDateRangeAndUser(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate,
                                     @Param("user") User user);
    
    // ✅ 수정: 사용자별 최근 뉴스 조회 (user → collectedByUser)
    @Query("SELECT n FROM News n WHERE n.collectedByUser = :user ORDER BY n.createdAt DESC")
    List<News> findTopNewsByUser(@Param("user") User user, Pageable pageable);
    
    // ✅ 수정: 특정 키워드를 포함한 뉴스 검색 (user → collectedByUser, publishedAt → publishedDate)
    @Query("SELECT n FROM News n WHERE n.collectedByUser = :user AND " +
           "(LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(n.content) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(n.keyword) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY n.publishedDate DESC")
    List<News> findByUserAndKeywordSearch(@Param("user") User user, @Param("keyword") String keyword);
    
    // ✅ 수정: 감성 점수별 뉴스 통계 (user → collectedByUser)
    @Query("SELECT n.sentimentLabel, COUNT(n), AVG(n.sentimentScore) " +
           "FROM News n WHERE n.collectedByUser = :user AND n.sentimentScore IS NOT NULL " +
           "GROUP BY n.sentimentLabel")
    List<Object[]> getSentimentStatsByUser(@Param("user") User user);
    
    // ✅ 수정: 월별 뉴스 수집 통계 (user → collectedByUser)
    @Query("SELECT YEAR(n.createdAt), MONTH(n.createdAt), COUNT(n) " +
           "FROM News n WHERE n.collectedByUser = :user " +
           "GROUP BY YEAR(n.createdAt), MONTH(n.createdAt) " +
           "ORDER BY YEAR(n.createdAt) DESC, MONTH(n.createdAt) DESC")
    List<Object[]> getMonthlyNewsStatsByUser(@Param("user") User user);
    
    // 🚀 추가 유용한 메서드들
    
    // 처리 상태별 조회
    List<News> findByProcessingStatusOrderByCreatedAtDesc(News.ProcessingStatus status);
    
    // 사용자 + 처리 상태 조회
    @Query("SELECT n FROM News n WHERE n.collectedByUser.id = :userId AND n.processingStatus = :status")
    List<News> findByUserAndProcessingStatus(
        @Param("userId") Long userId, 
        @Param("status") News.ProcessingStatus status
    );
    
    // 중복 체크 (contentHash 사용)
    Optional<News> findByContentHash(String contentHash);
    
    // 크롤 작업 ID로 조회
    List<News> findByCrawlJobIdOrderByCreatedAtDesc(String crawlJobId);
    
    // 최근 뉴스 조회 (시간 기준)
    @Query("SELECT n FROM News n WHERE n.collectedByUser.id = :userId " +
           "AND n.createdAt >= :since ORDER BY n.createdAt DESC")
    List<News> findRecentNewsByUser(
        @Param("userId") Long userId, 
        @Param("since") LocalDateTime since
    );
    
    // 품질 점수별 조회
    @Query("SELECT n FROM News n WHERE n.collectedByUser.id = :userId " +
           "AND n.qualityScore >= :minQuality ORDER BY n.qualityScore DESC")
    List<News> findHighQualityNewsByUser(
        @Param("userId") Long userId, 
        @Param("minQuality") Double minQuality
    );
}