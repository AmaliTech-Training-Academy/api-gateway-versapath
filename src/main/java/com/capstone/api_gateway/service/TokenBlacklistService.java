package com.capstone.api_gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class TokenBlacklistService {

    private final @Qualifier("customReactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate;
    private static final String BLACKLIST_PREFIX = "blacklisted_token:";

    public TokenBlacklistService(@Qualifier("customReactiveRedisTemplate")
                                 ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Check if token is blacklisted (reactive)
     * @param jti JWT Token ID
     * @return Mono<Boolean> - true if token is blacklisted
     */
    public Mono<Boolean> isTokenBlacklisted(String jti) {
        if (jti == null || jti.trim().isEmpty()) {
            log.warn("JTI is null or empty");
            return Mono.just(false);
        }
        
        String key = BLACKLIST_PREFIX + jti;
        
        return redisTemplate.hasKey(key)
                .doOnNext(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.debug("Token {} is blacklisted", jti);
                    }
                })
                .doOnError(error -> log.error("Error checking blacklist for token {}: {}", jti, error.getMessage()))
                .onErrorReturn(false); // If Redis is down, allow the request (fail open)
    }
}