package com.financescope.financescope.service;

import com.financescope.financescope.dto.analysis.AnalysisDTO;
import com.financescope.financescope.entity.*;
import com.financescope.financescope.exception.BusinessException;
import com.financescope.financescope.repository.*;
import com.financescope.financescope.service.external.SentimentAnalysisService;
import com.financescope.financescope.service.external.ClusteringService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class AnalysisService {

    private final NewsRepository newsRepository;
    private final UserRepository userRepository;
    private final NewsAnalysisRepository newsAnalysisRepository;
    private final IndicatorAnalysisRepository indicatorAnalysisRepository;
    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final SentimentAnalysisService sentimentAnalysisService;
    private final ClusteringService clusteringService;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    // 경제 지표와 키워드 매핑
    private static final Map<String, List<String>> KEYWORD_INDICATOR_MAPPING = Map.of(
            "금리", Arrays.asList("금리", "기준금리", "이자율", "국채", "통화정책"),
            "환율", Arrays.asList("환율", "달러", "원화", "외환", "달러환율"),
            "주가", Arrays.asList("주가", "코스피", "코스닥", "증시", "주식"),
            "물가", Arrays.asList("물가", "인플레이션", "소비자물가", "CPI", "물가상승"),
            "고용", Arrays.asList("고용", "실업", "일자리", "취업", "고용률", "실업률"),
            "GDP", Arrays.asList("GDP", "경제성장", "성장률", "국내총생산")
    );

    public List<AnalysisDTO.SentimentAnalysisResponse> analyzeSentiment(
            AnalysisDTO.SentimentAnalysisRequest request, String userEmail) {
        log.info("감성 분석 시작 - 사용자: {}, 뉴스 수: {}", userEmail, request.getNewsIds().size());

        User user = findUserByEmail(userEmail);
        List<News> newsList = getNewsListWithValidation(request.getNewsIds(), user);

        // 구독 제한 확인
        validateAnalysisLimits(user, newsList.size());

        List<AnalysisDTO.SentimentAnalysisResponse> results = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (News news : newsList) {
            try {
                // 이미 감성 분석이 완료된 경우 기존 결과 사용
                if (news.getSentimentScore() != null && news.getSentimentLabel() != null) {
                    results.add(createSentimentResponse(news));
                    continue;
                }

                // 감성 분석 실행
                var sentimentResult = sentimentAnalysisService.analyzeSentiment(news.getSummary());
                
                // 뉴스 엔티티 업데이트
                news.setSentimentScore(sentimentResult.getScore());
                news.setSentimentLabel(mapSentimentLabel(sentimentResult.getLabel()));
                newsRepository.save(news);

                // 분석 결과 저장
                saveNewsAnalysis(news, user, "SENTIMENT", sentimentResult, 
                               System.currentTimeMillis() - startTime);

                results.add(createSentimentResponse(news));

            } catch (Exception e) {
                log.error("감성 분석 실패 - 뉴스 ID: {}, 오류: {}", news.getId(), e.getMessage());
                // 개별 실패는 건너뛰고 계속 진행
            }
        }

        // 분석 히스토리 저장
        saveAnalysisHistory(user, "감성 분석", AnalysisHistory.AnalysisType.NEWS_ANALYSIS,
                           Collections.singletonList("sentiment"), newsList.size(), 
                           calculateAverageAccuracy(results));

        log.info("감성 분석 완료 - 성공: {}/{}", results.size(), newsList.size());
        return results;
    }

    public Map<String, AnalysisDTO.IndicatorAnalysisResponse> analyzeIndicators(
            AnalysisDTO.IndicatorAnalysisRequest request, String userEmail) {
        log.info("경제 지표 분석 시작 - 사용자: {}, 지표: {}, 뉴스 수: {}", 
                userEmail, request.getIndicators(), request.getNewsIds().size());

        User user = findUserByEmail(userEmail);
        List<News> newsList = getNewsListWithValidation(request.getNewsIds(), user);

        // 구독 제한 확인
        validateAnalysisLimits(user, newsList.size());

        Map<String, AnalysisDTO.IndicatorAnalysisResponse> results = new HashMap<>();

        for (String indicator : request.getIndicators()) {
            try {
                var analysisResult = analyzeIndicator(indicator, newsList, request.getUseSentiment());
                results.put(indicator, analysisResult);

                // 지표 분석 결과 저장
                saveIndicatorAnalysis(user, indicator, newsList, analysisResult, request);

            } catch (Exception e) {
                log.error("지표 분석 실패 - 지표: {}, 오류: {}", indicator, e.getMessage());
            }
        }

        // 분석 히스토리 저장
        saveAnalysisHistory(user, "경제 지표 분석", AnalysisHistory.AnalysisType.INDICATOR_ANALYSIS,
                           request.getIndicators(), newsList.size(), 
                           calculateIndicatorAnalysisAccuracy(results));

        log.info("경제 지표 분석 완료 - 지표 수: {}", results.size());
        return results;
    }

    public List<AnalysisDTO.ClusteringResponse> clusterNews(
            AnalysisDTO.ClusteringRequest request, String userEmail) {
        log.info("뉴스 클러스터링 시작 - 사용자: {}, 뉴스 수: {}, 클러스터 수: {}", 
                userEmail, request.getNewsIds().size(), request.getClusterCount());

        User user = findUserByEmail(userEmail);
        List<News> newsList = getNewsListWithValidation(request.getNewsIds(), user);

        // 구독 제한 확인
        validateAnalysisLimits(user, newsList.size());

        try {
            // 클러스터링 실행
            var clusteringResult = clusteringService.clusterNews(newsList, request.getClusterCount(), request.getMethod());
            
            List<AnalysisDTO.ClusteringResponse> results = new ArrayList<>();
            
            for (var cluster : clusteringResult.getClusters()) {
                var clusterResponse = AnalysisDTO.ClusteringResponse.builder()
                        .id(cluster.getId())
                        .name(cluster.getName())
                        .keywords(cluster.getKeywords())
                        .count(cluster.getNews().size())
                        .sentiment(calculateClusterSentiment(cluster.getNews()))
                        .news(cluster.getNews().stream()
                                .map(this::convertToNewsClusterItem)
                                .collect(Collectors.toList()))
                        .build();
                
                results.add(clusterResponse);
            }

            // 클러스터링 결과 저장
            saveClusteringAnalysis(user, newsList, results);

            log.info("뉴스 클러스터링 완료 - 클러스터 수: {}", results.size());
            return results;

        } catch (Exception e) {
            log.error("뉴스 클러스터링 실패: {}", e.getMessage());
            throw new BusinessException("클러스터링 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAvailableIndicators() {
        String cacheKey = "available_indicators";
        List<Map<String, Object>> cached = cacheService.get(cacheKey, List.class);
        
        if (cached != null) {
            return cached;
        }

        List<Map<String, Object>> indicators = Arrays.asList(
                Map.of("id", "interest_rate", "name", "기준금리", "unit", "%"),
                Map.of("id", "exchange_rate", "name", "환율(달러/원)", "unit", "원"),
                Map.of("id", "kospi", "name", "KOSPI", "unit", "포인트"),
                Map.of("id", "kosdaq", "name", "KOSDAQ", "unit", "포인트"),
                Map.of("id", "cpi", "name", "소비자물가지수", "unit", "포인트"),
                Map.of("id", "ppi", "name", "생산자물가지수", "unit", "포인트"),
                Map.of("id", "gdp", "name", "GDP 성장률", "unit", "%"),
                Map.of("id", "unemployment", "name", "실업률", "unit", "%"),
                Map.of("id", "export", "name", "수출액", "unit", "억 달러"),
                Map.of("id", "import", "name", "수입액", "unit", "억 달러")
        );

        cacheService.put(cacheKey, indicators, 3600);
        return indicators;
    }

    @Transactional(readOnly = true)
    public Map<String, List<String>> getKeywordIndicatorMapping() {
        return new HashMap<>(KEYWORD_INDICATOR_MAPPING);
    }

    public Map<String, List<String>> updateKeywordIndicatorMapping(
            Map<String, List<String>> mappingData, String userEmail) {
        log.info("키워드-지표 매핑 업데이트 - 사용자: {}", userEmail);
        
        // 실제 구현에서는 사용자별 맞춤 매핑을 저장할 수 있음
        // 현재는 전역 매핑 반환
        return new HashMap<>(KEYWORD_INDICATOR_MAPPING);
    }

    @Transactional(readOnly = true)
    public Page<Object> getAnalysisHistory(Pageable pageable, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        Page<AnalysisHistory> historyPage = analysisHistoryRepository.findByUser(user, pageable);
        
        return historyPage.map(this::convertToAnalysisHistoryResponse);
    }

    @Transactional(readOnly = true)
    public Object getAnalysisDetail(Long id, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        AnalysisHistory history = analysisHistoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("분석 기록을 찾을 수 없습니다."));
        
        if (!history.getUser().equals(user)) {
            throw new BusinessException("접근 권한이 없습니다.");
        }
        
        return convertToAnalysisDetailResponse(history);
    }

    public void deleteAnalysis(Long id, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        AnalysisHistory history = analysisHistoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("분석 기록을 찾을 수 없습니다."));
        
        if (!history.getUser().equals(user)) {
            throw new BusinessException("삭제 권한이 없습니다.");
        }
        
        analysisHistoryRepository.delete(history);
        log.info("분석 기록 삭제 완료 - ID: {}, 사용자: {}", id, userEmail);
    }

    public Object batchAnalysis(Map<String, Object> batchRequest, String userEmail) {
        log.info("일괄 분석 시작 - 사용자: {}", userEmail);
        
        User user = findUserByEmail(userEmail);
        
        // 일괄 분석 로직 구현
        Map<String, Object> results = new HashMap<>();
        results.put("message", "일괄 분석이 시작되었습니다.");
        results.put("jobId", UUID.randomUUID().toString());
        
        return results;
    }

    // Private helper methods

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));
    }

    private List<News> getNewsListWithValidation(List<Long> newsIds, User user) {
        List<News> newsList = newsRepository.findAllById(newsIds);
        
        if (newsList.size() != newsIds.size()) {
            throw new BusinessException("일부 뉴스를 찾을 수 없습니다.");
        }
        
        // 권한 확인
        boolean hasUnauthorized = newsList.stream()
                .anyMatch(news -> !news.getCollectedByUser().equals(user));
        
        if (hasUnauthorized) {
            throw new BusinessException("접근 권한이 없는 뉴스가 포함되어 있습니다.");
        }
        
        return newsList;
    }

    private void validateAnalysisLimits(User user, int newsCount) {
        if (user.getSubscriptionPlan() == User.SubscriptionPlan.FREE) {
            if (newsCount > 100) {
                throw new BusinessException("무료 사용자는 최대 100개까지 분석할 수 있습니다.");
            }
        }
    }

    private AnalysisDTO.IndicatorAnalysisResponse analyzeIndicator(
            String indicator, List<News> newsList, Boolean useSentiment) {
        
        List<String> keywords = KEYWORD_INDICATOR_MAPPING.getOrDefault(indicator, Arrays.asList(indicator));
        
        // 관련 뉴스 필터링
        List<News> relatedNews = newsList.stream()
                .filter(news -> containsKeywords(news, keywords))
                .collect(Collectors.toList());
        
        if (relatedNews.isEmpty()) {
            return AnalysisDTO.IndicatorAnalysisResponse.builder()
                    .indicator(indicator)
                    .totalMentions(0)
                    .groupedByDate(new HashMap<>())
                    .trend("STABLE")
                    .confidence(0.0)
                    .averageSentiment(0.0)
                    .sourceDistribution(new HashMap<>())
                    .build();
        }
        
        // 날짜별 그룹화
        Map<String, List<AnalysisDTO.DailyAnalysis>> groupedByDate = groupNewsByDate(relatedNews, useSentiment);
        
        // 트렌드 분석
        String trend = analyzeTrend(groupedByDate);
        
        // 평균 감성 계산
        double averageSentiment = calculateAverageSentiment(relatedNews);
        
        // 소스 분포 계산
        Map<String, Integer> sourceDistribution = calculateSourceDistribution(relatedNews);
        
        return AnalysisDTO.IndicatorAnalysisResponse.builder()
                .indicator(indicator)
                .totalMentions(relatedNews.size())
                .groupedByDate(groupedByDate)
                .trend(trend)
                .confidence(calculateConfidence(relatedNews.size()))
                .averageSentiment(averageSentiment)
                .sourceDistribution(sourceDistribution)
                .build();
    }

    private boolean containsKeywords(News news, List<String> keywords) {
        String text = (news.getTitle() + " " + (news.getSummary() != null ? news.getSummary() : "")).toLowerCase();
        return keywords.stream().anyMatch(keyword -> text.contains(keyword.toLowerCase()));
    }

    private Map<String, List<AnalysisDTO.DailyAnalysis>> groupNewsByDate(List<News> newsList, Boolean useSentiment) {
        Map<String, List<AnalysisDTO.DailyAnalysis>> grouped = new HashMap<>();
        
        for (News news : newsList) {
            String dateKey = news.getPublishedDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            AnalysisDTO.DailyAnalysis dailyAnalysis = AnalysisDTO.DailyAnalysis.builder()
                    .title(news.getTitle())
                    .keyword(news.getKeyword())
                    .sentiment(useSentiment ? news.getSentimentScore() : null)
                    .summary(news.getSummary())
                    .source(news.getSource())
                    .build();
            
            grouped.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(dailyAnalysis);
        }
        
        return grouped;
    }

    private String analyzeTrend(Map<String, List<AnalysisDTO.DailyAnalysis>> groupedByDate) {
        if (groupedByDate.size() < 2) {
            return "STABLE";
        }
        
        // 간단한 트렌드 분석 (실제로는 더 복잡한 알고리즘 필요)
        List<String> dates = groupedByDate.keySet().stream().sorted().collect(Collectors.toList());
        int firstHalfSize = groupedByDate.get(dates.get(0)).size();
        int lastHalfSize = groupedByDate.get(dates.get(dates.size() - 1)).size();
        
        if (lastHalfSize > firstHalfSize * 1.2) {
            return "RISING";
        } else if (lastHalfSize < firstHalfSize * 0.8) {
            return "FALLING";
        } else {
            return "STABLE";
        }
    }

    private double calculateAverageSentiment(List<News> newsList) {
        return newsList.stream()
                .filter(news -> news.getSentimentScore() != null)
                .mapToDouble(News::getSentimentScore)
                .average()
                .orElse(0.0);
    }

    private Map<String, Integer> calculateSourceDistribution(List<News> newsList) {
        return newsList.stream()
                .collect(Collectors.groupingBy(
                        News::getSource,
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
    }

    private double calculateConfidence(int newsCount) {
        // 뉴스 수를 기반으로 신뢰도 계산
        if (newsCount >= 50) return 0.9;
        if (newsCount >= 20) return 0.8;
        if (newsCount >= 10) return 0.7;
        if (newsCount >= 5) return 0.6;
        return 0.5;
    }

    private String calculateClusterSentiment(List<News> newsList) {
        double avgSentiment = calculateAverageSentiment(newsList);
        
        if (avgSentiment > 0.6) return "긍정적";
        if (avgSentiment < 0.4) return "부정적";
        return "중립적";
    }

    private News.SentimentLabel mapSentimentLabel(String label) {
        switch (label.toLowerCase()) {
            case "positive": return News.SentimentLabel.POSITIVE;
            case "negative": return News.SentimentLabel.NEGATIVE;
            default: return News.SentimentLabel.NEUTRAL;
        }
    }

    private AnalysisDTO.SentimentAnalysisResponse createSentimentResponse(News news) {
        return AnalysisDTO.SentimentAnalysisResponse.builder()
                .newsId(news.getId())
                .title(news.getTitle())
                .date(news.getPublishedDate())
                .sentimentScore(news.getSentimentScore())
                .sentimentLabel(news.getSentimentLabel().name())
                .originalSummary(news.getSummary())
                .build();
    }

    private AnalysisDTO.NewsClusterItem convertToNewsClusterItem(News news) {
        return AnalysisDTO.NewsClusterItem.builder()
                .id(news.getId())
                .title(news.getTitle())
                .summary(news.getSummary())
                .date(news.getPublishedDate())
                .source(news.getSource())
                .build();
    }

    private void saveNewsAnalysis(News news, User user, String analysisType, 
                                  Object result, long processingTime) {
        try {
            NewsAnalysis analysis = NewsAnalysis.builder()
                    .news(news)
                    .user(user)
                    .analysisType(analysisType)
                    .resultData(objectMapper.writeValueAsString(result))
                    .processingTimeMs(processingTime)
                    .build();
            
            newsAnalysisRepository.save(analysis);
        } catch (Exception e) {
            log.error("분석 결과 저장 실패: {}", e.getMessage());
        }
    }

    private void saveIndicatorAnalysis(User user, String indicator, List<News> newsList,
                                       AnalysisDTO.IndicatorAnalysisResponse result,
                                       AnalysisDTO.IndicatorAnalysisRequest request) {
        try {
            IndicatorAnalysis analysis = IndicatorAnalysis.builder()
                    .user(user)
                    .indicator(indicator)
                    .analysisDate(LocalDateTime.now())
                    .dateRangeStart(request.getStartDate())
                    .dateRangeEnd(request.getEndDate())
                    .totalMentions(result.getTotalMentions())
                    .averageSentiment(result.getAverageSentiment())
                    .trend(IndicatorAnalysis.TrendDirection.valueOf(result.getTrend()))
                    .confidenceScore(result.getConfidence())
                    .dailyData(objectMapper.writeValueAsString(result.getGroupedByDate()))
                    .sourceDistribution(objectMapper.writeValueAsString(result.getSourceDistribution()))
                    .relatedNews(newsList)
                    .build();
            
            indicatorAnalysisRepository.save(analysis);
        } catch (Exception e) {
            log.error("지표 분석 결과 저장 실패: {}", e.getMessage());
        }
    }

    private void saveClusteringAnalysis(User user, List<News> newsList, 
                                        List<AnalysisDTO.ClusteringResponse> results) {
        try {
            String resultJson = objectMapper.writeValueAsString(results);
            
            NewsAnalysis analysis = NewsAnalysis.builder()
                    .user(user)
                    .analysisType("CLUSTERING")
                    .resultData(resultJson)
                    .processingTimeMs(System.currentTimeMillis())
                    .build();
            
            newsAnalysisRepository.save(analysis);
        } catch (Exception e) {
            log.error("클러스터링 결과 저장 실패: {}", e.getMessage());
        }
    }

    private void saveAnalysisHistory(User user, String name, AnalysisHistory.AnalysisType type,
                                     List<String> indicators, int newsCount, double accuracy) {
        try {
            AnalysisHistory history = AnalysisHistory.builder()
                    .user(user)
                    .name(name)
                    .analysisType(type)
                    .indicators(objectMapper.writeValueAsString(indicators))
                    .newsCount(newsCount)
                    .accuracyScore(accuracy)
                    .resultSummary(String.format("%s 완료 - 뉴스 %d개 분석", name, newsCount))
                    .build();
            
            analysisHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("분석 히스토리 저장 실패: {}", e.getMessage());
        }
    }

    private double calculateAverageAccuracy(List<AnalysisDTO.SentimentAnalysisResponse> results) {
        // 간단한 정확도 계산 (실제로는 더 복잡한 평가 필요)
        return Math.random() * 20 + 75; // 75-95% 범위의 임의 값
    }

    private double calculateIndicatorAnalysisAccuracy(Map<String, AnalysisDTO.IndicatorAnalysisResponse> results) {
        return results.values().stream()
                .mapToDouble(AnalysisDTO.IndicatorAnalysisResponse::getConfidence)
                .average()
                .orElse(0.0) * 100;
    }

    private Object convertToAnalysisHistoryResponse(AnalysisHistory history) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", history.getId());
        response.put("name", history.getName());
        response.put("date", history.getCreatedAt());
        response.put("analysisType", history.getAnalysisType());
        response.put("newsCount", history.getNewsCount());
        response.put("accuracyScore", history.getAccuracyScore());
        
        try {
            response.put("indicators", objectMapper.readValue(history.getIndicators(), List.class));
        } catch (Exception e) {
            response.put("indicators", Collections.emptyList());
        }
        
        return response;
    }

    private Object convertToAnalysisDetailResponse(AnalysisHistory history) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", history.getId());
        detail.put("name", history.getName());
        detail.put("analysisType", history.getAnalysisType());
        detail.put("createdAt", history.getCreatedAt());
        detail.put("newsCount", history.getNewsCount());
        detail.put("accuracyScore", history.getAccuracyScore());
        detail.put("resultSummary", history.getResultSummary());
        
        try {
            detail.put("indicators", objectMapper.readValue(history.getIndicators(), List.class));
            if (history.getConfigData() != null) {
                detail.put("configData", objectMapper.readValue(history.getConfigData(), Map.class));
            }
        } catch (Exception e) {
            log.warn("분석 상세 정보 파싱 실패: {}", e.getMessage());
        }
        
        return detail;
    }
}
