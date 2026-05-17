package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostgresWhatsAppMessageRepository implements WhatsAppMessageRepository {
    private final JdbcClient jdbcClient;

    private final RowMapper<WhatsAppMessage> mapper = (rs, rowNum) -> WhatsAppMessage.builder()
            .id(rs.getLong("id"))
            .conversationId(rs.getLong("conversation_id"))
            .contactId(rs.getLong("contact_id"))
            .waMessageId(rs.getString("wa_message_id"))
            .direction(WhatsAppEnums.MessageDirection.valueOf(rs.getString("direction")))
            .type(WhatsAppEnums.MessageType.valueOf(rs.getString("type")))
            .status(WhatsAppEnums.MessageStatus.valueOf(rs.getString("status")))
            .textBody(rs.getString("text_body"))
            .mediaId(rs.getString("media_id"))
            .mediaMimeType(rs.getString("media_mime_type"))
            .mediaSha256(rs.getString("media_sha256"))
            .templateName(rs.getString("template_name"))
            .rawPayload(rs.getString("raw_payload"))
            .errorCode(rs.getString("error_code"))
            .errorTitle(rs.getString("error_title"))
            .errorDetails(rs.getString("error_details"))
            .messageAt(rs.getObject("message_at", OffsetDateTime.class))
            .createdAt(rs.getObject("created_at", OffsetDateTime.class))
            .updatedAt(rs.getObject("updated_at", OffsetDateTime.class))
            .build();

    @Override
    public WhatsAppMessage save(WhatsAppMessage message) {
        String sql = """
                INSERT INTO whatsapp_messages (
                    conversation_id, contact_id, wa_message_id, direction, type, status,
                    text_body, media_id, media_mime_type, media_sha256, template_name,
                    raw_payload, error_code, error_title, error_details, message_at
                ) VALUES (
                    :conversationId, :contactId, :waMessageId, :direction, :type, :status,
                    :textBody, :mediaId, :mediaMimeType, :mediaSha256, :templateName,
                    CAST(:rawPayload AS jsonb), :errorCode, :errorTitle, :errorDetails, :messageAt
                )
                ON CONFLICT (wa_message_id) WHERE wa_message_id IS NOT NULL DO UPDATE SET
                    status = EXCLUDED.status,
                    updated_at = now()
                RETURNING *
                """;
        return jdbcClient.sql(sql)
                .param("conversationId", message.getConversationId())
                .param("contactId", message.getContactId())
                .param("waMessageId", message.getWaMessageId())
                .param("direction", message.getDirection().name())
                .param("type", message.getType().name())
                .param("status", message.getStatus().name())
                .param("textBody", message.getTextBody())
                .param("mediaId", message.getMediaId())
                .param("mediaMimeType", message.getMediaMimeType())
                .param("mediaSha256", message.getMediaSha256())
                .param("templateName", message.getTemplateName())
                .param("rawPayload", message.getRawPayload())
                .param("errorCode", message.getErrorCode())
                .param("errorTitle", message.getErrorTitle())
                .param("errorDetails", message.getErrorDetails())
                .param("messageAt", message.getMessageAt())
                .query(mapper)
                .single();
    }

    @Override
    public List<WhatsAppMessage> findByConversation(Long conversationId, int page, int size) {
        return jdbcClient.sql("""
                SELECT * FROM whatsapp_messages
                WHERE conversation_id = :conversationId
                ORDER BY message_at ASC, id ASC
                LIMIT :limit OFFSET :offset
                """)
                .param("conversationId", conversationId)
                .param("limit", size)
                .param("offset", page * size)
                .query(mapper)
                .list();
    }


    @Override
    public List<WhatsAppMessage> findByConversationAfterId(Long conversationId, Long afterId, int size) {
        long safeAfterId = afterId == null ? 0L : afterId;

        return jdbcClient.sql("""
                SELECT * FROM whatsapp_messages
                WHERE conversation_id = :conversationId
                  AND id > :afterId
                ORDER BY message_at ASC, id ASC
                LIMIT :limit
                """)
                .param("conversationId", conversationId)
                .param("afterId", safeAfterId)
                .param("limit", size)
                .query(mapper)
                .list();
    }


    @Override
    public Long findMaxIdByConversation(Long conversationId) {
        return jdbcClient.sql("""
                SELECT COALESCE(MAX(id), 0)
                FROM whatsapp_messages
                WHERE conversation_id = :conversationId
                """)
                .param("conversationId", conversationId)
                .query(Long.class)
                .single();
    }

    @Override
    public long countByConversation(Long conversationId) {
        Long count = jdbcClient.sql("SELECT count(*) FROM whatsapp_messages WHERE conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .query(Long.class)
                .single();
        return count == null ? 0 : count;
    }

    @Override
    public Optional<WhatsAppMessage> findByWaMessageId(String waMessageId) {
        return jdbcClient.sql("SELECT * FROM whatsapp_messages WHERE wa_message_id = :id")
                .param("id", waMessageId)
                .query(mapper)
                .optional();
    }

    @Override
    public void updateStatus(String waMessageId,
                             WhatsAppEnums.MessageStatus status,
                             String errorCode,
                             String errorTitle,
                             String errorDetails,
                             String rawPayload) {
        jdbcClient.sql("""
                UPDATE whatsapp_messages
                SET status = :status,
                    error_code = :errorCode,
                    error_title = :errorTitle,
                    error_details = :errorDetails,
                    raw_payload = COALESCE(CAST(:rawPayload AS jsonb), raw_payload),
                    updated_at = now()
                WHERE wa_message_id = :waMessageId
                """)
                .param("status", status.name())
                .param("errorCode", errorCode)
                .param("errorTitle", errorTitle)
                .param("errorDetails", errorDetails)
                .param("rawPayload", rawPayload)
                .param("waMessageId", waMessageId)
                .update();
    }
}
