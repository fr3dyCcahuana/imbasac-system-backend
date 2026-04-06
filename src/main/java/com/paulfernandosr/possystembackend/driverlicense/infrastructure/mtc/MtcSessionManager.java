package com.paulfernandosr.possystembackend.driverlicense.infrastructure.mtc;

import com.microsoft.playwright.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MtcSessionManager {

    private static final Logger log = LoggerFactory.getLogger(MtcSessionManager.class);

    private final Map<String, MtcSession> sessions = new ConcurrentHashMap<>();
    private final Playwright playwright;

    public MtcSessionManager() {
        this.playwright = Playwright.create();
        log.info("[mtc][session-manager] Playwright inicializado");
    }

    public MtcSession createSession(boolean headless) {
        Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(headless)
        );

        BrowserContext context = browser.newContext();
        Page page = context.newPage();

        String sessionId = UUID.randomUUID().toString();

        MtcSession session = new MtcSession(
                sessionId,
                browser,
                context,
                page,
                Instant.now()
        );

        sessions.put(sessionId, session);

        log.info("[mtc][session-manager] sesión creada. sessionId={}, headless={}", sessionId, headless);
        return session;
    }

    public MtcSession getSession(String sessionId) {
        MtcSession session = sessions.get(sessionId);
        log.info("[mtc][session-manager] getSession. sessionId={}, exists={}", sessionId, session != null);
        return session;
    }

    public void removeSession(String sessionId) {
        MtcSession session = sessions.remove(sessionId);
        if (session == null) {
            log.info("[mtc][session-manager] removeSession. sessionId={} no encontrada", sessionId);
            return;
        }

        log.info("[mtc][session-manager] cerrando sesión. sessionId={}", sessionId);

        try { session.getPage().close(); } catch (Exception ignored) {}
        try { session.getContext().close(); } catch (Exception ignored) {}
        try { session.getBrowser().close(); } catch (Exception ignored) {}

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