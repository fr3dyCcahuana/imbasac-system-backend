package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionTokenResponse;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionProvider;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionTokenCacheRepository;
import com.paulfernandosr.possystembackend.guideremission.infrastructure.config.GuideRemissionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuideRemissionTokenManager {
    private final GuideRemissionProvider guideRemissionProvider;
    private final GuideRemissionTokenCacheRepository tokenCacheRepository;
    private final GuideRemissionProperties properties;
    private final GuideRemissionPhpResponseEvaluator responseEvaluator;

    public GuideRemissionTokenResolution getOrCreateToken() {
        return tokenCacheRepository.findByCompanyRuc(companyRuc())
                .filter(token -> token.getAccessToken() != null && !token.getAccessToken().isBlank())
                .map(token -> {
                    log.info("[guide-remission][token-cache] Token reutilizado desde Redis. modo={}, ruc={}",
                            properties.resolvedModoLabel(), companyRuc());
                    return new GuideRemissionTokenResolution(token, "CACHE");
                })
                .orElseGet(this::forceRefreshToken);
    }

    public GuideRemissionTokenResolution forceRefreshToken() {
        log.info("[guide-remission][token-cache] Solicitando nuevo token. modo={}, ruc={}",
                properties.resolvedModoLabel(), companyRuc());
        GuideRemissionTokenResponse response = guideRemissionProvider.requestToken();
        responseEvaluator.assertValidTokenResponse(response);
        tokenCacheRepository.save(companyRuc(), response);
        log.info("[guide-remission][token-cache] Token nuevo guardado en Redis. modo={}, ruc={}, expiresIn={}",
                properties.resolvedModoLabel(), companyRuc(), response.getExpiresIn());
        return new GuideRemissionTokenResolution(response, "NEW");
    }

    public void invalidateCachedToken() {
        log.warn("[guide-remission][token-cache] Invalidando token cacheado. modo={}, ruc={}",
                properties.resolvedModoLabel(), companyRuc());
        tokenCacheRepository.delete(companyRuc());
    }

    private String companyRuc() {
        return properties.getCompany().getRuc();
    }
}
