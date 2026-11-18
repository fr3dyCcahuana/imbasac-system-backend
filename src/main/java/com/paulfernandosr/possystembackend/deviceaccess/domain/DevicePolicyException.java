package com.paulfernandosr.possystembackend.deviceaccess.domain;
public class DevicePolicyException extends RuntimeException {
    public enum Code { DEVICE_POLICY_NOT_CONFIGURED, DEVICE_POLICY_UNAVAILABLE, DEVICE_USER_NOT_AUTHORIZED, DEVICE_CERTIFICATE_CHANNEL_NOT_READY, DEVICE_CERTIFICATE_INVALID, DEVICE_NOT_AUTHORIZED }
    private final Code code;
    public DevicePolicyException(Code code) {
        super(switch(code) {
            case DEVICE_POLICY_NOT_CONFIGURED -> "El rol no tiene una política de acceso configurada.";
            case DEVICE_POLICY_UNAVAILABLE -> "No se pudo comprobar la política de acceso. Intenta nuevamente.";
            case DEVICE_USER_NOT_AUTHORIZED -> "El usuario no está autorizado para este acceso.";
            case DEVICE_CERTIFICATE_CHANNEL_NOT_READY -> "Este rol exige certificado y el canal de certificados todavía no está habilitado.";
            case DEVICE_CERTIFICATE_INVALID -> "El certificado no es válido o requiere renovación. Contacta al administrador.";
            case DEVICE_NOT_AUTHORIZED -> "Esta computadora no está autorizada para tu usuario.";
        });
        this.code=code;
    }
    public Code getCode() { return code; }
}
