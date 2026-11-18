package com.paulfernandosr.possystembackend.accessaudit.domain;

import java.util.Set;

public final class AuditCategory {
    private AuditCategory() {}
    public static final Set<String> VALUES=Set.of("ACCESS","CREDENTIALS","CERTIFICATE","DEVICE","ACCOUNT","SESSION","PERMISSIONS","SERVICE","AUDIT","OTHER");
    public static String label(String category) {
        if(category==null) return "Sin clasificación específica";
        return switch(category) {
            case "ACCESS"->"Acceso y validación";
            case "CREDENTIALS"->"Usuario y credenciales";
            case "CERTIFICATE"->"Certificado";
            case "DEVICE"->"Autorización de computadora";
            case "ACCOUNT"->"Bloqueo de cuenta";
            case "SESSION"->"Sesión";
            case "PERMISSIONS"->"Permisos";
            case "SERVICE"->"Disponibilidad del servicio";
            case "AUDIT"->"Consulta de auditoría";
            default->"Sin clasificación específica";
        };
    }
    // Fixed SQL shared by list, totals, detail and export. Includes legacy reason codes.
    public static final String SQL="""
        CASE
          WHEN reason IN ('AUTHENTICATION_UNAVAILABLE','DEVICE_POLICY_UNAVAILABLE','ACCESS_CONTROL_UNAVAILABLE',
            'DEVICE_CERTIFICATE_CHANNEL_NOT_READY','SESSION_VALIDATION_UNAVAILABLE','DEVICE_POLICY_NOT_CONFIGURED') THEN 'SERVICE'
          WHEN reason IN ('ACCESS_ACCOUNT_BLOCKED','ACCOUNT_DISABLED') THEN 'ACCOUNT'
          WHEN reason IN ('CREDENTIALS_OR_ACCOUNT_REJECTED','CREDENTIALS_REJECTED','LOGIN_REQUEST_NOT_ACCEPTED') THEN 'CREDENTIALS'
          WHEN reason IN ('CERTIFICATE_PC_NOT_AUTHORIZED_FOR_USER','ACCESS_DEVICE_NOT_AUTHORIZED','DEVICE_NOT_AUTHORIZED') THEN 'DEVICE'
          WHEN reason LIKE 'CERTIFICATE_%' OR reason LIKE 'DEVICE_CERTIFICATE_%' THEN 'CERTIFICATE'
          WHEN event_type LIKE 'SESSION_%' OR event_type='LOGOUT' OR reason LIKE 'SESSION_%' THEN 'SESSION'
          WHEN event_type='ACCESS_DENIED' THEN 'PERMISSIONS'
          WHEN event_type LIKE 'AUDIT_%' THEN 'AUDIT'
          WHEN event_type IN ('LOGIN_GRANTED','BROWSER_COMPLETED','BROWSER_REJECTED') THEN 'ACCESS'
          ELSE 'OTHER'
        END
        """;
}
