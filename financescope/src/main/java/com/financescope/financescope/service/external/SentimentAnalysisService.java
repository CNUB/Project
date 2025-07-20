package com.financescope.financescope.service.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SentimentAnalysisService {

    private final WebClient.Builder webClientBuilder;
    
    @Value("${external-api.ml.huggingface.api-key:}")
    private String huggingfaceApiKey;
    
    @Value("${external-api.ml.openai.api-key:}")
    private String openaiApiKey;

    public SentimentResult analyzeSentiment(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new SentimentResult(0.5, "NEUTRAL");
        }
        
        try {
            // 외부 API 사용 가능한 경우
            if (!huggingfaceApiKey.isEmpty()) {
                return analyzeWithHuggingface(text);
            } else if (!openaiApiKey.isEmpty()) {
                return analyzeWithOpenAI(text);
            } else {
                // 간단한 룰 기반 감성 분석
                return analyzeWithSimpleRules(text);
            }
        } catch (Exception e) {
            log.error("감성 분석 실패, 룰 기반으로 대체: {}", e.getMessage());
            return analyzeWithSimpleRules(text);
        }
    }
    
    private SentimentResult analyzeWithHuggingface(String text) {
        try {
            WebClient webClient = webClientBuilder.build();
            
            Map<String, String> requestBody = Map.of("inputs", text);
            
            List<Map<String, Object>> response = webClient.post()
                    .uri("https://api-inference.huggingface.co/models/nlptown/bert-base-multilingual-uncased-sentiment")
                    .header("Authorization", "Bearer " + huggingfaceApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();
            
            if (response != null && !response.isEmpty()) {
                Map<String, Object> result = response.get(0);
                String label = (String) result.get("label");
                Double score = (Double) result.get("score");
                
                return convertHuggingfaceResult(label, score);
            }
        } catch (Exception e) {
            log.error("Hugging Face API 호출 실패: {}", e.getMessage());
        }
        
        return analyzeWithSimpleRules(text);
    }
    
    private SentimentResult analyzeWithOpenAI(String text) {
        try {
            WebClient webClient = webClientBuilder.build();
            
            Map<String, Object> requestBody = Map.of(
                    "model", "gpt-3.5-turbo",
                    "messages", Arrays.asList(
                            Map.of("role", "system", "content", "다음 텍스트의 감성을 분석하여 POSITIVE, NEGATIVE, NEUTRAL 중 하나로 답하고 0-1 사이의 신뢰도 점수를 제공하세요."),
                            Map.of("role", "user", "content", text)
                    ),
                    "max_tokens", 50
            );
            
            Map<String, Object> response = webClient.post()
                    .uri("https://api.openai.com/v1/chat/completions")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    
                    return parseOpenAIResult(content);
                }
            }
        } catch (Exception e) {
            log.error("OpenAI API 호출 실패: {}", e.getMessage());
        }
        
        return analyzeWithSimpleRules(text);
    }
    
    private SentimentResult analyzeWithSimpleRules(String text) {
        String lowerText = text.toLowerCase();
        
        // 긍정 단어들
        List<String> positiveWords = Arrays.asList(
                "상승", "증가", "호조", "개선", "성장", "확대", "강세", "긍정", "좋은", "우수", 
                "성공", "향상", "발전", "활성", "회복", "증진", "상향", "프리미엄"
        );
        
        // 부정 단어들
        List<String> negativeWords = Arrays.asList(
                "하락", "감소", "약세", "축소", "부진", "악화", "위험", "부정", "나쁜", "실패",
                "저조", "침체", "급락", "폭락", "우려", "경고", "위기", "문제", "손실"
        );
        
        int positiveCount = 0;
        int negativeCount = 0;
        
        for (String word : positiveWords) {
            positiveCount += countOccurrences(lowerText, word);
        }
        
        for (String word : negativeWords) {
            negativeCount += countOccurrences(lowerText, word);
        }
        
        int totalSentimentWords = positiveCount + negativeCount;
        
        if (totalSentimentWords == 0) {
            return new SentimentResult(0.5, "NEUTRAL");
        }
        
        double positiveRatio = (double) positiveCount / totalSentimentWords;
        
        if (positiveRatio > 0.6) {
            return new SentimentResult(0.5 + (positiveRatio * 0.5), "POSITIVE");
        } else if (positiveRatio < 0.4) {
            return new SentimentResult(0.5 - ((1 - positiveRatio) * 0.5), "NEGATIVE");
        } else {
            return new SentimentResult(0.5, "NEUTRAL");
        }
    }
    
    private int countOccurrences(String text, String word) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(word, index)) != -1) {
            count++;
            index += word.length();
        }
        return count;
    }
    
    private SentimentResult convertHuggingfaceResult(String label, Double score) {
        // Hugging Face 결과를 표준 형식으로 변환
        if (label.toLowerCase().contains("positive") || label.contains("4") || label.contains("5")) {
            return new SentimentResult(score, "POSITIVE");
        } else if (label.toLowerCase().contains("negative") || label.contains("1") || label.contains("2")) {
            return new SentimentResult(1.0 - score, "NEGATIVE");
        } else {
            return new SentimentResult(0.5, "NEUTRAL");
        }
    }
    
    private SentimentResult parseOpenAIResult(String content) {
        content = content.toLowerCase();
        
        if (content.contains("positive")) {
            return new SentimentResult(0.8, "POSITIVE");
        } else if (content.contains("negative")) {
            return new SentimentResult(0.2, "NEGATIVE");
        } else {
            return new SentimentResult(0.5, "NEUTRAL");
        }
    }
    
    // 감성 분석 결과 클래스
    public static class SentimentResult {
        private final double score;
        private final String label;
        
        public SentimentResult(double score, String label) {
            this.score = score;
            this.label = label;
        }
        
        public double getScore() { return score; }
        public String getLabel() { return label; }
    }
}