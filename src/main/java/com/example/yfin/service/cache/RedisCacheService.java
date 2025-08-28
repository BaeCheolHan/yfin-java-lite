package com.example.yfin.service.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class RedisCacheService {
    private final org.springframework.data.redis.core.ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisCacheService(org.springframework.data.redis.core.ReactiveStringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public <T> Mono<T> get(String key, TypeReference<T> type) {
        return redis.opsForValue().get(key)
                .flatMap(json -> Mono.fromCallable(() -> mapper.readValue(json, type)))
                .onErrorResume(e -> Mono.empty());
    }

    public Mono<Boolean> set(String key, Object value, Duration ttl) {
        return Mono.fromCallable(() -> mapper.writeValueAsString(value))
                .flatMap(js -> redis.opsForValue().set(key, js, ttl))
                .onErrorReturn(false);
    }
}


