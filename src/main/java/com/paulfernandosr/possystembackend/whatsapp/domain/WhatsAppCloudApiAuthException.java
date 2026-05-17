package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.Getter;

/**
 * Error crítico de autenticación contra WhatsApp Cloud API.
 *
 * Se usa para detener campañas masivas cuando Meta responde 401 o code=190,
 * evitando marcar todos los destinatarios como fallidos por un token vencido,
 * inválido o revocado.
 */
@Getter
public class WhatsAppCloudApiAuthException extends RuntimeException {
    private final String errorCode;
    private final String errorSubcode;
    private final String fbtraceId;

    public WhatsAppCloudApiAuthException(String message, String errorCode, String errorSubcode, String fbtraceId) {
        super(message);
        this.errorCode = errorCode;
        this.errorSubcode = errorSubcode;
        this.fbtraceId = fbtraceId;
    }
}
