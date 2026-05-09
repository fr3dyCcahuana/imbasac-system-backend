package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.URI;

@Component
@ConfigurationProperties(prefix = "integration.customer-lookup")
public class CustomerLookupProperties {

    private boolean proxyEnabled = false;
    private String proxyServer = "socks5://127.0.0.1:1080";
    private int connectTimeoutMs = 30000;
    private int readTimeoutMs = 60000;

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

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public java.net.Proxy toJavaNetProxy() {
        if (proxyServer == null || proxyServer.isBlank()) {
            throw new IllegalStateException("Proxy no configurado para customer-lookup.");
        }

        URI uri = URI.create(proxyServer.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost();
        int port = uri.getPort();

        if (host == null || host.isBlank() || port <= 0) {
            throw new IllegalStateException("Proxy inválido para customer-lookup: " + proxyServer);
        }

        java.net.Proxy.Type type = scheme.startsWith("socks")
                ? java.net.Proxy.Type.SOCKS
                : java.net.Proxy.Type.HTTP;

        return new java.net.Proxy(type, new InetSocketAddress(host, port));
    }
}
