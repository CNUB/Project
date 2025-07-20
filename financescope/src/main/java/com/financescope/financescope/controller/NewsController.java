package com.financescope.financescope.controller;

import com.financescope.financescope.dto.news.NewsDTO;
import com.financescope.financescope.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@RestController
@RequestMapping("/news")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "News", description = "뉴스 수집 및 관리 API")
public class NewsController {

    private final NewsService newsService;

    @PostMapping("/crawl")
    @Operation(summary = "뉴스 크롤링 시작", description = "지정된 조건으로 뉴스 크롤링을 시작합니다.")
    public ResponseEntity<NewsDTO.CrawlResponse> startCrawling(
            @Valid @RequestBody NewsDTO.CrawlRequest request,
            Authentication authentication) {
        log.info("뉴스 크롤링 시작 요청 - 사용자: {}, 키워드: {}", 
                authentication.getName(), request.getKeywords());
        
        NewsDTO.CrawlResponse response = newsService.startCrawling(request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/crawl/status/{jobId}")
    @Operation(summary = "크롤링 상태 확인", description = "크롤링 작업의 진행상태를 확인합니다.")
    public ResponseEntity<NewsDTO.CrawlStatusResponse> getCrawlingStatus(
            @PathVariable String jobId,
            Authentication authentication) {
        log.info("크롤링 상태 확인 - 작업ID: {}, 사용자: {}", jobId, authentication.getName());
        
        NewsDTO.CrawlStatusResponse response = newsService.getCrawlingStatus(jobId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/crawl/cancel/{jobId}")
    @Operation(summary = "크롤링 취소", description = "진행중인 크롤링 작업을 취소합니다.")
    public ResponseEntity<NewsDTO.CrawlResponse> cancelCrawling(
            @PathVariable String jobId,
            Authentication authentication) {
        log.info("크롤링 취소 요청 - 작업ID: {}, 사용자: {}", jobId, authentication.getName());
        
        NewsDTO.CrawlResponse response = newsService.cancelCrawling(jobId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "뉴스 목록 조회", description = "수집된 뉴스 목록을 페이징하여 조회합니다.")
    public ResponseEntity<Page<NewsDTO.NewsResponse>> getNews(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Authentication authentication) {
        log.info("뉴스 목록 조회 - 사용자: {}, 키워드: {}", authentication.getName(), keyword);
        
        Page<NewsDTO.NewsResponse> response = newsService.getNews(
                pageable, keyword, source, category, startDate, endDate, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "뉴스 상세 조회", description = "특정 뉴스의 상세 정보를 조회합니다.")
    public ResponseEntity<NewsDTO.NewsResponse> getNewsById(
            @PathVariable Long id,
            Authentication authentication) {
        log.info("뉴스 상세 조회 - ID: {}, 사용자: {}", id, authentication.getName());
        
        NewsDTO.NewsResponse response = newsService.getNewsById(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/summarize")
    @Operation(summary = "뉴스 요약 생성", description = "선택한 뉴스들의 요약을 생성합니다.")
    public ResponseEntity<List<NewsDTO.SummaryResponse>> summarizeNews(
            @Valid @RequestBody NewsDTO.SummarizeRequest request,
            Authentication authentication) {
        log.info("뉴스 요약 요청 - 사용자: {}, 뉴스 수: {}, 모델: {}", 
                authentication.getName(), request.getNewsIds().size(), request.getModel());
        
        List<NewsDTO.SummaryResponse> response = newsService.summarizeNews(request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sources")
    @Operation(summary = "뉴스 소스 목록", description = "사용 가능한 뉴스 소스 목록을 조회합니다.")
    public ResponseEntity<List<String>> getNewsSources() {
        log.info("뉴스 소스 목록 조회");
        
        List<String> sources = newsService.getAvailableSources();
        return ResponseEntity.ok(sources);
    }

    @GetMapping("/categories")
    @Operation(summary = "뉴스 카테고리 목록", description = "사용 가능한 뉴스 카테고리 목록을 조회합니다.")
    public ResponseEntity<List<String>> getNewsCategories() {
        log.info("뉴스 카테고리 목록 조회");
        
        List<String> categories = newsService.getAvailableCategories();
        return ResponseEntity.ok(categories);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "뉴스 삭제", description = "특정 뉴스를 삭제합니다.")
    public ResponseEntity<Void> deleteNews(
            @PathVariable Long id,
            Authentication authentication) {
        log.info("뉴스 삭제 - ID: {}, 사용자: {}", id, authentication.getName());
        
        newsService.deleteNews(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/batch")
    @Operation(summary = "뉴스 일괄 삭제", description = "여러 뉴스를 일괄 삭제합니다.")
    public ResponseEntity<Void> deleteNewsInBatch(
            @RequestBody List<Long> newsIds,
            Authentication authentication) {
        log.info("뉴스 일괄 삭제 - 수: {}, 사용자: {}", newsIds.size(), authentication.getName());
        
        newsService.deleteNewsInBatch(newsIds, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/favorite")
    @Operation(summary = "뉴스 즐겨찾기 추가", description = "뉴스를 즐겨찾기에 추가합니다.")
    public ResponseEntity<Void> addToFavorites(
            @PathVariable Long id,
            Authentication authentication) {
        log.info("뉴스 즐겨찾기 추가 - ID: {}, 사용자: {}", id, authentication.getName());
        
        newsService.addToFavorites(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/favorite")
    @Operation(summary = "뉴스 즐겨찾기 제거", description = "뉴스를 즐겨찾기에서 제거합니다.")
    public ResponseEntity<Void> removeFromFavorites(
            @PathVariable Long id,
            Authentication authentication) {
        log.info("뉴스 즐겨찾기 제거 - ID: {}, 사용자: {}", id, authentication.getName());
        
        newsService.removeFromFavorites(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/favorites")
    @Operation(summary = "즐겨찾기 뉴스 조회", description = "사용자의 즐겨찾기 뉴스 목록을 조회합니다.")
    public ResponseEntity<Page<NewsDTO.NewsResponse>> getFavoriteNews(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        log.info("즐겨찾기 뉴스 조회 - 사용자: {}", authentication.getName());
        
        Page<NewsDTO.NewsResponse> response = newsService.getFavoriteNews(pageable, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistics")
    @Operation(summary = "뉴스 통계", description = "사용자의 뉴스 수집 통계를 조회합니다.")
    public ResponseEntity<Object> getNewsStatistics(
            @RequestParam(required = false) String period,
            Authentication authentication) {
        log.info("뉴스 통계 조회 - 사용자: {}, 기간: {}", authentication.getName(), period);
        
        Object statistics = newsService.getNewsStatistics(authentication.getName(), period);
        return ResponseEntity.ok(statistics);
    }

    @PostMapping("/export")
    @Operation(summary = "뉴스 데이터 내보내기", description = "뉴스 데이터를 지정된 형식으로 내보냅니다.")
    public ResponseEntity<Object> exportNews(
            @RequestParam String format, // csv, excel, json
            @RequestParam(required = false) List<Long> newsIds,
            Authentication authentication) {
        log.info("뉴스 데이터 내보내기 - 사용자: {}, 형식: {}", authentication.getName(), format);
        
        Object exportResult = newsService.exportNews(format, newsIds, authentication.getName());
        return ResponseEntity.ok(exportResult);
    }
}