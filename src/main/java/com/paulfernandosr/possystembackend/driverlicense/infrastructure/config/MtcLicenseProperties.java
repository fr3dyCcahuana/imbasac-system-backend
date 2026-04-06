package com.paulfernandosr.possystembackend.driverlicense.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.mtc-license")
public class MtcLicenseProperties {

    private String searchUrl = "https://slcp.mtc.gob.pe/";
    private boolean headless = false;
    private int timeoutMs = 30000;
    private long sessionTtlSeconds = 120L;

    /**
     * Directorio donde guardar evidencia de debug:
     * screenshots/html/txt
     */
    private String debugDir = "logs/mtc-debug";

    public String getSearchUrl() {
        return searchUrl;
    }

    public void setSearchUrl(String searchUrl) {
        this.searchUrl = searchUrl;
    }

    public boolean isHeadless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public long getSessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    public void setSessionTtlSeconds(long sessionTtlSeconds) {
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    public String getDebugDir() {
        return debugDir;
    }

    public void setDebugDir(String debugDir) {
        this.debugDir = debugDir;
    }
}