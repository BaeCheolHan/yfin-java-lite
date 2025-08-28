package com.example.yfin.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cm = new CaffeineCacheManager("quote","quotes","history","dividends","options","financials","resolve","earnings","profile","search");
        cm.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(5)));
        // Reactive @Cacheable(Mono/Flux) 사용 시 AsyncCache 필요
        cm.setAsyncCacheMode(true);
        return cm;
    }
}