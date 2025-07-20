package com.financescope.financescope.service.external;

import com.financescope.financescope.dto.news.NewsDTO;
import com.financescope.financescope.entity.News;
import com.financescope.financescope.entity.User;
import com.financescope.financescope.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsCrawlerService {

    private final NewsRepository newsRepository;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${external-api.news.naver.client-id:}")
    private String naverClientId;
    
    @Value("${external-api.news.naver.client-secret:}")
    private String naverClientSecret;
    
    @Value("${app.crawling.max-concurrent-requests:10}")
    private int maxConcurrentRequests;
    
    @Value("${app.crawling.request-delay:1000}")
    private long requestDelay;
    
    @Value("${app.crawling.user-agent:FinanceScope-Bot/1.0}")
    private String userAgent;
    
    // 진행 중인 크롤링 작업 상태 관리
    private final Map<String, CrawlingJobStatus> crawlingJobs = new ConcurrentHashMap<>();

    public void startCrawling(String jobId, NewsDTO.CrawlRequest request, User user) {
        log.info("크롤링 작업 시작 - JobID: {}, 사용자: {}", jobId, user.getEmail());
        
        CrawlingJobStatus jobStatus = new CrawlingJobStatus();
        jobStatus.setJobId(jobId);
        jobStatus.setUser(user);
        jobStatus.setRequest(request);
        jobStatus.setStatus("RUNNING");
        jobStatus.setStartTime(LocalDateTime.now());
        jobStatus.setProgress(0);
        jobStatus.setMessage("크롤링 시작");
        
        crawlingJobs.put(jobId, jobStatus);
        
        // 비동기로 크롤링 실행
        executeCrawlingAsync(jobStatus);
    }
    
    @Async
    public void executeCrawlingAsync(CrawlingJobStatus jobStatus) {
        try {
            List<News> collectedNews = new ArrayList<>();
            
            for (String keyword : jobStatus.getRequest().getKeywords()) {
                log.info("키워드 '{}' 크롤링 시작", keyword);
                
                jobStatus.setMessage("키워드 '" + keyword + "' 크롤링 중");
                updateProgress(jobStatus);
                
                List<News> keywordNews = crawlNewsByKeyword(keyword, jobStatus);
                collectedNews.addAll(keywordNews);
                
                // 요청 간 지연
                Thread.sleep(requestDelay);
            }
            
            // 중복 제거
            collectedNews = removeDuplicates(collectedNews);
            
            // 데이터베이스 저장
            jobStatus.setMessage("뉴스 데이터 저장 중");
            saveNewsToDatabase(collectedNews, jobStatus);
            
            // 작업 완료
            jobStatus.setStatus("COMPLETED");
            jobStatus.setMessage("크롤링 완료: " + collectedNews.size() + "개 뉴스 수집");
            jobStatus.setProgress(100);
            jobStatus.setNewsCount(collectedNews.size());
            jobStatus.setEndTime(LocalDateTime.now());
            
            log.info("크롤링 작업 완료 - JobID: {}, 수집된 뉴스: {}개", 
                    jobStatus.getJobId(), collectedNews.size());
            
        } catch (Exception e) {
            log.error("크롤링 작업 실패 - JobID: {}, 오류: {}", jobStatus.getJobId(), e.getMessage());
            
            jobStatus.setStatus("FAILED");
            jobStatus.setMessage("크롤링 실패: " + e.getMessage());
            jobStatus.setError(e.getMessage());
            jobStatus.setEndTime(LocalDateTime.now());
        }
    }
    
    public NewsDTO.CrawlStatusResponse getCrawlingStatus(String jobId, User user) {
        CrawlingJobStatus jobStatus = crawlingJobs.get(jobId);
        
        if (jobStatus == null) {
            throw new RuntimeException("크롤링 작업을 찾을 수 없습니다: " + jobId);
        }
        
        if (!jobStatus.getUser().equals(user)) {
            throw new RuntimeException("접근 권한이 없습니다.");
        }
        
        return NewsDTO.CrawlStatusResponse.builder()
                .jobId(jobId)
                .status(jobStatus.getStatus())
                .progress(jobStatus.getProgress())
                .message(jobStatus.getMessage())
                .newsCount(jobStatus.getNewsCount())
                .completed("COMPLETED".equals(jobStatus.getStatus()) || "FAILED".equals(jobStatus.getStatus()))
                .error(jobStatus.getError())
                .build();
    }
    
    public void cancelCrawling(String jobId, User user) {
        CrawlingJobStatus jobStatus = crawlingJobs.get(jobId);
        
        if (jobStatus == null) {
            throw new RuntimeException("크롤링 작업을 찾을 수 없습니다: " + jobId);
        }
        
        if (!jobStatus.getUser().equals(user)) {
            throw new RuntimeException("접근 권한이 없습니다.");
        }
        
        jobStatus.setStatus("CANCELLED");
        jobStatus.setMessage("사용자에 의해 취소됨");
        jobStatus.setEndTime(LocalDateTime.now());
        
        log.info("크롤링 작업 취소 - JobID: {}", jobId);
    }
    
    private List<News> crawlNewsByKeyword(String keyword, CrawlingJobStatus jobStatus) {
        List<News> newsList = new ArrayList<>();
        
        try {
            switch (jobStatus.getRequest().getSource().toLowerCase()) {
                case "네이버":
                case "naver":
                    newsList = crawlNaverNews(keyword, jobStatus);
                    break;
                case "구글":
                case "google":
                    newsList = crawlGoogleNews(keyword, jobStatus);
                    break;
                default:
                    log.warn("지원하지 않는 뉴스 소스: {}", jobStatus.getRequest().getSource());
            }
        } catch (Exception e) {
            log.error("키워드 '{}' 크롤링 실패: {}", keyword, e.getMessage());
        }
        
        return newsList;
    }
    
    private List<News> crawlNaverNews(String keyword, CrawlingJobStatus jobStatus) throws Exception {
        List<News> newsList = new ArrayList<>();
        
        if (naverClientId.isEmpty() || naverClientSecret.isEmpty()) {
            // API 키가 없는 경우 웹 스크래핑 사용
            return crawlNaverNewsWeb(keyword, jobStatus);
        }
        
        // 네이버 API 사용
        WebClient webClient = webClientBuilder.build();
        
        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String apiUrl = String.format("https://openapi.naver.com/v1/search/news.json?query=%s&display=%d&sort=%s",
                encodedKeyword, 
                Math.min(jobStatus.getRequest().getMaxResults(), 100),
                "sim".equals(jobStatus.getRequest().getSortBy()) ? "sim" : "date");
        
        try {
            Map<String, Object> response = webClient.get()
                    .uri(apiUrl)
                    .header("X-Naver-Client-Id", naverClientId)
                    .header("X-Naver-Client-Secret", naverClientSecret)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("items")) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                
                for (Map<String, Object> item : items) {
                    News news = createNewsFromNaverItem(item, keyword, jobStatus);
                    if (news != null) {
                        newsList.add(news);
                    }
                }
            }
        } catch (Exception e) {
            log.error("네이버 API 호출 실패: {}", e.getMessage());
            // API 실패 시 웹 스크래핑으로 대체
            return crawlNaverNewsWeb(keyword, jobStatus);
        }
        
        return newsList;
    }
    
    private List<News> crawlNaverNewsWeb(String keyword, CrawlingJobStatus jobStatus) throws Exception {
        List<News> newsList = new ArrayList<>();
        
        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String searchUrl = String.format("https://search.naver.com/search.naver?where=news&query=%s&sort=1", encodedKeyword);
        
        try {
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(userAgent)
                    .timeout(10000)
                    .get();
            
            Elements newsElements = doc.select(".list_news .bx");
            
            for (Element element : newsElements) {
                if (newsList.size() >= jobStatus.getRequest().getMaxResults()) {
                    break;
                }
                
                News news = parseNaverNewsElement(element, keyword, jobStatus);
                if (news != null) {
                    newsList.add(news);
                }
            }
        } catch (Exception e) {
            log.error("네이버 웹 스크래핑 실패: {}", e.getMessage());
        }
        
        return newsList;
    }
    
    private List<News> crawlGoogleNews(String keyword, CrawlingJobStatus jobStatus) throws Exception {
        List<News> newsList = new ArrayList<>();
        
        // Google News RSS 또는 웹 스크래핑 구현
        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String searchUrl = String.format("https://news.google.com/rss/search?q=%s&hl=ko&gl=KR&ceid=KR:ko", encodedKeyword);
        
        try {
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(userAgent)
                    .timeout(10000)
                    .get();
            
            Elements items = doc.select("item");
            
            for (Element item : items) {
                if (newsList.size() >= jobStatus.getRequest().getMaxResults()) {
                    break;
                }
                
                News news = parseGoogleNewsItem(item, keyword, jobStatus);
                if (news != null) {
                    newsList.add(news);
                }
            }
        } catch (Exception e) {
            log.error("구글 뉴스 크롤링 실패: {}", e.getMessage());
        }
        
        return newsList;
    }
    
    private News createNewsFromNaverItem(Map<String, Object> item, String keyword, CrawlingJobStatus jobStatus) {
        try {
            String title = cleanHtmlTags((String) item.get("title"));
            String link = (String) item.get("link");
            String description = cleanHtmlTags((String) item.get("description"));
            String pubDate = (String) item.get("pubDate");
            
            LocalDateTime publishedDate = parseNaverDate(pubDate);
            String contentHash = generateContentHash(title, link);
            
            return News.builder()
                    .title(title)
                    .originalUrl(link)
                    .content(description)
                    .source("네이버")
                    .keyword(keyword)
                    .publishedDate(publishedDate)
                    .crawledDate(LocalDateTime.now())
                    .contentHash(contentHash)
                    .crawlJobId(jobStatus.getJobId())
                    .collectedByUser(jobStatus.getUser())
                    .processingStatus(News.ProcessingStatus.RAW)
                    .build();
        } catch (Exception e) {
            log.error("네이버 뉴스 파싱 실패: {}", e.getMessage());
            return null;
        }
    }
    
    private News parseNaverNewsElement(Element element, String keyword, CrawlingJobStatus jobStatus) {
        try {
            Element titleElement = element.selectFirst(".news_tit");
            if (titleElement == null) return null;
            
            String title = titleElement.text();
            String link = titleElement.attr("href");
            
            Element summaryElement = element.selectFirst(".news_dsc");
            String summary = summaryElement != null ? summaryElement.text() : "";
            
            Element sourceElement = element.selectFirst(".info_group .press");
            String source = sourceElement != null ? sourceElement.text() : "네이버";
            
            String contentHash = generateContentHash(title, link);
            
            return News.builder()
                    .title(title)
                    .originalUrl(link)
                    .summary(summary)
                    .source(source)
                    .keyword(keyword)
                    .publishedDate(LocalDateTime.now()) // 정확한 날짜 파싱 필요
                    .crawledDate(LocalDateTime.now())
                    .contentHash(contentHash)
                    .crawlJobId(jobStatus.getJobId())
                    .collectedByUser(jobStatus.getUser())
                    .processingStatus(News.ProcessingStatus.RAW)
                    .build();
        } catch (Exception e) {
            log.error("네이버 뉴스 요소 파싱 실패: {}", e.getMessage());
            return null;
        }
    }
    
    private News parseGoogleNewsItem(Element item, String keyword, CrawlingJobStatus jobStatus) {
        try {
            String title = item.selectFirst("title").text();
            String link = item.selectFirst("link").text();
            String description = item.selectFirst("description").text();
            String pubDate = item.selectFirst("pubDate").text();
            
            LocalDateTime publishedDate = parseGoogleDate(pubDate);
            String contentHash = generateContentHash(title, link);
            
            return News.builder()
                    .title(title)
                    .originalUrl(link)
                    .content(description)
                    .source("구글 뉴스")
                    .keyword(keyword)
                    .publishedDate(publishedDate)
                    .crawledDate(LocalDateTime.now())
                    .contentHash(contentHash)
                    .crawlJobId(jobStatus.getJobId())
                    .collectedByUser(jobStatus.getUser())
                    .processingStatus(News.ProcessingStatus.RAW)
                    .build();
        } catch (Exception e) {
            log.error("구글 뉴스 파싱 실패: {}", e.getMessage());
            return null;
        }
    }
    
    private List<News> removeDuplicates(List<News> newsList) {
        Map<String, News> uniqueNews = new LinkedHashMap<>();
        
        for (News news : newsList) {
            String hash = news.getContentHash();
            if (!uniqueNews.containsKey(hash)) {
                uniqueNews.put(hash, news);
            }
        }
        
        int duplicateCount = newsList.size() - uniqueNews.size();
        if (duplicateCount > 0) {
            log.info("중복 뉴스 제거: {}개", duplicateCount);
        }
        
        return new ArrayList<>(uniqueNews.values());
    }
    
    private void saveNewsToDatabase(List<News> newsList, CrawlingJobStatus jobStatus) {
        try {
            newsRepository.saveAll(newsList);
            log.info("뉴스 데이터 저장 완료 - {}개", newsList.size());
        } catch (Exception e) {
            log.error("뉴스 데이터 저장 실패: {}", e.getMessage());
            throw new RuntimeException("뉴스 데이터 저장에 실패했습니다.", e);
        }
    }
    
    private void updateProgress(CrawlingJobStatus jobStatus) {
        int currentKeywordIndex = jobStatus.getRequest().getKeywords().indexOf(jobStatus.getCurrentKeyword());
        int totalKeywords = jobStatus.getRequest().getKeywords().size();
        
        if (currentKeywordIndex >= 0) {
            int progress = (currentKeywordIndex * 100) / totalKeywords;
            jobStatus.setProgress(Math.min(progress, 95)); // 95%까지만 진행률 표시
        }
    }
    
    private String cleanHtmlTags(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", "").replaceAll("&[^;]+;", "").trim();
    }
    
    private LocalDateTime parseNaverDate(String dateString) {
        try {
            // 네이버 API 날짜 형식: "Mon, ddMMMyyyy HH:mm:ss +0900"
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
            return LocalDateTime.parse(dateString, formatter);
        } catch (Exception e) {
            log.warn("네이버 날짜 파싱 실패: {}", dateString);
            return LocalDateTime.now();
        }
    }
    
    private LocalDateTime parseGoogleDate(String dateString) {
        try {
            // Google RSS 날짜 형식 파싱
            DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
            return LocalDateTime.parse(dateString, formatter);
        } catch (Exception e) {
            log.warn("구글 날짜 파싱 실패: {}", dateString);
            return LocalDateTime.now();
        }
    }
    
    private String generateContentHash(String title, String link) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = title + "|" + link;
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("해시 생성 실패: {}", e.getMessage());
            return UUID.randomUUID().toString();
        }
    }
    
    // 크롤링 작업 상태 관리 클래스
    private static class CrawlingJobStatus {
        private String jobId;
        private User user;
        private NewsDTO.CrawlRequest request;
        private String status;
        private int progress;
        private String message;
        private String error;
        private int newsCount;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String currentKeyword;
        
        // Getters and Setters
        public String getJobId() { return jobId; }
        public void setJobId(String jobId) { this.jobId = jobId; }
        
        public User getUser() { return user; }
        public void setUser(User user) { this.user = user; }
        
        public NewsDTO.CrawlRequest getRequest() { return request; }
        public void setRequest(NewsDTO.CrawlRequest request) { this.request = request; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public int getProgress() { return progress; }
        public void setProgress(int progress) { this.progress = progress; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public int getNewsCount() { return newsCount; }
        public void setNewsCount(int newsCount) { this.newsCount = newsCount; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        
        public String getCurrentKeyword() { return currentKeyword; }
        public void setCurrentKeyword(String currentKeyword) { this.currentKeyword = currentKeyword; }
    }
}