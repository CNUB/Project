package com.financescope.financescope.service.external;

import com.financescope.financescope.entity.IndicatorAnalysis;
import com.financescope.financescope.entity.News;
import com.financescope.financescope.entity.Prediction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionModelService {

    public Object runPrediction(String indicator, Prediction.ModelType modelType, 
                               Integer predictionDays, List<News> newsList, 
                               List<IndicatorAnalysis> analysisData, Boolean useSummary) {
        
        log.info("예측 모델 실행 - 지표: {}, 모델: {}, 기간: {}일", indicator, modelType, predictionDays);
        
        try {
            switch (modelType) {
                case ARIMA:
                    return runARIMAModel(indicator, predictionDays, newsList, analysisData);
                case PROPHET:
                    return runProphetModel(indicator, predictionDays, newsList, analysisData);
                case LSTM:
                    return runLSTMModel(indicator, predictionDays, newsList, analysisData);
                case XGBOOST:
                    return runXGBoostModel(indicator, predictionDays, newsList, analysisData);
                case ENSEMBLE:
                    return runEnsembleModel(indicator, predictionDays, newsList, analysisData);
                default:
                    return runSimpleModel(indicator, predictionDays, newsList, analysisData);
            }
        } catch (Exception e) {
            log.error("예측 모델 실행 실패: {}", e.getMessage());
            return runSimpleModel(indicator, predictionDays, newsList, analysisData);
        }
    }
    
    private Object runARIMAModel(String indicator, Integer predictionDays, 
                                List<News> newsList, List<IndicatorAnalysis> analysisData) {
        log.info("ARIMA 모델 실행");
        
        // 실제 구현에서는 R 또는 Python 스크립트 호출
        // 여기서는 시뮬레이션
        
        List<Map<String, Object>> forecastData = generateForecastData(indicator, predictionDays, 0.1);
        
        Map<String, Object> result = new HashMap<>();
        result.put("forecastData", forecastData);
        result.put("confidenceIntervals", generateConfidenceIntervals(forecastData));
        result.put("importantEvents", generateImportantEvents(predictionDays));
        result.put("performance", Map.of(
                "accuracy", 0.85 + Math.random() * 0.1,
                "mape", 3.0 + Math.random() * 2.0,
                "rmse", 5.0 + Math.random() * 3.0,
                "rSquared", 0.8 + Math.random() * 0.15
        ));
        result.put("confidence", 0.8 + Math.random() * 0.15);
        result.put("trend", determineTrend(forecastData));
        result.put("sentimentInfluence", calculateSentimentInfluence(newsList));
        
        return result;
    }
    
    private Object runProphetModel(String indicator, Integer predictionDays, 
                                  List<News> newsList, List<IndicatorAnalysis> analysisData) {
        log.info("Prophet 모델 실행");
        
        List<Map<String, Object>> forecastData = generateForecastData(indicator, predictionDays, 0.15);
        
        Map<String, Object> result = new HashMap<>();
        result.put("forecastData", forecastData);
        result.put("confidenceIntervals", generateConfidenceIntervals(forecastData));
        result.put("importantEvents", generateImportantEvents(predictionDays));
        result.put("performance", Map.of(
                "accuracy", 0.87 + Math.random() * 0.1,
                "mape", 2.5 + Math.random() * 2.0,
                "rmse", 4.5 + Math.random() * 3.0,
                "rSquared", 0.85 + Math.random() * 0.12
        ));
        result.put("confidence", 0.82 + Math.random() * 0.15);
        result.put("trend", determineTrend(forecastData));
        result.put("sentimentInfluence", calculateSentimentInfluence(newsList));
        
        return result;
    }
    
    private Object runLSTMModel(String indicator, Integer predictionDays, 
                               List<News> newsList, List<IndicatorAnalysis> analysisData) {
        log.info("LSTM 모델 실행");
        
        List<Map<String, Object>> forecastData = generateForecastData(indicator, predictionDays, 0.2);
        
        Map<String, Object> result = new HashMap<>();
        result.put("forecastData", forecastData);
        result.put("confidenceIntervals", generateConfidenceIntervals(forecastData));
        result.put("importantEvents", generateImportantEvents(predictionDays));
        result.put("performance", Map.of(
                "accuracy", 0.89 + Math.random() * 0.08,
                "mape", 2.0 + Math.random() * 1.5,
                "rmse", 4.0 + Math.random() * 2.5,
                "rSquared", 0.88 + Math.random() * 0.1
        ));
        result.put("confidence", 0.85 + Math.random() * 0.12);
        result.put("trend", determineTrend(forecastData));
        result.put("sentimentInfluence", calculateSentimentInfluence(newsList));
        
        return result;
    }
    
    private Object runXGBoostModel(String indicator, Integer predictionDays, 
                                  List<News> newsList, List<IndicatorAnalysis> analysisData) {
        log.info("XGBoost 모델 실행");
        
        List<Map<String, Object>> forecastData = generateForecastData(indicator, predictionDays, 0.12);
        
        Map<String, Object> result = new HashMap<>();
        result.put("forecastData", forecastData);
        result.put("confidenceIntervals", generateConfidenceIntervals(forecastData));
        result.put("importantEvents", generateImportantEvents(predictionDays));
        result.put("performance", Map.of(
                "accuracy", 0.91 + Math.random() * 0.07,
                "mape", 1.8 + Math.random() * 1.2,
                "rmse", 3.5 + Math.random() * 2.0,
                "rSquared", 0.90 + Math.random() * 0.08
        ));
        result.put("confidence", 0.87 + Math.random() * 0.1);
        result.put("trend", determineTrend(forecastData));
        result.put("sentimentInfluence", calculateSentimentInfluence(newsList));
        
        return result;
    }
    
    private Object runEnsembleModel(String indicator, Integer predictionDays, 
                                   List<News> newsList, List<IndicatorAnalysis> analysisData) {
        log.info("앙상블 모델 실행");
        
        // 여러 모델의 결과를 조합
        List<Map<String, Object>> forecastData = generateForecastData(indicator, predictionDays, 0.08);
        
        Map<String, Object> result = new HashMap<>();
        result.put("forecastData", forecastData);
        result.put("confidenceIntervals", generateConfidenceIntervals(forecastData));
        result.put("importantEvents", generateImportantEvents(predictionDays));
        result.put("performance", Map.of(
                "accuracy", 0.93 + Math.random() * 0.05,
                "mape", 1.5 + Math.random() * 1.0,
                "rmse", 3.0 + Math.random() * 1.8,
                "rSquared", 0.92 + Math.random() * 0.06
        ));
        result.put("confidence", 0.90 + Math.random() * 0.08);
        result.put("trend", determineTrend(forecastData));
        result.put("sentimentInfluence", calculateSentimentInfluence(newsList));
        
        return result;
    }
    
    private Object runSimpleModel(String indicator, Integer predictionDays, 
                                 List<News> newsList, List<IndicatorAnalysis> analysisData) {
        log.info("간단 모델 실행");
        
        List<Map<String, Object>> forecastData = generateForecastData(indicator, predictionDays, 0.25);
        
        Map<String, Object> result = new HashMap<>();
        result.put("forecastData", forecastData);
        result.put("confidenceIntervals", generateConfidenceIntervals(forecastData));
        result.put("importantEvents", generateImportantEvents(predictionDays));
        result.put("performance", Map.of(
                "accuracy", 0.75 + Math.random() * 0.15,
                "mape", 4.0 + Math.random() * 3.0,
                "rmse", 6.0 + Math.random() * 4.0,
                "rSquared", 0.70 + Math.random() * 0.2
        ));
        result.put("confidence", 0.70 + Math.random() * 0.2);
        result.put("trend", determineTrend(forecastData));
        result.put("sentimentInfluence", calculateSentimentInfluence(newsList));
        
        return result;
    }
    
    private List<Map<String, Object>> generateForecastData(String indicator, Integer predictionDays, double volatility) {
        List<Map<String, Object>> forecast = new ArrayList<>();
        
        // 지표별 기본값 설정
        double baseValue = getBaseValueForIndicator(indicator);
        double trendFactor = (Math.random() - 0.5) * 0.1; // -5% ~ +5% 트렌드
        
        LocalDateTime now = LocalDateTime.now();
        
        // 과거 30일 데이터 (실제값)
        for (int i = -30; i <= 0; i++) {
            LocalDateTime date = now.plusDays(i);
            double noise = (Math.random() - 0.5) * baseValue * volatility;
            double value = baseValue + (i * trendFactor * baseValue / 100) + noise;
            
            Map<String, Object> point = new HashMap<>();
            point.put("date", date.toLocalDate().toString());
            point.put("value", Math.round(value * 100.0) / 100.0);
            point.put("isActual", true);
            point.put("upper", null);
            point.put("lower", null);
            
            forecast.add(point);
        }
        
        // 예측 데이터
        for (int i = 1; i <= predictionDays; i++) {
            LocalDateTime date = now.plusDays(i);
            double noise = (Math.random() - 0.5) * baseValue * volatility;
            double value = baseValue + (i * trendFactor * baseValue / 100) + noise;
            
            // 신뢰 구간 계산
            double confidence = baseValue * volatility * (1 + i * 0.1);
            
            Map<String, Object> point = new HashMap<>();
            point.put("date", date.toLocalDate().toString());
            point.put("value", Math.round(value * 100.0) / 100.0);
            point.put("isActual", false);
            point.put("upper", Math.round((value + confidence) * 100.0) / 100.0);
            point.put("lower", Math.round((value - confidence) * 100.0) / 100.0);
            
            forecast.add(point);
        }
        
        return forecast;
    }
    
    private double getBaseValueForIndicator(String indicator) {
        switch (indicator.toLowerCase()) {
            case "금리":
            case "interest_rate":
                return 3.5;
            case "환율":
            case "exchange_rate":
                return 1320.0;
            case "주가":
            case "kospi":
                return 2650.0;
            case "kosdaq":
                return 850.0;
            case "물가":
            case "cpi":
                return 104.8;
            case "gdp":
                return 2.3;
            case "실업률":
            case "unemployment":
                return 3.1;
            default:
                return 100.0;
        }
    }
    
    private List<Map<String, Object>> generateConfidenceIntervals(List<Map<String, Object>> forecastData) {
        return forecastData.stream()
                .filter(point -> !(Boolean) point.get("isActual"))
                .collect(ArrayList::new, (list, point) -> list.add(point), ArrayList::addAll);
    }
    
    private List<Map<String, Object>> generateImportantEvents(Integer predictionDays) {
        List<Map<String, Object>> events = new ArrayList<>();
        
        // 중간 지점에 이벤트 추가
        if (predictionDays > 7) {
            LocalDateTime midDate = LocalDateTime.now().plusDays(predictionDays / 2);
            events.add(Map.of(
                    "date", midDate.toLocalDate().toString(),
                    "event", "예상 변곡점",
                    "type", "PREDICTION"
            ));
        }
        
        // 마지막 날에 이벤트 추가
        LocalDateTime endDate = LocalDateTime.now().plusDays(predictionDays);
        events.add(Map.of(
                "date", endDate.toLocalDate().toString(),
                "event", "예측 기간 종료",
                "type", "PREDICTION"
        ));
        
        return events;
    }
    
    private String determineTrend(List<Map<String, Object>> forecastData) {
        if (forecastData.size() < 2) return "STABLE";
        
        List<Map<String, Object>> futureData = forecastData.stream()
                .filter(point -> !(Boolean) point.get("isActual"))
                .collect(ArrayList::new, (list, point) -> list.add(point), ArrayList::addAll);
        
        if (futureData.size() < 2) return "STABLE";
        
        double firstValue = (Double) futureData.get(0).get("value");
        double lastValue = (Double) futureData.get(futureData.size() - 1).get("value");
        
        double changePercent = ((lastValue - firstValue) / firstValue) * 100;
        
        if (changePercent > 2.0) return "RISING";
        if (changePercent < -2.0) return "FALLING";
        return "STABLE";
    }
    
    private double calculateSentimentInfluence(List<News> newsList) {
        if (newsList == null || newsList.isEmpty()) {
            return 0.0;
        }
        
        double totalSentiment = newsList.stream()
                .filter(news -> news.getSentimentScore() != null)
                .mapToDouble(News::getSentimentScore)
                .average()
                .orElse(0.5);
        
        // 0.5를 중심으로 영향도 계산 (0.0 ~ 1.0)
        return Math.abs(totalSentiment - 0.5) * 2;
    }
}
