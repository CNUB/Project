package com.financescope.financescope.controller;

import com.financescope.financescope.dto.report.ReportDTO;     // ✅ 올바른 경로
import com.financescope.financescope.service.ReportService;    // ✅ 올바른 경로
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reports", description = "리포트 생성 및 시각화 API")
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/generate")
    @Operation(summary = "리포트 생성", description = "분석 결과를 바탕으로 리포트를 생성합니다.")
    public ResponseEntity<ReportDTO.ReportGenerationResponse> generateReport(
            @Valid @RequestBody ReportDTO.ReportGenerationRequest request,
            Authentication authentication) {
        log.info("리포트 생성 요청 - 사용자: {}, 타입: {}, 형식: {}", 
                authentication.getName(), request.getReportType(), request.getFormat());
        
        ReportDTO.ReportGenerationResponse response = 
                reportService.generateReport(request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/{reportId}")
    @Operation(summary = "리포트 다운로드", description = "생성된 리포트를 다운로드합니다.")
    public ResponseEntity<Resource> downloadReport(
            @PathVariable String reportId,
            Authentication authentication) {
        log.info("리포트 다운로드 - ID: {}, 사용자: {}", reportId, authentication.getName());
        
        Resource resource = reportService.downloadReport(reportId, authentication.getName());
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @PostMapping("/network-map")
    @Operation(summary = "네트워크 맵 생성", description = "뉴스와 지표 간의 연결 네트워크 맵을 생성합니다.")
    public ResponseEntity<ReportDTO.NetworkMapResponse> generateNetworkMap(
            @Valid @RequestBody ReportDTO.NetworkMapRequest request,
            Authentication authentication) {
        log.info("네트워크 맵 생성 요청 - 사용자: {}", authentication.getName());
        
        ReportDTO.NetworkMapResponse response = 
                reportService.generateNetworkMap(request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/templates")
    @Operation(summary = "리포트 템플릿 목록", description = "사용 가능한 리포트 템플릿 목록을 조회합니다.")
    public ResponseEntity<Object> getReportTemplates() {
        log.info("리포트 템플릿 목록 조회");
        
        Object templates = reportService.getReportTemplates();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/history")
    @Operation(summary = "리포트 생성 이력", description = "사용자의 리포트 생성 이력을 조회합니다.")
    public ResponseEntity<Object> getReportHistory(
            Authentication authentication) {
        log.info("리포트 생성 이력 조회 - 사용자: {}", authentication.getName());
        
        Object history = reportService.getReportHistory(authentication.getName());
        return ResponseEntity.ok(history);
    }
}