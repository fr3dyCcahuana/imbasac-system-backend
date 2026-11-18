package com.paulfernandosr.possystembackend.accessaudit.domain;

public class AuditException extends RuntimeException {
    private final int status;
    private final String code;
    public AuditException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static AuditException unavailable() {
        return new AuditException(503,"ACCESS_AUDIT_UNAVAILABLE","El registro de accesos no está disponible.");
    }
    public static AuditException disabled() {
        return new AuditException(503,"ACCESS_AUDIT_DISABLED","El registro de accesos aún no está habilitado.");
    }
    public static AuditException badRequest(String message) {
        return new AuditException(400,"ACCESS_AUDIT_INVALID_QUERY",message);
    }
}
