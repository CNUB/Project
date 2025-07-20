// config/CacheConfig.java
package com.financescope.financescope.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class CacheConfig {
    
    @Value("${app.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${app.cache.default-expiry:3600}")
    private long defaultExpiry;
    
    @Value("${app.cache.cleanup-interval:300}")
    private long cleanupInterval;
    
    /**
     * 캐시 정리를 위한 스케줄러
     */
    @Bean
    @Primary
    public ScheduledExecutorService cacheScheduler() {
        return Executors.newScheduledThreadPool(1);
    }
    
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }
    
    public long getDefaultExpiry() {
        return defaultExpiry;
    }
    
    public long getCleanupInterval() {
        return cleanupInterval;
    }
}