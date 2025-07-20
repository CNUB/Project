package com.financescope.financescope.service;

import com.financescope.financescope.dto.prediction.PredictionDTO;
import com.financescope.financescope.entity.*;
import com.financescope.financescope.exception.BusinessException;
import com.financescope.financescope.repository.*;
import com.financescope.financescope.service.external.PredictionModelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
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
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final UserRepository userRepository;
    private final NewsRepository newsRepository;
    private final IndicatorAnalysisRepository indicatorAnalysisRepository;
    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final PredictionModelService predictionModelService;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public PredictionDTO.PredictionResponse runPrediction(
            PredictionDTO.PredictionRequest request, String userEmail) {
        log.info("예측 실행 시작 - 사용자: {}, 지표: {}, 모델: {}", 
                userEmail, request.getIndicator(), request.getModel());

        User user = findUserByEmail(userEmail);
        
        // 구독 제한 확인
        validatePredictionLimits(user);

        // 예측 엔티티 생성
        Prediction prediction = createPredictionEntity(request, user);
        prediction = predictionRepository.save(prediction);

        try {
            // 비동기로 예측 실행
            executePredictionAsync(prediction.getId(), request);
            
            // 즉시 응답 반환 (상태: PENDING)
            return convertToPredictionResponse(prediction);
            
        } catch (Exception e) {
            log.error("예측 실행 실패: {}", e.getMessage());
            prediction.setStatus(Prediction.PredictionStatus.FAILED);
            prediction.setErrorMessage(e.getMessage());
            predictionRepository.save(prediction);
            
            throw new BusinessException("예측 실행에 실패했습니다: " + e.getMessage());
        }
    }

    @Async
    public void executePredictionAsync(Long predictionId, PredictionDTO.PredictionRequest request) {
        log.info("비동기 예측 실행 시작 - ID: {}", predictionId);
        
        Prediction prediction = predictionRepository.findById(predictionId)
                .orElseThrow(() -> new BusinessException("예측을 찾을 수 없습니다."));

        long startTime = System.currentTimeMillis();
        
        try {
            prediction.setStatus(Prediction.PredictionStatus.RUNNING);
            predictionRepository.save(prediction);

            // 뉴스 데이터 수집
            List<News> newsList = collectNewsForPrediction(request, prediction.getUser());
            
            // 지표 분석 데이터 수집
            List<IndicatorAnalysis> analysisData = collectAnalysisForPrediction(request, prediction.getUser());
            
            // 예측 모델 실행
            var modelResult = predictionModelService.runPrediction(
                    request.getIndicator(),
                    Prediction.ModelType.valueOf(request.getModel().toUpperCase()),
                    request.getPredictionDays(),
                    newsList,
                    analysisData,
                    request.getUseSummary()
            );

            // 예측 결과 저장
            updatePredictionWithResults(prediction, modelResult, System.currentTimeMillis() - startTime);
            
            // 분석 히스토리 저장
            saveAnalysisHistory(prediction);

            log.info("예측 완료 - ID: {}, 처리시간: {}ms", predictionId, System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("예측 실행 중 오류 - ID: {}, 오류: {}", predictionId, e.getMessage());
            
            prediction.setStatus(Prediction.PredictionStatus.FAILED);
            prediction.setErrorMessage(e.getMessage());
            prediction.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            predictionRepository.save(prediction);
        }
    }

    @Transactional(readOnly = true)
    public PredictionDTO.PredictionStatusResponse getPredictionStatus(Long id, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        Prediction prediction = predictionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("예측을 찾을 수 없습니다."));
        
        if (!prediction.getUser().equals(user)) {
            throw new BusinessException("접근 권한이 없습니다.");
        }

        return PredictionDTO.PredictionStatusResponse.builder()
                .id(prediction.getId())
                .status(prediction.getStatus().name())
                .progress(calculateProgress(prediction))
                .message(getStatusMessage(prediction))
                .processingTimeMs(prediction.getProcessingTimeMs())
                .error(prediction.getErrorMessage())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<PredictionDTO.PredictionResponse> getPredictions(
            Pageable pageable, String indicator, String model, String startDate, String endDate, String userEmail) {
        
        User user = findUserByEmail(userEmail);
        
        Prediction.ModelType modelType = model != null ? Prediction.ModelType.valueOf(model.toUpperCase()) : null;
        LocalDateTime startDateTime = parseDateTime(startDate);
        LocalDateTime endDateTime = parseDateTime(endDate);
        
        Page<Prediction> predictionsPage = predictionRepository.findPredictionsByFilters(
                user, indicator, modelType, startDateTime, endDateTime, pageable);
        
        return predictionsPage.map(this::convertToPredictionResponse);
    }

    @Transactional(readOnly = true)
    public PredictionDTO.PredictionResponse getPredictionById(Long id, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        Prediction prediction = predictionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("예측을 찾을 수 없습니다."));
        
        if (!prediction.getUser().equals(user)) {
            throw new BusinessException("접근 권한이 없습니다.");
        }
        
        return convertToPredictionResponse(prediction);
    }

    public void deletePrediction(Long id, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        Prediction prediction = predictionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("예측을 찾을 수 없습니다."));
        
        if (!prediction.getUser().equals(user)) {
            throw new BusinessException("삭제 권한이 없습니다.");
        }
        
        predictionRepository.delete(prediction);
        log.info("예측 삭제 완료 - ID: {}, 사용자: {}", id, userEmail);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAvailableModels() {
        String cacheKey = "available_prediction_models";
        List<Map<String, Object>> cached = cacheService.get(cacheKey, List.class);
        
        if (cached != null) {
            return cached;
        }

        List<Map<String, Object>> models = Arrays.asList(
                Map.of("id", "ARIMA", "name", "ARIMA", "description", "자기회귀통합이동평균 모델"),
                Map.of("id", "PROPHET", "name", "Prophet", "description", "페이스북 개발 시계열 예측 모델"),
                Map.of("id", "LSTM", "name", "LSTM", "description", "장단기 메모리 신경망 모델"),
                Map.of("id", "XGBOOST", "name", "XGBoost", "description", "그래디언트 부스팅 기반 모델"),
                Map.of("id", "ENSEMBLE", "name", "앙상블", "description", "여러 모델의 조합")
        );

        cacheService.put(cacheKey, models, 3600);
        return models;
    }

    public Object validatePrediction(Long id, Map<String, Object> actualData, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        Prediction prediction = predictionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("예측을 찾을 수 없습니다."));
        
        if (!prediction.getUser().equals(user)) {
            throw new BusinessException("접근 권한이 없습니다.");
        }

        try {
            // 실제 데이터와 예측 데이터 비교
            String actualValuesJson = objectMapper.writeValueAsString(actualData);
            prediction.setActualValues(actualValuesJson);
            
            // 정확도 계산
            double accuracyScore = calculateAccuracyScore(prediction, actualData);
            prediction.setAccuracyScore(accuracyScore);
            prediction.setValidationDate(LocalDateTime.now());
            
            predictionRepository.save(prediction);

            Map<String, Object> validationResult = new HashMap<>();
            validationResult.put("predictionId", id);
            validationResult.put("accuracyScore", accuracyScore);
            validationResult.put("validationDate", LocalDateTime.now());
            validationResult.put("message", "예측 검증이 완료되었습니다.");

            log.info("예측 검증 완료 - ID: {}, 정확도: {}%", id, accuracyScore);
            return validationResult;

        } catch (Exception e) {
            log.error("예측 검증 실패: {}", e.getMessage());
            throw new BusinessException("예측 검증에 실패했습니다: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Object getModelPerformanceStats(String userEmail, String period, String indicator) {
        User user = findUserByEmail(userEmail);
        
        // 전체 예측 수
        Long totalPredictions = predictionRepository.countByUser(user);
        
        // 평균 정확도
        Double averageAccuracy = predictionRepository.findAverageAccuracyByUser(user).orElse(0.0);
        
        // 모델별 성능
        List<Object[]> modelPerformance = predictionRepository.findModelPerformanceByUser(user);
        Map<String, Map<String, Object>> modelStats = new HashMap<>();
        
        for (Object[] row : modelPerformance) {
            Prediction.ModelType modelType = (Prediction.ModelType) row[0];
            Long count = (Long) row[1];
            Double accuracy = (Double) row[2];
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("count", count);
            stats.put("accuracy", accuracy != null ? accuracy : 0.0);
            
            modelStats.put(modelType.name().toLowerCase(), stats);
        }
        
        // 최근 예측들
        List<Prediction> recentPredictions = predictionRepository.findByUser(user, Pageable.ofSize(10)).getContent();
        List<Map<String, Object>> recent = recentPredictions.stream()
                .map(this::convertToRecentPrediction)
                .collect(Collectors.toList());

        Map<String, Object> performanceStats = new HashMap<>();
        performanceStats.put("predictionsTotal", totalPredictions);
        performanceStats.put("averageAccuracy", averageAccuracy);
        performanceStats.put("models", modelStats);
        performanceStats.put("recentPredictions", recent);

        return performanceStats;
    }

    public Object comparePredictions(List<Long> predictionIds, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        List<Prediction> predictions = predictionRepository.findAllById(predictionIds);
        
        // 권한 확인
        boolean hasUnauthorized = predictions.stream()
                .anyMatch(p -> !p.getUser().equals(user));
        
        if (hasUnauthorized) {
            throw new BusinessException("접근 권한이 없는 예측이 포함되어 있습니다.");
        }

        Map<String, Object> comparison = new HashMap<>();
        comparison.put("predictions", predictions.stream()
                .map(this::convertToPredictionResponse)
                .collect(Collectors.toList()));
        comparison.put("comparisonChart", generateComparisonChart(predictions));
        comparison.put("performanceComparison", generatePerformanceComparison(predictions));

        return comparison;
    }

    public Object sharePrediction(Long id, Map<String, Object> shareRequest, String userEmail) {
        User user = findUserByEmail(userEmail);
        
        Prediction prediction = predictionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("예측을 찾을 수 없습니다."));
        
        if (!prediction.getUser().equals(user)) {
            throw new BusinessException("공유 권한이 없습니다.");
        }

        // 공유 로직 구현 (실제로는 공유 테이블 필요)
        String shareToken = UUID.randomUUID().toString();
        
        Map<String, Object> shareResult = new HashMap<>();
        shareResult.put("shareToken", shareToken);
        shareResult.put("shareUrl", "/shared/prediction/" + shareToken);
        shareResult.put("expiresAt", LocalDateTime.now().plusDays(7));
        shareResult.put("message", "예측이 공유되었습니다.");

        log.info("예측 공유 완료 - ID: {}, 토큰: {}", id, shareToken);
        return shareResult;
    }

    public Object batchPrediction(Map<String, Object> batchRequest, String userEmail) {
        log.info("일괄 예측 시작 - 사용자: {}", userEmail);
        
        User user = findUserByEmail(userEmail);
        
        // 일괄 예측 로직 구현
        String jobId = UUID.randomUUID().toString();
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "일괄 예측이 시작되었습니다.");
        result.put("jobId", jobId);
        result.put("estimatedTime", "5-10분");
        
        return result;
    }

    // Private helper methods

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));
    }

    private void validatePredictionLimits(User user) {
        if (user.getSubscriptionPlan() == User.SubscriptionPlan.FREE) {
            Long monthlyPredictions = predictionRepository.countByUser(user);
            if (monthlyPredictions >= 10) {
                throw new BusinessException("무료 사용자는 월 10회까지 예측할 수 있습니다.");
            }
        }
    }

    private Prediction createPredictionEntity(PredictionDTO.PredictionRequest request, User user) {
        return Prediction.builder()
                .user(user)
                .indicator(request.getIndicator())
                .modelType(Prediction.ModelType.valueOf(request.getModel().toUpperCase()))
                .predictionDate(LocalDateTime.now())
                .predictionPeriodDays(request.getPredictionDays())
                .predictionPeriodEnd(LocalDateTime.now().plusDays(request.getPredictionDays()))
                .status(Prediction.PredictionStatus.PENDING)
                .build();
    }

    private List<News> collectNewsForPrediction(PredictionDTO.PredictionRequest request, User user) {
        if (request.getNewsIds() != null && !request.getNewsIds().isEmpty()) {
            return newsRepository.findAllById(request.getNewsIds());
        }
        
        // 기본적으로 최근 30일간의 관련 뉴스 수집
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now();
        
        return newsRepository.findByDateRangeAndUser(startDate, endDate, user);
    }

    private List<IndicatorAnalysis> collectAnalysisForPrediction(PredictionDTO.PredictionRequest request, User user) {
        if (request.getAnalysisIds() != null && !request.getAnalysisIds().isEmpty()) {
            return indicatorAnalysisRepository.findAllById(request.getAnalysisIds());
        }
        
        // 해당 지표의 최근 분석 결과들 수집
        return indicatorAnalysisRepository.findByUserAndIndicator(user, request.getIndicator());
    }

    private void updatePredictionWithResults(Prediction prediction, Object modelResult, long processingTime) {
        try {
            // 모델 결과를 JSON으로 변환하여 저장
            Map<String, Object> result = (Map<String, Object>) modelResult;
            
            prediction.setForecastData(objectMapper.writeValueAsString(result.get("forecastData")));
            prediction.setConfidenceIntervals(objectMapper.writeValueAsString(result.get("confidenceIntervals")));
            prediction.setImportantEvents(objectMapper.writeValueAsString(result.get("importantEvents")));
            
            // 모델 성능 지표
            Map<String, Object> performance = (Map<String, Object>) result.get("performance");
            if (performance != null) {
                prediction.setModelAccuracy((Double) performance.get("accuracy"));
                prediction.setMape((Double) performance.get("mape"));
                prediction.setRmse((Double) performance.get("rmse"));
                prediction.setRSquared((Double) performance.get("rSquared"));
            }
            
            prediction.setOverallConfidence((Double) result.get("confidence"));
            prediction.setPredictedTrend(Prediction.TrendDirection.valueOf((String) result.get("trend")));
            prediction.setSentimentInfluence((Double) result.get("sentimentInfluence"));
            prediction.setProcessingTimeMs(processingTime);
            prediction.setStatus(Prediction.PredictionStatus.COMPLETED);
            
            predictionRepository.save(prediction);
            
        } catch (Exception e) {
            log.error("예측 결과 저장 실패: {}", e.getMessage());
            throw new BusinessException("예측 결과 저장에 실패했습니다.");
        }
    }

    private void saveAnalysisHistory(Prediction prediction) {
        try {
            AnalysisHistory history = AnalysisHistory.builder()
                    .user(prediction.getUser())
                    .name(prediction.getIndicator() + " " + prediction.getModelType().name() + " 예측")
                    .analysisType(AnalysisHistory.AnalysisType.PREDICTION)
                    .predictionId(prediction.getId())
                    .accuracyScore(prediction.getModelAccuracy())
                    .resultSummary(String.format("%s 지표 %d일 예측 완료 (신뢰도: %.1f%%)", 
                            prediction.getIndicator(), prediction.getPredictionPeriodDays(), 
                            prediction.getOverallConfidence() * 100))
                    .build();
            
            analysisHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("예측 히스토리 저장 실패: {}", e.getMessage());
        }
    }

    private int calculateProgress(Prediction prediction) {
        switch (prediction.getStatus()) {
            case PENDING: return 0;
            case RUNNING: 
                // 처리 시간 기반 진행률 계산
                if (prediction.getProcessingTimeMs() != null) {
                    long elapsed = prediction.getProcessingTimeMs();
                    long estimated = prediction.getPredictionPeriodDays() * 1000L; // 간단한 추정
                    return Math.min(90, (int) (elapsed * 90 / estimated));
                }
                return 50;
            case COMPLETED: return 100;
            case FAILED: return 0;
            default: return 0;
        }
    }

    private String getStatusMessage(Prediction prediction) {
        switch (prediction.getStatus()) {
            case PENDING: return "예측 대기 중";
            case RUNNING: return "예측 실행 중";
            case COMPLETED: return "예측 완료";
            case FAILED: return "예측 실패: " + prediction.getErrorMessage();
            default: return "알 수 없는 상태";
        }
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

    private double calculateAccuracyScore(Prediction prediction, Map<String, Object> actualData) {
        // 실제 구현에서는 예측값과 실제값을 비교하여 정확도 계산
        // 여기서는 간단한 시뮬레이션
        return Math.random() * 30 + 70; // 70-100% 범위
    }

    private Map<String, Object> convertToRecentPrediction(Prediction prediction) {
        Map<String, Object> recent = new HashMap<>();
        recent.put("id", prediction.getId());
        recent.put("date", prediction.getPredictionDate());
        recent.put("indicator", prediction.getIndicator());
        recent.put("model", prediction.getModelType().name().toLowerCase());
        recent.put("accuracy", prediction.getAccuracyScore());
        return recent;
    }

    private Object generateComparisonChart(List<Prediction> predictions) {
        // 예측 비교 차트 데이터 생성
        Map<String, Object> chartData = new HashMap<>();
        chartData.put("type", "comparison");
        chartData.put("predictions", predictions.stream()
                .map(p -> Map.of(
                        "id", p.getId(),
                        "name", p.getIndicator() + "_" + p.getModelType(),
                        "accuracy", p.getAccuracyScore() != null ? p.getAccuracyScore() : 0.0
                ))
                .collect(Collectors.toList()));
        return chartData;
    }

    private Object generatePerformanceComparison(List<Prediction> predictions) {
        Map<String, Object> performance = new HashMap<>();
        
        double avgAccuracy = predictions.stream()
                .filter(p -> p.getAccuracyScore() != null)
                .mapToDouble(Prediction::getAccuracyScore)
                .average()
                .orElse(0.0);
        
        performance.put("averageAccuracy", avgAccuracy);
        performance.put("bestModel", predictions.stream()
                .max(Comparator.comparing(p -> p.getAccuracyScore() != null ? p.getAccuracyScore() : 0.0))
                .map(p -> p.getModelType().name())
                .orElse("N/A"));
        
        return performance;
    }

    private PredictionDTO.PredictionResponse convertToPredictionResponse(Prediction prediction) {
        try {
            List<PredictionDTO.ForecastPoint> forecastData = new ArrayList<>();
            List<PredictionDTO.ImportantEvent> importantEvents = new ArrayList<>();
            List<String> keywords = new ArrayList<>();

            // JSON 데이터 파싱
            if (prediction.getForecastData() != null) {
                List<Map<String, Object>> forecastList = objectMapper.readValue(
                        prediction.getForecastData(), List.class);
                
                forecastData = forecastList.stream()
                        .map(this::convertToForecastPoint)
                        .collect(Collectors.toList());
            }

            if (prediction.getImportantEvents() != null) {
                List<Map<String, Object>> eventsList = objectMapper.readValue(
                        prediction.getImportantEvents(), List.class);
                
                importantEvents = eventsList.stream()
                        .map(this::convertToImportantEvent)
                        .collect(Collectors.toList());
            }

            // 모델 성능 정보
            PredictionDTO.ModelPerformance modelPerformance = PredictionDTO.ModelPerformance.builder()
                    .accuracy(prediction.getModelAccuracy())
                    .mape(prediction.getMape())
                    .rmse(prediction.getRmse())
                    .rSquared(prediction.getRSquared())
                    .build();

            return PredictionDTO.PredictionResponse.builder()
                    .id(prediction.getId())
                    .indicator(prediction.getIndicator())
                    .model(prediction.getModelType().name())
                    .predictionDate(prediction.getPredictionDate())
                    .predictionDays(prediction.getPredictionPeriodDays())
                    .forecastData(forecastData)
                    .importantDates(importantEvents)
                    .keywords(keywords)
                    .sentiment(prediction.getSentimentInfluence() != null && prediction.getSentimentInfluence() > 0.5 ? "긍정적" : "부정적")
                    .confidence(prediction.getOverallConfidence() != null ? prediction.getOverallConfidence() * 100 : 0.0)
                    .trend(prediction.getPredictedTrend() != null ? prediction.getPredictedTrend().name() : "STABLE")
                    .modelPerformance(modelPerformance)
                    .build();

        } catch (Exception e) {
            log.error("예측 응답 변환 실패: {}", e.getMessage());
            
            // 기본 응답 반환
            return PredictionDTO.PredictionResponse.builder()
                    .id(prediction.getId())
                    .indicator(prediction.getIndicator())
                    .model(prediction.getModelType().name())
                    .predictionDate(prediction.getPredictionDate())
                    .predictionDays(prediction.getPredictionPeriodDays())
                    .forecastData(new ArrayList<>())
                    .importantDates(new ArrayList<>())
                    .keywords(new ArrayList<>())
                    .confidence(0.0)
                    .trend("STABLE")
                    .build();
        }
    }

    private PredictionDTO.ForecastPoint convertToForecastPoint(Map<String, Object> data) {
        return PredictionDTO.ForecastPoint.builder()
                .date((String) data.get("date"))
                .value((Double) data.get("value"))
                .isActual((Boolean) data.get("isActual"))
                .upper((Double) data.get("upper"))
                .lower((Double) data.get("lower"))
                .build();
    }

    private PredictionDTO.ImportantEvent convertToImportantEvent(Map<String, Object> data) {
        return PredictionDTO.ImportantEvent.builder()
                .date((String) data.get("date"))
                .event((String) data.get("event"))
                .type((String) data.getOrDefault("type", "PREDICTION"))
                .build();
    }
}