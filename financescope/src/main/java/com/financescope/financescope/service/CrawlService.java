package com.financescope.financescope.service;

import com.financescope.financescope.dto.CrawlRequest;
import com.financescope.financescope.dto.CrawlStatusResponse;
import com.financescope.financescope.entity.News;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CrawlService {

    private final ConcurrentHashMap<String, CrawlStatusResponse> jobStatuses = new ConcurrentHashMap<>();

    @Async("taskExecutor")
    public void startCrawling(String jobId, CrawlRequest request) {
        log.info("Job {} started with request: {}", jobId, request);
        try {
            // 초기 상태 설정
            CrawlStatusResponse initialStatus = CrawlStatusResponse.builder()
                    .status("INITIALIZING")
                    .progress(0)
                    .results(new ArrayList<>()).build();
            jobStatuses.put(jobId, initialStatus);

            // --- 실제 크롤링 로직 시작 ---
            // 이 부분에 WebClient를 사용한 실제 크롤링 코드를 구현해야 합니다.
            // 여기서는 시뮬레이션을 위해 잠시 대기하고 더미 데이터를 생성합니다.
            List<News> collectedNews = new ArrayList<>();
            int totalSteps = 10;
            for (int i = 1; i <= totalSteps; i++) {
                // 작업이 취소되었는지 확인
                if (jobStatuses.get(jobId).getStatus().equals("CANCELLED")) {
                    log.info("Job {} was cancelled.", jobId);
                    break;
                }

                TimeUnit.SECONDS.sleep(2); // 2초 대기 시뮬레이션
                int progress = i * 10;

                // 더미 뉴스 데이터 생성
                News dummyNews = new News();
                dummyNews.setTitle(request.getKeywords().get(0) + " 관련 뉴스 " + i);
                dummyNews.setSource(request.getSource());
                                dummyNews.setOriginalUrl("http://example.com/news/" + UUID.randomUUID());
                dummyNews.setKeyword(request.getKeywords().get(0));
                dummyNews.setPublishedDate(LocalDateTime.now().minusDays(i));
                dummyNews.setCrawledDate(LocalDateTime.now());
                collectedNews.add(dummyNews);

                CrawlStatusResponse currentStatus = CrawlStatusResponse.builder()
                        .status("RUNNING")
                        .progress(progress)
                        .results(new ArrayList<>(collectedNews))
                        .build();
                jobStatuses.put(jobId, currentStatus);
                log.info("Job {} progress: {}%", jobId, progress);
            }
            // --- 실제 크롤링 로직 종료 ---

            // 최종 상태 업데이트
            if (!jobStatuses.get(jobId).getStatus().equals("CANCELLED")) {
                CrawlStatusResponse finalStatus = CrawlStatusResponse.builder()
                        .status("COMPLETED")
                        .progress(100)
                        .results(collectedNews)
                        .build();
                jobStatuses.put(jobId, finalStatus);
                log.info("Job {} completed.", jobId);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CrawlStatusResponse errorStatus = CrawlStatusResponse.builder()
                    .status("FAILED")
                    .error("크롤링 작업이 중단되었습니다: " + e.getMessage())
                    .progress(jobStatuses.get(jobId).getProgress())
                    .results(jobStatuses.get(jobId).getResults())
                    .build();
            jobStatuses.put(jobId, errorStatus);
            log.error("Job {} was interrupted.", jobId, e);
        } catch (Exception e) {
            CrawlStatusResponse errorStatus = CrawlStatusResponse.builder()
                    .status("FAILED")
                    .error("크롤링 중 오류 발생: " + e.getMessage())
                    .progress(jobStatuses.get(jobId) != null ? jobStatuses.get(jobId).getProgress() : 0)
                    .results(jobStatuses.get(jobId) != null ? jobStatuses.get(jobId).getResults() : new ArrayList<>())                    
                    .build();
            jobStatuses.put(jobId, errorStatus);
            log.error("Error during crawling for job {}.", jobId, e);
        }
    }

    public CrawlStatusResponse getStatus(String jobId) {
        return jobStatuses.get(jobId);
    }

    public void cancelCrawling(String jobId) {
        CrawlStatusResponse currentStatus = jobStatuses.get(jobId);
        if (currentStatus != null && !currentStatus.getStatus().equals("COMPLETED")) {
            CrawlStatusResponse cancelledStatus = CrawlStatusResponse.builder()
                    .status("CANCELLED")
                    .progress(currentStatus.getProgress())
                    .results(currentStatus.getResults())
                    .error("사용자에 의해 취소됨")
                    .build();
            jobStatuses.put(jobId, cancelledStatus);
            log.info("Job {} cancellation requested.", jobId);
        }
    }
}
