package com.paulfernandosr.possystembackend.accessaudit.infrastructure;

import com.paulfernandosr.possystembackend.accessaudit.application.AccessAuditService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@RequiredArgsConstructor
public class AccessAuditCaptureFilter extends OncePerRequestFilter {
    private final AccessAuditService audit;
    private final AuditRequestMetadata metadata;
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if (!audit.isEnabled()) { chain.doFilter(request,response); return; }
        try { chain.doFilter(request,response); }
        finally {
            var meta=metadata.from(request);
            if ("POST".equals(request.getMethod()) && "/auth/login".equals(request.getServletPath())
                    && response.getStatus()>=400 && !Boolean.TRUE.equals(request.getAttribute("imbasac.loginHandled"))) {
                audit.denied("LOGIN_REJECTED",null,meta);
            }
            if (!request.getServletPath().startsWith("/auth/")) {
                String user=(String)request.getAttribute("imbasac.authenticatedUsername");
                if (response.getStatus()==403) {
                    String failure=(String)request.getAttribute(com.paulfernandosr.possystembackend.accessaudit.domain.AuditFailure.ATTRIBUTE);
                    audit.denied("ACCESS_DENIED",user,(String)request.getAttribute("imbasac.accessSessionKey"),failure,meta);
                }
                else if (response.getStatus()==401 && request.getHeader("Authorization")!=null) {
                    String verified=(String)request.getAttribute("imbasac.verifiedTokenUsername");
                    String key=(String)request.getAttribute("imbasac.verifiedTokenSessionKey");
                    String failure=(String)request.getAttribute(com.paulfernandosr.possystembackend.accessaudit.domain.AuditFailure.ATTRIBUTE);
                    audit.denied("SESSION_REJECTED",verified,key,failure,meta);
                }
                if (user!=null && "GET".equals(request.getMethod()) && response.getStatus()>=200 && response.getStatus()<300
                        && request.getServletPath().startsWith("/security/access-audit/")
                        && !request.getServletPath().endsWith("/status") && !request.getServletPath().endsWith("/export")) {
                    audit.viewed(user,meta);
                }
            }
        }
    }
}
