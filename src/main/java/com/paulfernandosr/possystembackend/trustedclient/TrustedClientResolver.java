package com.paulfernandosr.possystembackend.trustedclient;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

@Component
public class TrustedClientResolver {
    public static final String PEER_ATTRIBUTE="jakarta.servlet.request.X509Certificate";
    private static final String CACHE=TrustedClientResolver.class.getName();
    private static final String CLIENT_AUTH="1.3.6.1.5.5.7.3.2";
    private final boolean enabled;
    private final Set<String> pins;

    public TrustedClientResolver(@Value("${security.trusted-proxy.enabled:false}") boolean enabled,
            @Value("${security.trusted-proxy.certificate-sha256:}") String configuredPins) {
        this.enabled=enabled;
        Set<String> parsed=new HashSet<>();
        for(String value: configuredPins.split(",")) {
            if(value.isBlank()) continue;
            String pin=value.strip().toLowerCase(Locale.ROOT);
            if(!pin.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid trusted proxy certificate pin");
            parsed.add(pin);
        }
        if(enabled && parsed.isEmpty()) throw new IllegalArgumentException("Trusted proxy requires a certificate pin");
        this.pins=Set.copyOf(parsed);
    }
    public boolean isEnabled() { return enabled; }
    public TrustedClientContext current() {
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) return resolve(attrs.getRequest());
        return new TrustedClientContext(false,null,UUID.randomUUID(),"UNTRUSTED",null);
    }
    public TrustedClientContext resolve(HttpServletRequest request) {
        if(request.getAttribute(CACHE) instanceof TrustedClientContext cached) return cached;
        ServletRequest transport=original(request);
        TrustedClientContext result;
        if(!isProxy(transport)) {
            result=new TrustedClientContext(false,numericIp(transport.getRemoteAddr()),UUID.randomUUID(),"UNTRUSTED",null);
        } else {
            String ip=numericIp(single(request,"X-Imbasac-Client-IP"));
            String verification=single(request,"X-Imbasac-Client-Verify");
            String requestId=single(request,"X-Imbasac-Request-ID");
            UUID id=requestId!=null && requestId.matches("[a-fA-F0-9]{32}")
                    ? UUID.fromString(requestId.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})","$1-$2-$3-$4-$5")) : UUID.randomUUID();
            X509Certificate cert=null;
            if("SUCCESS".equals(verification)) {
                try {
                    String pem=single(request,"X-Imbasac-Client-Cert");
                    if(pem==null || pem.length()>24000) throw new IllegalArgumentException();
                    String decoded=URLDecoder.decode(pem,StandardCharsets.UTF_8);
                    if(!decoded.startsWith("-----BEGIN CERTIFICATE-----") || decoded.indexOf("-----BEGIN CERTIFICATE-----",1)>=0) throw new IllegalArgumentException();
                    cert=(X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                            new ByteArrayInputStream(decoded.getBytes(StandardCharsets.US_ASCII)));
                } catch(Exception ignored) { verification="INVALID"; }
            } else if(!"NONE".equals(verification)) verification="INVALID";
            result=new TrustedClientContext(true,ip,id,verification,cert);
        }
        request.setAttribute(CACHE,result);
        return result;
    }
    private boolean isProxy(ServletRequest transport) {
        if(!enabled || !transport.isSecure()) return false;
        if(!(transport.getAttribute(PEER_ATTRIBUTE) instanceof X509Certificate[] chain) || chain.length==0) return false;
        try {
            X509Certificate leaf=chain[0]; leaf.checkValidity();
            return pins.contains(fingerprint(leaf)) && leaf.getBasicConstraints()<0
                    && leaf.getExtendedKeyUsage()!=null && leaf.getExtendedKeyUsage().contains(CLIENT_AUTH)
                    && leaf.getSubjectAlternativeNames()!=null && leaf.getSubjectAlternativeNames().stream()
                    .anyMatch(san -> Integer.valueOf(6).equals(san.get(0)) && "urn:imbasac:service:nginx".equals(san.get(1)));
        } catch(Exception ignored) { return false; }
    }
    public static String fingerprint(X509Certificate certificate) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())); }
        catch(Exception e) { throw new IllegalArgumentException("Invalid certificate"); }
    }
    public static ServletRequest original(ServletRequest request) {
        for(int depth=0; depth<32 && request instanceof ServletRequestWrapper wrapper; depth++) request=wrapper.getRequest();
        return request;
    }
    private static String single(HttpServletRequest request,String name) {
        List<String> values=Collections.list(request.getHeaders(name));
        return values.size()==1 ? values.getFirst() : null;
    }
    public static String numericIp(String value) {
        if(value==null || value.length()>45 || !value.matches("[0-9a-fA-F:.]+")) return null;
        try {
            if(!value.contains(":")) {
                String[] octets=value.split("\\.",-1);
                if(octets.length!=4) return null;
                for(String octet:octets) if(!octet.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(octet)>255) return null;
            }
            return InetAddress.getByName(value).getHostAddress(); // Only numeric literals; never DNS.
        } catch(Exception ignored) { return null; }
    }
}
