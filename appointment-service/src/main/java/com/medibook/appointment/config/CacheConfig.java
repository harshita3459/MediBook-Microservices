package com.medibook.appointment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

@Configuration
@Slf4j
public class CacheConfig implements CachingConfigurer {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .entryTtl(Duration.ofMinutes(5));

        Map<String, RedisCacheConfiguration> configs = Map.of(
                "appointments.byId", defaultConfig.entryTtl(Duration.ofMinutes(10)),
                "appointments.patient", defaultConfig.entryTtl(Duration.ofMinutes(3)),
                "appointments.provider", defaultConfig.entryTtl(Duration.ofMinutes(3)),
                "appointments.providerDate", defaultConfig.entryTtl(Duration.ofMinutes(2)),
                "appointments.upcoming", defaultConfig.entryTtl(Duration.ofMinutes(2)),
                "appointments.all", defaultConfig.entryTtl(Duration.ofMinutes(2)),
                "appointments.count", defaultConfig.entryTtl(Duration.ofMinutes(3))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configs)
                .transactionAware()
                .build();
    }

    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.warn("Redis cache GET failed for cache={} key={}. Falling back to source data.",
                        cache != null ? cache.getName() : "unknown", key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache, Object key, Object value) {
                log.warn("Redis cache PUT failed for cache={} key={}. Continuing without cache.",
                        cache != null ? cache.getName() : "unknown", key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.warn("Redis cache EVICT failed for cache={} key={}. Continuing without cache.",
                        cache != null ? cache.getName() : "unknown", key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
                log.warn("Redis cache CLEAR failed for cache={}. Continuing without cache.",
                        cache != null ? cache.getName() : "unknown", exception);
            }
        };
    }
}
