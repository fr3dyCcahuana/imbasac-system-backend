package com.paulfernandosr.possystembackend.security.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.security.domain.Token;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@RequiredArgsConstructor
public class RedisConfig {
    private final ObjectMapper objectMapper;

    @Bean
    public RedisTemplate<String, Token> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Token> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        Jackson2JsonRedisSerializer<Token> valueSerializer = new Jackson2JsonRedisSerializer<>(objectMapper, Token.class);
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
