package com.paulfernandosr.possystembackend.guideremission.infrastructure.adapter.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTokenResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.exception.GuideRemissionIntegrationException;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionTokenCacheRepository;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.GuideRemissionRedisConstants;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RedisGuideRemissionTokenCacheRepository implements GuideRemissionTokenCacheRepository {
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final GuideRemissionProperties properties;

    @Override
    public Optional<GuideRemissionTokenResponse> findByCompanyRuc(String companyRuc) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(redisKey(companyRuc)))
                .map(this::deserialize);
    }

    @Override
    public void save(String companyRuc, GuideRemissionTokenResponse tokenResponse) {
        String value = serialize(tokenResponse);
        stringRedisTemplate.opsForValue().set(redisKey(companyRuc), value, resolveTtl(tokenResponse));
    }

    @Override
    public void delete(String companyRuc) {
        stringRedisTemplate.delete(redisKey(companyRuc));
    }

    private Duration resolveTtl(GuideRemissionTokenResponse tokenResponse) {
        long fallback = properties.getCachedTokenTtlSecondsFallback();
        long skew = properties.getTokenTtlSkewSeconds();
        long expiresIn = tokenResponse.getExpiresIn() == null ? fallback : tokenResponse.getExpiresIn();
        long effectiveTtl = Math.max(1, expiresIn - skew);
        return Duration.ofSeconds(effectiveTtl);
    }

    private String redisKey(String companyRuc) {
        String prefix = properties.getRedisKeyPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = GuideRemissionRedisConstants.DEFAULT_TOKEN_PREFIX;
        }
        return prefix + companyRuc;
    }

    private String serialize(GuideRemissionTokenResponse tokenResponse) {
        try {
            return objectMapper.writeValueAsString(tokenResponse);
        } catch (JsonProcessingException ex) {
            throw new GuideRemissionIntegrationException("No se pudo serializar el token de guía para Redis.", ex);
        }
    }

    private GuideRemissionTokenResponse deserialize(String value) {
        try {
            return objectMapper.readValue(value, GuideRemissionTokenResponse.class);
        } catch (JsonProcessingException ex) {
            throw new GuideRemissionIntegrationException("No se pudo deserializar el token de guía desde Redis.", ex);
        }
    }
}
