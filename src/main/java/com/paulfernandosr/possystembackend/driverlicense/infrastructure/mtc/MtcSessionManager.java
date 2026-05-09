package com.paulfernandosr.possystembackend.driverlicense.infrastructure.mtc;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Proxy;
import com.paulfernandosr.possystembackend.driverlicense.infrastructure.config.MtcLicenseProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MtcSessionManager {

    private static final Logger log = LoggerFactory.getLogger(MtcSessionManager.class);

    private final Map<String, MtcSession> sessions = new ConcurrentHashMap<>();
    private final Playwright playwright;
    private final MtcLicenseProperties properties;

    public MtcSessionManager(MtcLicenseProperties properties) {
        this.properties = properties;
        this.playwright = Playwright.create();

        log.info(
                "[mtc][session-manager] Playwright inicializado. headless={}, timeoutMs={}, proxyEnabled={}, proxyServer={}",
                properties.isHeadless(),
                properties.getTimeoutMs(),
                properties.isProxyEnabled(),
                properties.getProxyServer()
        );
    }

    public MtcSession createSession(boolean headless) {
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setTimeout((double) properties.getTimeoutMs())
                .setArgs(List.of(
                        "--no-sandbox",
                        "--disable-setuid-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-gpu"
                ));

        if (properties.isProxyEnabled()
                && properties.getProxyServer() != null
                && !properties.getProxyServer().isBlank()) {

            Proxy proxy = new Proxy(properties.getProxyServer());

            if (properties.getProxyBypass() != null && !properties.getProxyBypass().isBlank()) {
                proxy.setBypass(properties.getProxyBypass());
            }

            if (properties.getProxyUsername() != null && !properties.getProxyUsername().isBlank()) {
                proxy.setUsername(properties.getProxyUsername());
            }

            if (properties.getProxyPassword() != null && !properties.getProxyPassword().isBlank()) {
                proxy.setPassword(properties.getProxyPassword());
            }

            launchOptions.setProxy(proxy);

            log.info(
                    "[mtc][session-manager] proxy Playwright habilitado. server={}, bypass={}",
                    properties.getProxyServer(),
                    properties.getProxyBypass()
            );
        }

        Browser browser = playwright.chromium().launch(launchOptions);

        BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                        .setLocale("es-PE")
                        .setTimezoneId("America/Lima")
                        .setUserAgent(
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                        "Chrome/124.0.0.0 Safari/537.36"
                        )
        );

        Page page = context.newPage();

        page.setDefaultTimeout(properties.getTimeoutMs());
        page.setDefaultNavigationTimeout(properties.getTimeoutMs());

        String sessionId = UUID.randomUUID().toString();

        MtcSession session = new MtcSession(
                sessionId,
                browser,
                context,
                page,
                Instant.now()
        );

        sessions.put(sessionId, session);

        log.info(
                "[mtc][session-manager] sesión creada. sessionId={}, headless={}, proxyEnabled={}",
                sessionId,
                headless,
                properties.isProxyEnabled()
        );

        return session;
    }

    public MtcSession getSession(String sessionId) {
        MtcSession session = sessions.get(sessionId);

        log.info(
                "[mtc][session-manager] getSession. sessionId={}, exists={}",
                sessionId,
                session != null
        );

        return session;
    }

    public void removeSession(String sessionId) {
        MtcSession session = sessions.remove(sessionId);

        if (session == null) {
            log.info("[mtc][session-manager] removeSession. sessionId={} no encontrada", sessionId);
            return;
        }

        log.info("[mtc][session-manager] cerrando sesión. sessionId={}", sessionId);

        try {
            session.getPage().close();
        } catch (Exception ignored) {
        }

        try {
            session.getContext().close();
        } catch (Exception ignored) {
        }

        try {
            session.getBrowser().close();
        } catch (Exception ignored) {
        }

        log.info("[mtc][session-manager] sesión cerrada. sessionId={}", sessionId);
    }

    @PreDestroy
    public void shutdown() {
        log.info("[mtc][session-manager] apagando Playwright y sesiones activas");

        for (String sessionId : sessions.keySet()) {
            removeSession(sessionId);
        }

        try {
            playwright.close();
        } catch (Exception ignored) {
        }

        log.info("[mtc][session-manager] apagado completo");
    }
}