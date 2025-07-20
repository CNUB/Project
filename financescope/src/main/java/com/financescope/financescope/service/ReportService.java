package com.financescope.financescope.service;

import com.financescope.financescope.dto.report.ReportDTO;
import com.financescope.financescope.entity.*;
import com.financescope.financescope.exception.BusinessException;
import com.financescope.financescope.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReportService {

    private final UserRepository userRepository;
    private final NewsRepository newsRepository;
    private final IndicatorAnalysisRepository indicatorAnalysisRepository;
    private final PredictionRepository predictionRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.file.report-dir:./reports}")
    private String reportDirectory;

    public ReportDTO.ReportGenerationResponse generateReport(
            ReportDTO.ReportGenerationRequest request, String userEmail) {
        log.info("리포트 생성 시작 - 사용자: {}, 타입: {}", userEmail, request.getReportType());

        User user = findUserByEmail(userEmail);
        
        try {
            // 리포트 ID 생성
            String reportId = generateReportId(user.getId(), request.getReportType());
            
            // 데이터 수집
            Map<String, Object> reportData = collectReportData(request, user);
            
            // 리포트 파일 생성
            String fileName = generateReportFile(reportId, request, reportData);
            
            // 다운로드 URL 생성
            String downloadUrl = "/api/reports/download/" + reportId;
            
            return ReportDTO.ReportGenerationResponse.builder()
                    .success(true)
                    .message("리포트가 생성되었습니다.")
                    .reportId(reportId)
                    .downloadUrl(downloadUrl)
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();

        } catch (Exception e) {
            log.error("리포트 생성 실패: {}", e.getMessage());
            throw new BusinessException("리포트 생성에 실패했습니다: " + e.getMessage());
        }
    }

    public Resource downloadReport(String reportId, String userEmail) {
        try {
            Path filePath = Paths.get(reportDirectory).resolve(reportId);
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new BusinessException("리포트 파일을 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            log.error("리포트 다운로드 실패: {}", e.getMessage());
            throw new BusinessException("리포트 다운로드에 실패했습니다.");
        }
    }

    public ReportDTO.NetworkMapResponse generateNetworkMap(
            ReportDTO.NetworkMapRequest request, String userEmail) {
        log.info("네트워크 맵 생성 - 사용자: {}", userEmail);

        User user = findUserByEmail(userEmail);
        
        try {
            List<ReportDTO.NetworkNode> nodes = new ArrayList<>();
            List<ReportDTO.NetworkLink> links = new ArrayList<>();
            
            // 지표 분석 데이터에서 노드와 링크 생성
            if (request.getAnalysisIds() != null) {
                List<IndicatorAnalysis> analyses = indicatorAnalysisRepository.findAllById(request.getAnalysisIds());
                
                for (IndicatorAnalysis analysis : analyses) {
                    // 지표 노드 추가
                    nodes.add(ReportDTO.NetworkNode.builder()
                            .id("indicator_" + analysis.getIndicator())
                            .type("indicator")
                            .name(analysis.getIndicator())
                            .value((double) analysis.getTotalMentions())
                            .category("economic_indicator")
                            .build());
                    
                    // 관련 뉴스 노드와 링크 추가
                    for (News news : analysis.getRelatedNews()) {
                        String newsId = "news_" + news.getId();
                        
                        // 뉴스 노드 추가
                        nodes.add(ReportDTO.NetworkNode.builder()
                                .id(newsId)
                                .type("news")
                                .name(news.getTitle())
                                .value(1.0)
                                .category("news")
                                .build());
                        
                        // 지표-뉴스 링크 추가
                        links.add(ReportDTO.NetworkLink.builder()
                                .source("indicator_" + analysis.getIndicator())
                                .target(newsId)
                                .value(1.0)
                                .type("relates_to")
                                .sentiment(news.getSentimentScore())
                                .build());
                    }
                }
            }
            
            // 중복 노드 제거
            Map<String, ReportDTO.NetworkNode> uniqueNodes = nodes.stream()
                    .collect(Collectors.toMap(ReportDTO.NetworkNode::getId, n -> n, (existing, replacement) -> existing));
            
            // 통계 정보 생성
            Map<String, Object> statistics = Map.of(
                    "totalNodes", uniqueNodes.size(),
                    "totalLinks", links.size(),
                    "indicatorNodes", uniqueNodes.values().stream().filter(n -> "indicator".equals(n.getType())).count(),
                    "newsNodes", uniqueNodes.values().stream().filter(n -> "news".equals(n.getType())).count()
            );
            
            return ReportDTO.NetworkMapResponse.builder()
                    .nodes(new ArrayList<>(uniqueNodes.values()))
                    .links(links)
                    .statistics(statistics)
                    .build();

        } catch (Exception e) {
            log.error("네트워크 맵 생성 실패: {}", e.getMessage());
            throw new BusinessException("네트워크 맵 생성에 실패했습니다: " + e.getMessage());
        }
    }

    public Object getReportTemplates() {
        List<Map<String, Object>> templates = Arrays.asList(
                Map.of("id", "summary", "name", "요약 리포트", "description", "주요 분석 결과 요약"),
                Map.of("id", "detailed", "name", "상세 리포트", "description", "전체 분석 과정과 결과"),
                Map.of("id", "executive", "name", "임원용 요약", "description", "경영진을 위한 핵심 인사이트"),
                Map.of("id", "presentation", "name", "프레젠테이션", "description", "발표용 슬라이드 형태")
        );
        
        return Map.of("templates", templates);
    }

    public Object getReportHistory(String userEmail) {
        User user = findUserByEmail(userEmail);
        
        // 실제 구현에서는 리포트 히스토리 테이블이 필요
        List<Map<String, Object>> history = Arrays.asList(
                Map.of(
                        "id", "report_001",
                        "name", "5월 경제지표 분석 리포트",
                        "type", "detailed",
                        "format", "pdf",
                        "createdAt", LocalDateTime.now().minusDays(5).toString(),
                        "status", "completed"
                ),
                Map.of(
                        "id", "report_002",
                        "name", "주간 시장 동향 요약",
                        "type", "summary",
                        "format", "docx",
                        "createdAt", LocalDateTime.now().minusDays(10).toString(),
                        "status", "completed"
                )
        );
        
        return Map.of("history", history);
    }

    // Private helper methods
    
    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));
    }

    private String generateReportId(Long userId, String reportType) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("report_%d_%s_%s", userId, reportType, timestamp);
    }

    private Map<String, Object> collectReportData(ReportDTO.ReportGenerationRequest request, User user) {
        Map<String, Object> data = new HashMap<>();
        
        // 뉴스 데이터 수집
        if (request.getIncludeSections().contains("news_summary") && request.getNewsIds() != null) {
            List<News> newsList = newsRepository.findAllById(request.getNewsIds());
            data.put("news", newsList);
        }
        
        // 지표 분석 데이터 수집
        if (request.getIncludeSections().contains("indicator_analysis") && request.getAnalysisIds() != null) {
            List<IndicatorAnalysis> analyses = indicatorAnalysisRepository.findAllById(request.getAnalysisIds());
            data.put("indicatorAnalyses", analyses);
        }
        
        // 예측 데이터 수집
        if (request.getIncludeSections().contains("prediction") && request.getPredictionIds() != null) {
            List<Prediction> predictions = predictionRepository.findAllById(request.getPredictionIds());
            data.put("predictions", predictions);
        }
        
        return data;
    }

    private String generateReportFile(String reportId, ReportDTO.ReportGenerationRequest request, Map<String, Object> data) {
        // 실제 구현에서는 템플릿 엔진(Thymeleaf, FreeMarker 등)을 사용하여 파일 생성
        String fileName = reportId + "." + request.getFormat();
        
        switch (request.getFormat().toLowerCase()) {
            case "pdf":
                return generatePdfReport(fileName, request, data);
            case "docx":
                return generateWordReport(fileName, request, data);
            case "pptx":
                return generatePowerPointReport(fileName, request, data);
            case "xlsx":
                return generateExcelReport(fileName, request, data);
            case "csv":
                return generateCsvReport(fileName, request, data);
            default:
                throw new BusinessException("지원하지 않는 파일 형식입니다: " + request.getFormat());
        }
    }

    private String generatePdfReport(String fileName, ReportDTO.ReportGenerationRequest request, Map<String, Object> data) {
        // PDF 생성 로직 (iText 사용)
        log.info("PDF 리포트 생성: {}", fileName);
        // TODO: 실제 PDF 생성 구현
        return fileName;
    }

    private String generateWordReport(String fileName, ReportDTO.ReportGenerationRequest request, Map<String, Object> data) {
        // Word 문서 생성 로직 (Apache POI 사용)
        log.info("Word 리포트 생성: {}", fileName);
        // TODO: 실제 Word 문서 생성 구현
        return fileName;
    }

    private String generatePowerPointReport(String fileName, ReportDTO.ReportGenerationRequest request, Map<String, Object> data) {
        // PowerPoint 생성 로직 (Apache POI 사용)
        log.info("PowerPoint 리포트 생성: {}", fileName);
        // TODO: 실제 PowerPoint 생성 구현
        return fileName;
    }

    private String generateExcelReport(String fileName, ReportDTO.ReportGenerationRequest request, Map<String, Object> data) {
        // Excel 생성 로직 (Apache POI 사용)
        log.info("Excel 리포트 생성: {}", fileName);
        // TODO: 실제 Excel 생성 구현
        return fileName;
    }

    private String generateCsvReport(String fileName, ReportDTO.ReportGenerationRequest request, Map<String, Object> data) {
        // CSV 생성 로직 (OpenCSV 사용)
        log.info("CSV 리포트 생성: {}", fileName);
        // TODO: 실제 CSV 생성 구현
        return fileName;
    }
}