package com.paulfernandosr.possystembackend.accessaudit.domain;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Diagnostic codes only. Never copy an exception message, request body or credential. */
public final class AuditFailure {
    public static final String ATTRIBUTE="imbasac.auditFailureReason";
    private AuditFailure() {}
    public static void note(String code) {
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)
            attrs.getRequest().setAttribute(ATTRIBUTE,code);
    }
}
