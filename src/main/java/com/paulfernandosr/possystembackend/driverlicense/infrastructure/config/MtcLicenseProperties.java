package com.paulfernandosr.possystembackend.driverlicense.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.mtc-license")
public class MtcLicenseProperties {

    private String searchUrl = "https://slcp.mtc.gob.pe/";
    private boolean headless = true;
    private int timeoutMs = 90000;
    private long sessionTtlSeconds = 120L;

    private boolean proxyEnabled = false;
    private String proxyServer;
    private String proxyBypass;
    private String proxyUsername;
    private String proxyPassword;

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

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public void setProxyEnabled(boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
    }

    public String getProxyServer() {
        return proxyServer;
    }

    public void setProxyServer(String proxyServer) {
        this.proxyServer = proxyServer;
    }

    public String getProxyBypass() {
        return proxyBypass;
    }

    public void setProxyBypass(String proxyBypass) {
        this.proxyBypass = proxyBypass;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public String getDebugDir() {
        return debugDir;
    }

    public void setDebugDir(String debugDir) {
        this.debugDir = debugDir;
    }
}