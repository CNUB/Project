package com.financescope.financescope.service.external;

import com.financescope.financescope.entity.News;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClusteringService {

    public ClusteringResult clusterNews(List<News> newsList, int clusterCount, String method) {
        log.info("뉴스 클러스터링 시작 - 뉴스 수: {}, 클러스터 수: {}, 방법: {}", 
                newsList.size(), clusterCount, method);
        
        try {
            switch (method.toLowerCase()) {
                case "kmeans":
                    return performKMeansClustering(newsList, clusterCount);
                case "hierarchical":
                    return performHierarchicalClustering(newsList, clusterCount);
                default:
                    return performSimpleClustering(newsList, clusterCount);
            }
        } catch (Exception e) {
            log.error("클러스터링 실패: {}", e.getMessage());
            return performSimpleClustering(newsList, clusterCount);
        }
    }
    
    private ClusteringResult performKMeansClustering(List<News> newsList, int clusterCount) {
        // 실제 구현에서는 ML 라이브러리 사용 (Weka, Smile 등)
        // 여기서는 간단한 시뮬레이션
        
        List<NewsCluster> clusters = new ArrayList<>();
        
        // 키워드 기반 간단 클러스터링
        Map<String, List<News>> keywordGroups = newsList.stream()
                .collect(Collectors.groupingBy(News::getKeyword));
        
        int clusterId = 1;
        for (Map.Entry<String, List<News>> entry : keywordGroups.entrySet()) {
            if (clusterId > clusterCount) break;
            
            String keyword = entry.getKey();
            List<News> newsInCluster = entry.getValue();
            
            // 클러스터 키워드 추출
            List<String> clusterKeywords = extractKeywordsFromNews(newsInCluster);
            
            NewsCluster cluster = new NewsCluster();
            cluster.setId("cluster-" + clusterId);
            cluster.setName("클러스터 " + clusterId + " (" + keyword + ")");
            cluster.setKeywords(clusterKeywords);
            cluster.setNews(newsInCluster);
            
            clusters.add(cluster);
            clusterId++;
        }
        
        // 남은 뉴스들을 기존 클러스터에 배정
        Set<News> assignedNews = clusters.stream()
                .flatMap(c -> c.getNews().stream())
                .collect(Collectors.toSet());
        
        List<News> unassignedNews = newsList.stream()
                .filter(news -> !assignedNews.contains(news))
                .collect(Collectors.toList());
        
        for (News news : unassignedNews) {
            // 가장 유사한 클러스터에 배정
            NewsCluster bestCluster = findBestCluster(news, clusters);
            if (bestCluster != null) {
                bestCluster.getNews().add(news);
            }
        }
        
        return new ClusteringResult(clusters);
    }
    
    private ClusteringResult performHierarchicalClustering(List<News> newsList, int clusterCount) {
        // 계층적 클러스터링 구현
        return performSimpleClustering(newsList, clusterCount);
    }
    
    private ClusteringResult performSimpleClustering(List<News> newsList, int clusterCount) {
        List<NewsCluster> clusters = new ArrayList<>();
        
        // 간단한 랜덤 클러스터링
        Collections.shuffle(newsList);
        
        int newsPerCluster = Math.max(1, newsList.size() / clusterCount);
        
        for (int i = 0; i < clusterCount; i++) {
            int startIndex = i * newsPerCluster;
            int endIndex = Math.min(startIndex + newsPerCluster, newsList.size());
            
            if (startIndex >= newsList.size()) break;
            
            List<News> clusterNews = newsList.subList(startIndex, endIndex);
            
            NewsCluster cluster = new NewsCluster();
            cluster.setId("cluster-" + (i + 1));
            cluster.setName("클러스터 " + (i + 1));
            cluster.setKeywords(extractKeywordsFromNews(clusterNews));
            cluster.setNews(new ArrayList<>(clusterNews));
            
            clusters.add(cluster);
        }
        
        return new ClusteringResult(clusters);
    }
    
    private List<String> extractKeywordsFromNews(List<News> newsList) {
        Map<String, Integer> keywordCount = new HashMap<>();
        
        for (News news : newsList) {
            // 제목에서 키워드 추출
            String[] words = news.getTitle().split("\\s+");
            for (String word : words) {
                word = word.replaceAll("[^가-힣a-zA-Z0-9]", "").toLowerCase();
                if (word.length() >= 2) {
                    keywordCount.put(word, keywordCount.getOrDefault(word, 0) + 1);
                }
            }
            
            // 기존 키워드도 포함
            if (news.getKeyword() != null) {
                keywordCount.put(news.getKeyword(), keywordCount.getOrDefault(news.getKeyword(), 0) + 2);
            }
        }
        
        // 빈도수 기준 상위 5개 키워드 반환
        return keywordCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    private NewsCluster findBestCluster(News news, List<NewsCluster> clusters) {
        if (clusters.isEmpty()) return null;
        
        // 키워드 유사도 기반으로 가장 적합한 클러스터 찾기
        NewsCluster bestCluster = clusters.get(0);
        double bestSimilarity = 0.0;
        
        for (NewsCluster cluster : clusters) {
            double similarity = calculateSimilarity(news, cluster);
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestCluster = cluster;
            }
        }
        
        return bestCluster;
    }
    
    private double calculateSimilarity(News news, NewsCluster cluster) {
        // 간단한 키워드 기반 유사도 계산
        String newsText = (news.getTitle() + " " + (news.getSummary() != null ? news.getSummary() : "")).toLowerCase();
        
        int matchCount = 0;
        for (String keyword : cluster.getKeywords()) {
            if (newsText.contains(keyword.toLowerCase())) {
                matchCount++;
            }
        }
        
        return cluster.getKeywords().isEmpty() ? 0.0 : (double) matchCount / cluster.getKeywords().size();
    }
    
    // 클러스터링 결과 클래스들
    public static class ClusteringResult {
        private final List<NewsCluster> clusters;
        
        public ClusteringResult(List<NewsCluster> clusters) {
            this.clusters = clusters;
        }
        
        public List<NewsCluster> getClusters() {
            return clusters;
        }
    }
    
    public static class NewsCluster {
        private String id;
        private String name;
        private List<String> keywords;
        private List<News> news;
        
        public NewsCluster() {
            this.keywords = new ArrayList<>();
            this.news = new ArrayList<>();
        }
        
        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public List<String> getKeywords() { return keywords; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }
        
        public List<News> getNews() { return news; }
        public void setNews(List<News> news) { this.news = news; }
    }
}
