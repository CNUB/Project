package com.financescope.financescope.service.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SummarizationService {

    private final WebClient.Builder webClientBuilder;
    
    @Value("${external-api.ml.huggingface.api-key:}")
    private String huggingfaceApiKey;
    
    @Value("${external-api.ml.openai.api-key:}")
    private String openaiApiKey;

    public String summarize(String text, String model, Integer maxLength) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        if (maxLength == null) {
            maxLength = 150;
        }
        
        try {
            switch (model.toUpperCase()) {
                case "KOBART":
                    return summarizeWithKoBART(text, maxLength);
                case "KOBERT":
                    return summarizeWithKoBERT(text, maxLength);
                case "T5":
                    return summarizeWithT5(text, maxLength);
                default:
                    return summarizeWithSimpleMethod(text, maxLength);
            }
        } catch (Exception e) {
            log.error("요약 실패, 간단한 방법으로 대체: {}", e.getMessage());
            return summarizeWithSimpleMethod(text, maxLength);
        }
    }
    
    private String summarizeWithKoBART(String text, Integer maxLength) {
        try {
            if (!huggingfaceApiKey.isEmpty()) {
                return summarizeWithHuggingface(text, "gogamza/kobart-summarization", maxLength);
            }
        } catch (Exception e) {
            log.error("KoBART 요약 실패: {}", e.getMessage());
        }
        
        return summarizeWithSimpleMethod(text, maxLength);
    }
    
    private String summarizeWithKoBERT(String text, Integer maxLength) {
        try {
            if (!huggingfaceApiKey.isEmpty()) {
                return summarizeWithHuggingface(text, "monologg/kobert", maxLength);
            }
        } catch (Exception e) {
            log.error("KoBERT 요약 실패: {}", e.getMessage());
        }
        
        return summarizeWithSimpleMethod(text, maxLength);
    }
    
    private String summarizeWithT5(String text, Integer maxLength) {
        try {
            if (!openaiApiKey.isEmpty()) {
                return summarizeWithOpenAI(text, maxLength);
            } else if (!huggingfaceApiKey.isEmpty()) {
                return summarizeWithHuggingface(text, "t5-base", maxLength);
            }
        } catch (Exception e) {
            log.error("T5 요약 실패: {}", e.getMessage());
        }
        
        return summarizeWithSimpleMethod(text, maxLength);
    }
    
    private String summarizeWithHuggingface(String text, String modelName, Integer maxLength) {
        try {
            WebClient webClient = webClientBuilder.build();
            
            Map<String, Object> requestBody = Map.of(
                    "inputs", text,
                    "parameters", Map.of(
                            "max_length", maxLength,
                            "min_length", Math.min(30, maxLength / 3),
                            "do_sample", false
                    )
            );
            
            List<Map<String, Object>> response = webClient.post()
                    .uri("https://api-inference.huggingface.co/models/" + modelName)
                    .header("Authorization", "Bearer " + huggingfaceApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();
            
            if (response != null && !response.isEmpty()) {
                Map<String, Object> result = response.get(0);
                String summary = (String) result.get("summary_text");
                
                if (summary != null && !summary.trim().isEmpty()) {
                    return summary.trim();
                }
            }
        } catch (Exception e) {
            log.error("Hugging Face 요약 API 호출 실패: {}", e.getMessage());
        }
        
        throw new RuntimeException("Hugging Face 요약 실패");
    }
    
    private String summarizeWithOpenAI(String text, Integer maxLength) {
        try {
            WebClient webClient = webClientBuilder.build();
            
            String prompt = String.format(
                    "다음 뉴스 기사를 %d자 이내로 요약해주세요. 핵심 내용만 간단명료하게 정리해주세요:\n\n%s",
                    maxLength, text
            );
            
            Map<String, Object> requestBody = Map.of(
                    "model", "gpt-3.5-turbo",
                    "messages", Arrays.asList(
                            Map.of("role", "system", "content", "당신은 뉴스 기사를 요약하는 전문가입니다."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "max_tokens", maxLength / 2,
                    "temperature", 0.3
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
                    
                    if (content != null && !content.trim().isEmpty()) {
                        return content.trim();
                    }
                }
            }
        } catch (Exception e) {
            log.error("OpenAI 요약 API 호출 실패: {}", e.getMessage());
        }
        
        throw new RuntimeException("OpenAI 요약 실패");
    }
    
    private String summarizeWithSimpleMethod(String text, Integer maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        
        // 문장 단위로 분리
        String[] sentences = text.split("[.!?]");
        
        if (sentences.length <= 1) {
            return text.substring(0, Math.min(text.length(), maxLength)) + "...";
        }
        
        // 첫 번째와 마지막 문장 선택 (간단한 추출 요약)
        StringBuilder summary = new StringBuilder();
        
        // 첫 문장 추가
        if (sentences.length > 0 && !sentences[0].trim().isEmpty()) {
            summary.append(sentences[0].trim()).append(". ");
        }
        
        // 중간에서 중요해 보이는 문장 하나 추가
        if (sentences.length > 2) {
            int midIndex = sentences.length / 2;
            String midSentence = sentences[midIndex].trim();
            if (!midSentence.isEmpty() && summary.length() + midSentence.length() < maxLength - 20) {
                summary.append(midSentence).append(". ");
            }
        }
        
        // 마지막 문장 추가 (공간이 허용하는 경우)
        if (sentences.length > 1) {
            String lastSentence = sentences[sentences.length - 1].trim();
            if (!lastSentence.isEmpty() && summary.length() + lastSentence.length() < maxLength) {
                summary.append(lastSentence).append(".");
            }
        }
        
        String result = summary.toString().trim();
        
        // 최대 길이 제한
        if (result.length() > maxLength) {
            result = result.substring(0, maxLength - 3) + "...";
        }
        
        return result.isEmpty() ? text.substring(0, Math.min(text.length(), maxLength)) + "..." : result;
    }
}