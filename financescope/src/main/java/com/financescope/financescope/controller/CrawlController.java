package com.financescope.financescope.controller;

import com.financescope.financescope.dto.CrawlRequest;
import com.financescope.financescope.dto.CrawlResponse;
import com.financescope.financescope.dto.CrawlStatusResponse;
import com.financescope.financescope.service.CrawlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/crawl")
@RequiredArgsConstructor
public class CrawlController {

    private final CrawlService crawlService;

    @PostMapping("/start")
    public ResponseEntity<CrawlResponse> startCrawling(@RequestBody CrawlRequest request) {
        String jobId = UUID.randomUUID().toString();
        crawlService.startCrawling(jobId, request);
        return ResponseEntity.ok(new CrawlResponse(jobId));
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<CrawlStatusResponse> getCrawlStatus(@PathVariable String jobId) {
        CrawlStatusResponse status = crawlService.getStatus(jobId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    @PostMapping("/cancel/{jobId}")
    public ResponseEntity<Void> cancelCrawling(@PathVariable String jobId) {
        crawlService.cancelCrawling(jobId);
        return ResponseEntity.ok().build();
    }
}
