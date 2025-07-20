package com.financescope.financescope.controller;

import com.financescope.financescope.dto.prediction.PredictionDTO;
import com.financescope.financescope.service.PredictionService;
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
@RequestMapping("/prediction")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Prediction", description = "시계열 예측 관련 API")
public class PredictionController {

    private final PredictionService predictionService;

    @PostMapping("/run")
    @Operation(summary = "예측 실행", description = "경제 지표에 대한 시계열 예측을 실행합니다.")
    public ResponseEntity<PredictionDTO.PredictionResponse> runPrediction(
            @Valid @RequestBody PredictionDTO.PredictionRequest request,
            Authentication authentication) {
        log.info("예측 실행 요청 - 사용자: {}, 지표: {}, 모델: {}, 기간: {}일", 
                authentication.getName(), request.getIndicator(), request.getModel(), request.getPredictionDays());
        
        PredictionDTO.PredictionResponse response = 
                predictionService.runPrediction(request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{id}")
    @Operation(summary = "예측 상태 확인", description = "예측 작업의 진행상태를 확인합니다.")
    public ResponseEntity<PredictionDTO.PredictionStatusResponse> getPredictionStatus(
            @PathVariable Long id,
            Authentication authentication) {
        log.info("예측 상태 확인 - ID: {}, 사용자: {}", id, authentication.getName());
        
        PredictionDTO.PredictionStatusResponse response = 
                predictionService.getPredictionStatus(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "예측 목록 조회", description = "사용자의 예측 목록을 페이징하여 조회합니다.")
    public ResponseEntity<Page<PredictionDTO.PredictionResponse>> getPredictions(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String indicator,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Authentication authentication) {
        log.info("예측 목록 조회 - 사용자: {}, 지표: {}", authentication.getName(), indicator);
        
        Page<PredictionDTO.PredictionResponse> response = predictionService.getPredictions(
                pageable, indicator, model, startDate, endDate, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "예측 상세 조회", description = "특정 예측의 상세 정보를 조회합니다.")
    public ResponseEntity<PredictionDTO.PredictionResponse> getPredictionById(
            @PathVariable Long id,
            Authentication authentication) {
        log.info("예측 상세 조회 - ID: {}, 사용자: {}", id, authentication.getName());
        
        PredictionDTO.PredictionResponse response = 
                predictionService.getPredictionById(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "예측 삭제", description = "특정 예측을 삭제합니다.")
    public ResponseEntity<Void> deletePrediction(
            @PathVariable Long id,
            Authentication authentication) {
        log.info("예측 삭제 - ID: {}, 사용자: {}", id, authentication.getName());
        
        predictionService.deletePrediction(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/models")
    @Operation(summary = "사용 가능한 예측 모델 목록", description = "시스템에서 지원하는 예측 모델 목록을 조회합니다.")
    public ResponseEntity<List<Map<String, Object>>> getAvailableModels() {
        log.info("사용 가능한 예측 모델 목록 조회");
        
        List<Map<String, Object>> models = predictionService.getAvailableModels();
        return ResponseEntity.ok(models);
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "예측 검증", description = "예측 결과를 실제 데이터와 비교하여 검증합니다.")
    public ResponseEntity<Object> validatePrediction(
            @PathVariable Long id,
            @RequestBody Map<String, Object> actualData,
            Authentication authentication) {
        log.info("예측 검증 - ID: {}, 사용자: {}", id, authentication.getName());
        
        Object validationResult = predictionService.validatePrediction(id, actualData, authentication.getName());
        return ResponseEntity.ok(validationResult);
    }

    @GetMapping("/performance")
    @Operation(summary = "모델 성능 통계", description = "사용자의 예측 모델 성능 통계를 조회합니다.")
    public ResponseEntity<Object> getModelPerformanceStats(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String indicator,
            Authentication authentication) {
        log.info("모델 성능 통계 조회 - 사용자: {}, 기간: {}", authentication.getName(), period);
        
        Object stats = predictionService.getModelPerformanceStats(authentication.getName(), period, indicator);
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/compare")
    @Operation(summary = "예측 결과 비교", description = "여러 예측 결과를 비교합니다.")
    public ResponseEntity<Object> comparePredictions(
            @RequestBody List<Long> predictionIds,
            Authentication authentication) {
        log.info("예측 결과 비교 - 사용자: {}, 예측 수: {}", authentication.getName(), predictionIds.size());
        
        Object comparison = predictionService.comparePredictions(predictionIds, authentication.getName());
        return ResponseEntity.ok(comparison);
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "예측 공유", description = "예측 결과를 다른 사용자와 공유합니다.")
    public ResponseEntity<Object> sharePrediction(
            @PathVariable Long id,
            @RequestBody Map<String, Object> shareRequest,
            Authentication authentication) {
        log.info("예측 공유 - ID: {}, 사용자: {}", id, authentication.getName());
        
        Object shareResult = predictionService.sharePrediction(id, shareRequest, authentication.getName());
        return ResponseEntity.ok(shareResult);
    }

    @PostMapping("/batch")
    @Operation(summary = "일괄 예측", description = "여러 지표에 대해 일괄 예측을 실행합니다.")
    public ResponseEntity<Object> batchPrediction(
            @RequestBody Map<String, Object> batchRequest,
            Authentication authentication) {
        log.info("일괄 예측 요청 - 사용자: {}", authentication.getName());
        
        Object result = predictionService.batchPrediction(batchRequest, authentication.getName());
        return ResponseEntity.ok(result);
    }
}
