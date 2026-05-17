package com.paulfernandosr.possystembackend.whatsapp.domain.port.output;

public interface WhatsAppWebhookEventRepository {
    Long save(String eventType, String waMessageId, String rawPayload);
    void markProcessed(Long eventId);
    void markFailed(Long eventId, String errorMessage);
}
