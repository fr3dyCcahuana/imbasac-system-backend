package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresWhatsAppWebhookEventRepository implements WhatsAppWebhookEventRepository {
    private final JdbcClient jdbcClient;

    @Override
    public Long save(String eventType, String waMessageId, String rawPayload) {
        return jdbcClient.sql("""
                INSERT INTO whatsapp_webhook_events (event_type, wa_message_id, raw_payload, processed)
                VALUES (:eventType, :waMessageId, CAST(:rawPayload AS jsonb), false)
                RETURNING id
                """)
                .param("eventType", eventType)
                .param("waMessageId", waMessageId)
                .param("rawPayload", rawPayload)
                .query(Long.class)
                .single();
    }

    @Override
    public void markProcessed(Long eventId) {
        jdbcClient.sql("UPDATE whatsapp_webhook_events SET processed = true, processed_at = now(), updated_at = now() WHERE id = :id")
                .param("id", eventId)
                .update();
    }

    @Override
    public void markFailed(Long eventId, String errorMessage) {
        jdbcClient.sql("UPDATE whatsapp_webhook_events SET processed = false, error_message = :error, updated_at = now() WHERE id = :id")
                .param("id", eventId)
                .param("error", errorMessage)
                .update();
    }
}
