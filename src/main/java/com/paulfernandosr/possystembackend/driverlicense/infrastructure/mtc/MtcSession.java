package com.paulfernandosr.possystembackend.driverlicense.infrastructure.mtc;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

import java.time.Instant;

public class MtcSession {

    private final String sessionId;
    private final Browser browser;
    private final BrowserContext context;
    private final Page page;
    private final Instant createdAt;

    public MtcSession(String sessionId,
                      Browser browser,
                      BrowserContext context,
                      Page page,
                      Instant createdAt) {
        this.sessionId = sessionId;
        this.browser = browser;
        this.context = context;
        this.page = page;
        this.createdAt = createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Browser getBrowser() {
        return browser;
    }

    public BrowserContext getContext() {
        return context;
    }

    public Page getPage() {
        return page;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}