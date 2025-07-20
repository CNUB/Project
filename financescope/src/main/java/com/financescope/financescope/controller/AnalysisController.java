package com.financescope.financescope.controller;

import com.financescope.financescope.dto.analysis.AnalysisDTO;
import com.financescope.financescope.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Analysis", description = "뉴스 분석 관련 API")
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping("/sentiment")
    @Operation(summary = "감성 분석", description = "뉴스의 감성을 분석합니다.")
    public ResponseEntity<List<AnalysisDTO.SentimentAnalysisResponse>> analyzeSentiment(
            @Valid @RequestBody AnalysisDTO.SentimentAnalysisRequest request,
            Authentication authentication) {
        log.info("감성 분석 요청 - 사용자: {}, 뉴스 수: {}", 
                authentication.getName(), request.getNewsIds().size());
        
        List<AnalysisDTO.SentimentAnalysisResponse> response = 
                analysisService.analyzeSentiment(request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/indicators")
    @Operation(summary = "경제 지표 분석", description = "뉴스에서 경제 지표 관련 정보를 분석합니다.")
    public ResponseEntity<Map<String, AnalysisDTO.IndicatorAnalysisResponse>> analyzeIndicators(
            @Valid @RequestBody AnalysisDTO.IndicatorAnalysisRequest request,
            Authentication authentication) {
        log.info("경제 지표 분석 요청 - 사용자: {}, 지표: {}, 뉴스 수: {}", 
                authentication.getName(), request.getIndicators(), request.getNewsIds().size());
        
        Map<String, AnalysisDTO.IndicatorAnalysisResponse> response = 
                analysisService.analyzeIndicators(request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/clustering")
    @Operation(summary = "뉴스 클러스터링", description = "유사한 뉴스들을 그룹화합니다.")
    public ResponseEntity<List<AnalysisDTO.ClusteringResponse>> clusterNews(
            @Valid @RequestBody AnalysisDTO.ClusteringRequest request,
            Authentication authentication) {
        log.info("뉴스 클러스터링 요청 - 사용자: {}, 뉴스 수: {}, 클러스터 수: {}", 
                authentication.getName(), request.getNewsIds().size(), request.getClusterCount());
        
        List<AnalysisDTO.ClusteringResponse> response = 
                analysisService.clusterNews(request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/indicators/available")
    @Operation(summary = "분석 가능한 경제 지표 목록", description = "시스템에서 분석 가능한 경제 지표 목록을 조회합니다.")
    public ResponseEntity<List<Map<String, Object>>> getAvailableIndicators() {
        log.info("분석 가능한 경제 지표 목록 조회");
        
        List<Map<String, Object>> indicators = analysisService.getAvailableIndicators();
        return ResponseEntity.ok(indicators);
    }

    @GetMapping("/indicators/keywords")
    @Operation(summary = "키워드-지표 매핑 조회", description = "키워드와 경제 지표 간의 매핑 정보를 조회합니다.")
    public ResponseEntity<Map<String, List<String>>> getKeywordIndicatorMapping() {
        log.info("키워드-지표 매핑 조회");
        
        Map<String, List<String>> mapping = analysisService.getKeywordIndicatorMapping();
        return ResponseEntity.ok(mapping);
    }

    @PostMapping("/indicators/keywords")
    @Operation(summary = "키워드-지표 매핑 업데이트", description = "키워드와 경제 지표 간의 매핑을 업데이트합니다.")
    public ResponseEntity<Map<String, List<String>>> updateKeywordIndicatorMapping(
            @RequestBody Map<String, List<String>> mappingData,
            Authentication authentication) {
        log.info("키워드-지표 매핑 업데이트 - 사용자: {}", authentication.getName());
        
        Map<String, List<String>> updatedMapping = 
                analysisService.updateKeywordIndicatorMapping(mappingData, authentication.getName());
        return ResponseEntity.ok(updatedMapping);
    }

    @GetMapping("/history")
    @Operation(summary = "분석 이력 조회", description = "사용자의 분석 이력을 조회합니다.")
    public ResponseEntity<Page<Object>> getAnalysisHistory(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        log.info("분석 이력 조회 - 사용자: {}", authentication.getName());
        
        Page<Object> history = analysisService.getAnalysisHistory(pageable, authentication.getName());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/history/{id}")
    @Operation(summary = "분석 상세 조회", description = "특정 분석의 상세 정보를 조회합니다.")
    public ResponseEntity<Object> getAnalysisDetail(
            @PathVariable Long id,
            Authentication authentication) {
        log.info("분석 상세 조회 - ID: {}, 사용자: {}", id, authentication.getName());
        
        Object detail = analysisService.getAnalysisDetail(id, authentication.getName());
        return ResponseEntity.ok(detail);
    }

    @DeleteMapping("/history/{id}")
    @Operation(summary = "분석 삭제", description = "특정 분석을 삭제합니다.")
    public ResponseEntity<Void> deleteAnalysis(
            @PathVariable Long id,
            Authentication authentication) {
        log.info("분석 삭제 - ID: {}, 사용자: {}", id, authentication.getName());
        
        analysisService.deleteAnalysis(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch")
    @Operation(summary = "일괄 분석", description = "여러 분석을 한 번에 실행합니다.")
    public ResponseEntity<Object> batchAnalysis(
            @RequestBody Map<String, Object> batchRequest,
            Authentication authentication) {
        log.info("일괄 분석 요청 - 사용자: {}", authentication.getName());
        
        Object result = analysisService.batchAnalysis(batchRequest, authentication.getName());
        return ResponseEntity.ok(result);
    }
}