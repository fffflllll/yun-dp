package com.hmdp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * JVM  Local Caffeine cache.
     *
     *  Use small TTL because this cache is only a fast path (best-effort) for hot keys / short-term dedupe.
     * Real correctness is still guaranteed by Redis Lua + DB constraints/transaction.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "seckill:order:dq", // dedupe cache
                "seckill:request:dq" // request dedupe cache
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(200_000)
                .expireAfterWrite(Duration.ofSeconds(30))
        );
        return cacheManager;
    }
}
