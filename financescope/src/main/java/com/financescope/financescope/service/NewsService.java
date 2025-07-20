package com.financescope.financescope.service;

import com.financescope.financescope.dto.news.NewsDTO;
import com.financescope.financescope.entity.News;
import com.financescope.financescope.entity.User;
import com.financescope.financescope.exception.BusinessException;
import com.financescope.financescope.repository.NewsRepository;
import com.financescope.financescope.repository.UserRepository;
import com.financescope.financescope.service.external.NewsCrawlerService;
import com.financescope.financescope.service.external.SummarizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NewsService {

    private final NewsRepository newsRepository;
    private final UserRepository userRepository;
    private final NewsCrawlerService newsCrawlerService;
    private final SummarizationService summarizationService;
    private final CacheService cacheService;

    public NewsDTO.CrawlResponse startCrawling(NewsDTO.CrawlRequest request, String userEmail) {
        log.info("뉴스 크롤링 시작 - 사용자: {}, 키워드: {}", userEmail, request.getKeywords());

        User user = findUserByEmail(userEmail);

        // 구독 제한 확인
        validateCrawlingLimits(user, request);

        // 크롤링 작업 시작
        String jobId = UUID.randomUUID().toString();
        
        try {
            newsCrawlerService.startCrawling(jobId, request, user);
            
            return NewsDTO.CrawlResponse.builder()
                    .success(true)
                    .message("뉴스 크롤링이 시작되었습니다.")
                    .jobId(jobId)
                    .estimatedTime(calculateEstimatedTime(request))
                    .build();
        } catch (Exception e) {
            log.error("크롤링 시작 실패: {}", e.getMessage());
            throw new BusinessException("크롤링 시작에 실패했습니다: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public NewsDTO.CrawlStatusResponse getCrawlingStatus(String jobId, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        try {
            return newsCrawlerService.getCrawlingStatus(jobId, user);
        } catch (Exception e) {
            log.error("크롤링 상태 확인 실패: {}", e.getMessage());
            throw new BusinessException("크롤링 상태 확인에 실패했습니다.");
        }
    }

    public NewsDTO.CrawlResponse cancelCrawling(String jobId, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        try {
            newsCrawlerService.cancelCrawling(jobId, user);
            
            return NewsDTO.CrawlResponse.builder()
                    .success(true)
                    .message("크롤링이 취소되었습니다.")
                    .jobId(jobId)
                    .build();
        } catch (Exception e) {
            log.error("크롤링 취소 실패: {}", e.getMessage());
            throw new BusinessException("크롤링 취소에 실패했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public Page<NewsDTO.NewsResponse> getNews(Pageable pageable, String keyword, String source, 
                                               String category, String startDate, String endDate, 
                                               String userEmail) {
        User user = findUserByEmail(userEmail);
        
        LocalDateTime startDateTime = parseDateTime(startDate);
        LocalDateTime endDateTime = parseDateTime(endDate);
        
        Page<News> newsPage = newsRepository.findNewsByFilters(
                user, keyword, source, category, startDateTime, endDateTime, pageable);
        
        return newsPage.map(this::convertToNewsResponse);
    }

    @Transactional(readOnly = true)
    public NewsDTO.NewsResponse getNewsById(Long id, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new BusinessException("뉴스를 찾을 수 없습니다."));
        
        // 권한 확인
        if (!news.getCollectedByUser().equals(user)) {
            throw new BusinessException("접근 권한이 없습니다.");
        }
        
        // 조회수 증가
        news.incrementViewCount();
        newsRepository.save(news);
        
        return convertToNewsResponse(news);
    }

    public List<NewsDTO.SummaryResponse> summarizeNews(NewsDTO.SummarizeRequest request, String userEmail) {
        log.info("뉴스 요약 시작 - 사용자: {}, 뉴스 수: {}", userEmail, request.getNewsIds().size());

        User user = findUserByEmail(userEmail);
        
        // 뉴스 조회 및 권한 확인
        List<News> newsList = newsRepository.findAllById(request.getNewsIds());
        validateNewsOwnership(newsList, user);
        
        // 구독 제한 확인
        validateSummarizationLimits(user, newsList.size());
        
        List<NewsDTO.SummaryResponse> summaries = new ArrayList<>();
        
        for (News news : newsList) {
            try {
                String summary = summarizationService.summarize(news.getContent(), request.getModel(), request.getMaxLength());
                
                // 뉴스 엔티티 업데이트
                news.setSummary(summary);
                news.setProcessingStatus(News.ProcessingStatus.SUMMARIZED);
                newsRepository.save(news);
                
                summaries.add(NewsDTO.SummaryResponse.builder()
                        .newsId(news.getId())
                        .title(news.getTitle())
                        .summary(summary)
                        .originalSummary(news.getContent())
                        .source(news.getSource())
                        .date(news.getPublishedDate())
                        .keyword(news.getKeyword())
                        .build());
                        
            } catch (Exception e) {
                log.error("뉴스 요약 실패 - ID: {}, 오류: {}", news.getId(), e.getMessage());
                // 개별 실패는 로그만 남기고 계속 진행
            }
        }
        
        log.info("뉴스 요약 완료 - 성공: {}/{}", summaries.size(), newsList.size());
        return summaries;
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableSources() {
        // 캐시에서 조회
        String cacheKey = "available_sources";
        List<String> cachedSources = cacheService.get(cacheKey, List.class);
        
        if (cachedSources != null) {
            return cachedSources;
        }
        
        List<String> sources = Arrays.asList(
                "네이버", "다음", "구글", "한국경제", "매일경제", 
                "파이낸셜뉴스", "이데일리", "머니투데이"
        );
        
        // 캐시에 저장 (1시간)
        cacheService.put(cacheKey, sources, 3600);
        
        return sources;
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableCategories() {
        String cacheKey = "available_categories";
        List<String> cachedCategories = cacheService.get(cacheKey, List.class);
        
        if (cachedCategories != null) {
            return cachedCategories;
        }
        
        List<String> categories = Arrays.asList(
                "경제", "금융", "증권", "부동산", "산업", "국제경제", "정책"
        );
        
        cacheService.put(cacheKey, categories, 3600);
        return categories;
    }

    public void deleteNews(Long id, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new BusinessException("뉴스를 찾을 수 없습니다."));
        
        if (!news.getCollectedByUser().equals(user)) {
            throw new BusinessException("삭제 권한이 없습니다.");
        }
        
        newsRepository.delete(news);
        log.info("뉴스 삭제 완료 - ID: {}, 사용자: {}", id, userEmail);
    }

    public void deleteNewsInBatch(List<Long> newsIds, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        List<News> newsList = newsRepository.findAllById(newsIds);
        validateNewsOwnership(newsList, user);
        
        newsRepository.deleteAll(newsList);
        log.info("뉴스 일괄 삭제 완료 - 수: {}, 사용자: {}", newsIds.size(), userEmail);
    }

    public void addToFavorites(Long id, String userEmail) {
        // 즐겨찾기 기능 구현 (별도 테이블 필요)
        log.info("뉴스 즐겨찾기 추가 - ID: {}, 사용자: {}", id, userEmail);
        // TODO: 즐겨찾기 테이블 구현
    }

    public void removeFromFavorites(Long id, String userEmail) {
        // 즐겨찾기 제거 기능 구현
        log.info("뉴스 즐겨찾기 제거 - ID: {}, 사용자: {}", id, userEmail);
        // TODO: 즐겨찾기 테이블 구현
    }

    @Transactional(readOnly = true)
    public Page<NewsDTO.NewsResponse> getFavoriteNews(Pageable pageable, String userEmail) {
        // 즐겨찾기 뉴스 조회
        User user = findUserByEmail(userEmail);
        // TODO: 즐겨찾기 조회 구현
        return Page.empty();
    }

    @Transactional(readOnly = true)
    public Object getNewsStatistics(String userEmail, String period) {
        User user = findUserByEmail(userEmail);
        
        LocalDateTime startDate = calculateStartDate(period);
        Long newsCount = newsRepository.countNewsAfterDate(user, startDate);
        
        List<String> sources = newsRepository.findDistinctSourcesByUser(user);
        List<String> categories = newsRepository.findDistinctCategoriesByUser(user);
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalNews", newsCount);
        statistics.put("sources", sources);
        statistics.put("categories", categories);
        statistics.put("period", period);
        
        return statistics;
    }

    public Object exportNews(String format, List<Long> newsIds, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        List<News> newsList;
        if (newsIds != null && !newsIds.isEmpty()) {
            newsList = newsRepository.findAllById(newsIds);
            validateNewsOwnership(newsList, user);
        } else {
            newsList = newsRepository.findByCollectedByUser(user, Pageable.unpaged()).getContent();
        }
        
        // 파일 생성 및 임시 저장
        String fileName = generateExportFileName(format, userEmail);
        
        switch (format.toLowerCase()) {
            case "csv":
                return exportToCsv(newsList, fileName);
            case "excel":
                return exportToExcel(newsList, fileName);
            case "json":
                return exportToJson(newsList, fileName);
            default:
                throw new BusinessException("지원하지 않는 내보내기 형식입니다.");
        }
    }

    // Private helper methods
    
    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));
    }

    private void validateCrawlingLimits(User user, NewsDTO.CrawlRequest request) {
        if (user.getSubscriptionPlan() == User.SubscriptionPlan.FREE) {
            if (request.getMaxResults() > 100) {
                throw new BusinessException("무료 사용자는 최대 100개까지 수집할 수 있습니다.");
            }
        }
    }

    private void validateSummarizationLimits(User user, int newsCount) {
        if (user.getSubscriptionPlan() == User.SubscriptionPlan.FREE) {
            if (newsCount > 50) {
                throw new BusinessException("무료 사용자는 최대 50개까지 요약할 수 있습니다.");
            }
        }
    }

    private void validateNewsOwnership(List<News> newsList, User user) {
        boolean hasUnauthorized = newsList.stream()
                .anyMatch(news -> !news.getCollectedByUser().equals(user));
        
        if (hasUnauthorized) {
            throw new BusinessException("접근 권한이 없는 뉴스가 포함되어 있습니다.");
        }
    }

    private int calculateEstimatedTime(NewsDTO.CrawlRequest request) {
        // 예상 시간 계산 (초 단위)
        int baseTime = 30; // 기본 30초
        int keywordTime = request.getKeywords().size() * 10; // 키워드당 10초
        int resultTime = (request.getMaxResults() / 10); // 결과 10개당 1초
        
        return Math.max(baseTime + keywordTime + resultTime, 60); // 최소 1분
    }

    private LocalDateTime parseDateTime(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }
        
        try {
            return LocalDateTime.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateString + "T00:00:00");
            } catch (Exception ex) {
                log.warn("날짜 파싱 실패: {}", dateString);
                return null;
            }
        }
    }

    private LocalDateTime calculateStartDate(String period) {
        LocalDateTime now = LocalDateTime.now();
        
        if (period == null) {
            return now.minusDays(30); // 기본 30일
        }
        
        switch (period.toLowerCase()) {
            case "1d":
                return now.minusDays(1);
            case "7d":
                return now.minusDays(7);
            case "30d":
                return now.minusDays(30);
            case "90d":
                return now.minusDays(90);
            default:
                return now.minusDays(30);
        }
    }

    private String generateExportFileName(String format, String userEmail) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("news_export_%s_%s.%s", userEmail.split("@")[0], timestamp, format);
    }

    private Object exportToCsv(List<News> newsList, String fileName) {
        // CSV 내보내기 구현
        log.info("CSV 내보내기: {}", fileName);
        // TODO: CSV 파일 생성 로직
        return Map.of("fileName", fileName, "downloadUrl", "/api/downloads/" + fileName);
    }

    private Object exportToExcel(List<News> newsList, String fileName) {
        // Excel 내보내기 구현
        log.info("Excel 내보내기: {}", fileName);
        // TODO: Excel 파일 생성 로직
        return Map.of("fileName", fileName, "downloadUrl", "/api/downloads/" + fileName);
    }

    private Object exportToJson(List<News> newsList, String fileName) {
        // JSON 내보내기 구현
        log.info("JSON 내보내기: {}", fileName);
        // TODO: JSON 파일 생성 로직
        return Map.of("fileName", fileName, "downloadUrl", "/api/downloads/" + fileName);
    }

    private NewsDTO.NewsResponse convertToNewsResponse(News news) {
        return NewsDTO.NewsResponse.builder()
                .id(news.getId())
                .title(news.getTitle())
                .summary(news.getSummary())
                .originalUrl(news.getOriginalUrl())
                .source(news.getSource())
                .keyword(news.getKeyword())
                .category(news.getCategory())
                .publishedDate(news.getPublishedDate())
                .sentimentScore(news.getSentimentScore())
                .sentimentLabel(news.getSentimentLabel() != null ? news.getSentimentLabel().name() : null)
                .relatedIndicators(news.getRelatedIndicators())
                .extractedKeywords(parseJsonToList(news.getExtractedKeywords()))
                .build();
    }

    private List<String> parseJsonToList(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            // JSON 파싱 로직
            // 실제 구현에서는 ObjectMapper 사용
            return Arrays.asList(jsonString.split(","));
        } catch (Exception e) {
            log.warn("JSON 파싱 실패: {}", jsonString);
            return new ArrayList<>();
        }
    }
}
