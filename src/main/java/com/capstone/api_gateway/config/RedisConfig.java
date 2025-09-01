package com.capstone.api_gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Slf4j
public class RedisConfig {

    @Bean(name = "customReactiveRedisTemplate")
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(ReactiveRedisConnectionFactory connectionFactory) {
        
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        
        RedisSerializationContext<String, String> context = RedisSerializationContext
                .<String, String>newSerializationContext()
                .key(stringSerializer)
                .value(stringSerializer)
                .hashKey(stringSerializer)
                .hashValue(stringSerializer)
                .build();
        
        ReactiveRedisTemplate<String, String> template = new ReactiveRedisTemplate<>(connectionFactory, context);
        
        log.info("Reactive Redis template configured successfully for API Gateway");
        return template;
    }
}