package com.paulfernandosr.possystembackend.driverlicense.infrastructure.mtc;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import com.paulfernandosr.possystembackend.driverlicense.infrastructure.config.MtcLicenseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Base64;

@Service
public class MtcCaptchaService {

    private static final Logger log = LoggerFactory.getLogger(MtcCaptchaService.class);
    private static final String REDIS_PREFIX = "mtc:session:";

    private final MtcSessionManager sessionManager;
    private final MtcLicenseProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final MtcDebugSupport debugSupport;

    public MtcCaptchaService(MtcSessionManager sessionManager,
                             MtcLicenseProperties properties,
                             StringRedisTemplate redisTemplate,
                             MtcDebugSupport debugSupport) {
        this.sessionManager = sessionManager;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.debugSupport = debugSupport;
    }

    public CaptchaInitResponse init() {
        MtcSession session = sessionManager.createSession(properties.isHeadless());
        String sessionId = session.getSessionId();
        Page page = session.getPage();

        page.setDefaultTimeout(properties.getTimeoutMs());

        try {
            log.info("[mtc][init] inicio. sessionId={}, url={}", sessionId, properties.getSearchUrl());

            page.navigate(
                    properties.getSearchUrl(),
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
            );

            log.info("[mtc][init] navegación completa. sessionId={}, currentUrl={}", sessionId, page.url());

            debugSupport.logSelectors(page, sessionId, "after-navigate");

            page.locator("#pnlBusqNroDoc").first().waitFor();
            page.locator("#pnlCaptcha").first().waitFor();

            Locator preloader = page.locator("#preloader");
            if (preloader.count() > 0) {
                try {
                    preloader.first().waitFor(
                            new Locator.WaitForOptions()
                                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN)
                                    .setTimeout(5000)
                    );
                    log.info("[mtc][init] preloader oculto. sessionId={}", sessionId);
                } catch (Exception ignored) {
                    log.info("[mtc][init] preloader no se ocultó dentro del timeout. sessionId={}", sessionId);
                }
            }

            Locator captchaImg = page.locator("#imgCaptcha");

            debugSupport.logSelectors(page, sessionId, "before-captcha-check");

            if (captchaImg.count() == 0) {
                debugSupport.dumpPageState(page, sessionId, "captcha-not-found");
                destroySession(sessionId);
                throw new IllegalStateException("No se encontró la imagen captcha en la página del MTC.");
            }

            captchaImg.first().waitFor(
                    new Locator.WaitForOptions()
                            .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
            );

            page.waitForFunction(
                    "() => {" +
                            "const img = document.getElementById('imgCaptcha');" +
                            "return !!img && img.complete && img.naturalWidth > 0;" +
                            "}"
            );

            byte[] imageBytes = captchaImg.first().screenshot();
            String base64 = Base64.getEncoder().encodeToString(imageBytes);

            redisTemplate.opsForValue().set(
                    redisKey(sessionId),
                    "ACTIVE",
                    Duration.ofSeconds(properties.getSessionTtlSeconds())
            );

            log.info("[mtc][init] captcha generado correctamente. sessionId={}, base64Length={}",
                    sessionId, base64.length());

            return new CaptchaInitResponse(sessionId, "data:image/png;base64," + base64);
        } catch (Exception e) {
            debugSupport.dumpPageState(page, sessionId, "init-error");
            destroySession(sessionId);
            throw e;
        }
    }

    public boolean isSessionActive(String sessionId) {
        boolean active = Boolean.TRUE.equals(redisTemplate.hasKey(redisKey(sessionId)));
        log.info("[mtc][session] isSessionActive. sessionId={}, active={}", sessionId, active);
        return active;
    }

    public void refreshTtl(String sessionId) {
        redisTemplate.expire(
                redisKey(sessionId),
                Duration.ofSeconds(properties.getSessionTtlSeconds())
        );
        log.info("[mtc][session] refreshTtl. sessionId={}, ttlSeconds={}",
                sessionId, properties.getSessionTtlSeconds());
    }

    public void destroySession(String sessionId) {
        log.info("[mtc][session] destroySession. sessionId={}", sessionId);
        redisTemplate.delete(redisKey(sessionId));
        sessionManager.removeSession(sessionId);
    }

    public MtcSession getSession(String sessionId) {
        return sessionManager.getSession(sessionId);
    }

    private String redisKey(String sessionId) {
        return REDIS_PREFIX + sessionId;
    }

    public record CaptchaInitResponse(String sessionId, String captchaImage) {
    }
}