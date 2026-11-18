package com.paulfernandosr.possystembackend.accessaudit.domain;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Set;

public record AuditQuery(Instant from, Instant until, String username, String role, String type,
                         String outcome, int page, int size, String category) {
    public AuditQuery(Instant from,Instant until,String username,String role,String type,String outcome,int page,int size) {
        this(from,until,username,role,type,outcome,page,size,null);
    }
    public static final ZoneId LIMA = ZoneId.of("America/Lima");
    public static final Set<String> TYPES = Set.of("LOGIN_GRANTED", "LOGIN_REJECTED", "LOGOUT",
            "SESSION_REPLACED", "SESSION_INVALIDATED", "SESSION_REVOKED", "SESSION_EXPIRED",
            "ACCESS_DENIED", "SESSION_REJECTED", "BROWSER_COMPLETED", "BROWSER_REJECTED",
            "AUDIT_VIEWED", "AUDIT_EXPORTED");

    public static AuditQuery of(LocalDate from, LocalDate to, String username, String role,
                                String type, String outcome, int page, int size) {
        return of(from,to,username,role,type,outcome,page,size,null);
    }
    public static AuditQuery of(LocalDate from, LocalDate to, String username, String role,
                                String type, String outcome, int page, int size, String category) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from,to) >= 31
                || page < 0 || page > 1000 || size < 1 || size > 100) {
            throw AuditException.badRequest("Selecciona entre uno y 31 días y una página válida.");
        }
        username = clean(username,160); role = clean(role,64); type = clean(type,48); outcome = clean(outcome,12);
        category=clean(category,32);
        if(category!=null && !AuditCategory.VALUES.contains(category)) throw AuditException.badRequest("La categoría del filtro no es válida.");
        if ((type != null && !TYPES.contains(type)) || (outcome != null && !Set.of("GRANTED","REJECTED","INFO").contains(outcome))) {
            throw AuditException.badRequest("El tipo o resultado del filtro no es válido.");
        }
        return new AuditQuery(from.atStartOfDay(LIMA).toInstant(), to.plusDays(1).atStartOfDay(LIMA).toInstant(),
                username,role,type,outcome,page,size,category);
    }
    private static String clean(String text, int max) {
        if (text == null || text.isBlank()) return null;
        if (text.length() > max || text.chars().anyMatch(Character::isISOControl)) {
            throw AuditException.badRequest("El filtro contiene un valor no válido.");
        }
        return text.trim();
    }
}
