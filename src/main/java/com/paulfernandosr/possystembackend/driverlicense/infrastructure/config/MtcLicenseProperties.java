package com.paulfernandosr.possystembackend.driverlicense.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.mtc-license")
public class MtcLicenseProperties {

    private String searchUrl = "https://slcp.mtc.gob.pe/";

    /**
     * En producción debe ir true.
     * En local puedes poner false si quieres ver el navegador.
     */
    private boolean headless = true;

    private int timeoutMs = 30000;
    private long sessionTtlSeconds = 120L;

    /**
     * Activa o desactiva el debug funcional:
     * - screenshots
     * - html
     * - body txt
     * - logs de selectores/form/body
     */
    private boolean debugEnabled = false;

    /**
     * Activa o desactiva logs de red Playwright:
     * - request
     * - response
     * - requestFailed
     */
    private boolean networkLogEnabled = false;

    /**
     * Directorio donde guardar evidencia de debug.
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

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public boolean isNetworkLogEnabled() {
        return networkLogEnabled;
    }

    public void setNetworkLogEnabled(boolean networkLogEnabled) {
        this.networkLogEnabled = networkLogEnabled;
    }

    public String getDebugDir() {
        return debugDir;
    }

    public void setDebugDir(String debugDir) {
        this.debugDir = debugDir;
    }
}