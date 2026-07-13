package com.example.frontapi.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * AS-03: L1 Caffeine + L2 Redis 계층 캐시 설정
 * - L1 Caffeine : TTL 5분, maximumSize=1000 (로컬 인메모리)
 * - L2 Redis    : AC 권한 1시간, Copilot 권한 30분 (분산 캐시)
 */
@Configuration
public class CacheConfig {

    // ── L1: Caffeine ──────────────────────────────────────────────────────────

    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
        );
        return manager;
    }

    // ── L2: Redis ─────────────────────────────────────────────────────────────

    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            );

        RedisCacheConfiguration copilotConfig = defaultConfig
            .entryTtl(Duration.ofMinutes(30));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration("copilot-permissions", copilotConfig)
            .build();
    }
}
