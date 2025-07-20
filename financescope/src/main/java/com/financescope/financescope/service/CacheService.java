// service/CacheService.java
package com.financescope.financescope.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
@Slf4j
public class CacheService {
    
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> expiryMap = new ConcurrentHashMap<>();
    
    public CacheService() {
        log.info("Memory-based CacheService initialized");
    }
    
    /**
     * 캐시에 값 저장 (만료 시간 없음)
     */
    public void put(String key, Object value) {
        cache.put(key, value);
        log.debug("Cache put: key={}", key);
    }
    
    /**
     * 캐시에 값 저장 (만료 시간 포함) - 오버로드된 메서드
     */
    public void put(String key, Object value, long expiryInSeconds) {
    cache.put(key, value);
    long expiryTime = System.currentTimeMillis() + (expiryInSeconds * 1000);
    expiryMap.put(key, expiryTime);
    log.debug("Cache put with expiry: key={}, expiryInSeconds={}", key, expiryInSeconds);
}
    
    /**
     * 캐시에서 값 조회
     */
    public Object get(String key) {
        // 만료 확인
        if (isExpired(key)) {
            delete(key);
            return null;
        }
        
        Object value = cache.get(key);
        log.debug("Cache get: key={}, found={}", key, value != null);
        return value;
    }
    
    /**
     * 제네릭 타입으로 캐시 값 조회
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * 캐시에서 값 삭제
     */
    public void delete(String key) {
        cache.remove(key);
        expiryMap.remove(key);
        log.debug("Cache delete: key={}", key);
    }
    
    /**
     * 모든 캐시 삭제
     */
    public void clear() {
        cache.clear();
        expiryMap.clear();
        log.info("Cache cleared");
    }
    
    /**
     * 키 존재 여부 확인
     */
    public boolean hasKey(String key) {
        if (isExpired(key)) {
            delete(key);
            return false;
        }
        return cache.containsKey(key);
    }
    
    /**
     * 만료 시간과 함께 캐시에 값 저장 (기존 메서드 유지)
     */
    public void putWithExpiry(String key, Object value, long expiryInSeconds) {
        put(key, value, expiryInSeconds); // 오버로드된 put 메서드 사용
    }
    
    /**
     * 캐시 크기 반환
     */
    public int size() {
        return cache.size();
    }
    
    /**
     * 캐시 통계 정보 반환
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("size", cache.size());
        stats.put("expiryEntries", expiryMap.size());
        return stats;
    }
    
    /**
     * 키가 만료되었는지 확인
     */
    private boolean isExpired(String key) {
        Long expiryTime = expiryMap.get(key);
        if (expiryTime == null) {
            return false;
        }
        return System.currentTimeMillis() > expiryTime;
    }
}