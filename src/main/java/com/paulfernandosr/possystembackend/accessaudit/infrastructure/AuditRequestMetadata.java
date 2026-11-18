package com.paulfernandosr.possystembackend.accessaudit.infrastructure;

import com.paulfernandosr.possystembackend.accessaudit.domain.AccessAuditModels.Metadata;
import com.paulfernandosr.possystembackend.accessaudit.domain.AccessAuditModels.AuthenticationContext;
import com.paulfernandosr.possystembackend.accessaudit.domain.AuditFailure;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import java.util.UUID;
import com.paulfernandosr.possystembackend.accessaudit.domain.port.AuditMetadataProvider;

@Component
public class AuditRequestMetadata implements AuditMetadataProvider {
    private final com.paulfernandosr.possystembackend.trustedclient.TrustedClientResolver clients;
    private final com.paulfernandosr.possystembackend.trustedclient.GeoLocationService geo;
    public AuditRequestMetadata() { this(null,null); }
    @org.springframework.beans.factory.annotation.Autowired
    public AuditRequestMetadata(com.paulfernandosr.possystembackend.trustedclient.TrustedClientResolver clients,
            com.paulfernandosr.possystembackend.trustedclient.GeoLocationService geo) { this.clients=clients;this.geo=geo; }
    public Metadata current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return from(attributes.getRequest());
        }
        return new Metadata(null,null,UUID.randomUUID());
    }
    public Metadata from(HttpServletRequest request) {
        String key = AuditRequestMetadata.class.getName();
        if (request.getAttribute(key) instanceof Metadata metadata) return withFailure(metadata,request);
        if(clients!=null) {
            var context=clients.resolve(request);
            var cert=context.proxyAuthenticated()?context.certificate():null;
            var authentication=new AuthenticationContext(context.proxyAuthenticated()?context.certificateState():"UNTRUSTED",
                    cert==null?null:com.paulfernandosr.possystembackend.trustedclient.TrustedClientResolver.fingerprint(cert),
                    cert==null?null:cert.getSerialNumber().toString(16),null,null,null);
            Metadata metadata=new Metadata(context.clientIp(),clean(request.getHeader("User-Agent"),300),context.requestId(),
                    context.proxyAuthenticated() && geo!=null && geo.isEnabled()?geo.locate(context.clientIp()):null,authentication);
            request.setAttribute(key,metadata);return withFailure(metadata,request);
        }
        // Compatibility for old tests/adapters: never trust forwarded-header wrappers.
        ServletRequest transport=request;
        for (int depth=0;depth<32 && transport instanceof ServletRequestWrapper wrapper;depth++) transport=wrapper.getRequest();
        Metadata metadata = new Metadata(clean(transport.getRemoteAddr(),80),
                clean(request.getHeader("User-Agent"),300),UUID.randomUUID());
        request.setAttribute(key,metadata);
        return withFailure(metadata,request);
    }
    private static Metadata withFailure(Metadata metadata,HttpServletRequest request) {
        Object reason=request.getAttribute(AuditFailure.ATTRIBUTE);
        if(!(reason instanceof String code) || !code.matches("[A-Z_]{1,80}")) return metadata;
        var context=metadata.authentication()==null
                ? new AuthenticationContext("UNKNOWN",null,null,null,null,code)
                : metadata.authentication().withFailure(code);
        return new Metadata(metadata.peerAddress(),metadata.userAgent(),metadata.correlationId(),metadata.location(),context);
    }
    public static String clean(String value, int max) {
        if (value == null) return null;
        String cleaned=value.replaceAll("[\\p{Cntrl}]", " ").strip();
        return cleaned.substring(0,Math.min(cleaned.length(),max));
    }
}
