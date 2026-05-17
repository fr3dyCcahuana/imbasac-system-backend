package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class PostgresWhatsAppConversationRepository implements WhatsAppConversationRepository {
    private final JdbcClient jdbcClient;

    private final RowMapper<WhatsAppConversation> mapper = (rs, rowNum) -> WhatsAppConversation.builder()
            .id(rs.getLong("id"))
            .contactId(rs.getLong("contact_id"))
            .waId(rs.getString("wa_id"))
            .phoneNumber(rs.getString("phone_number"))
            .profileName(rs.getString("profile_name"))
            .status(WhatsAppEnums.ConversationStatus.valueOf(rs.getString("status")))
            .automationMode(WhatsAppEnums.AutomationMode.valueOf(rs.getString("automation_mode")))
            .conversationState(resolveConversationState(rs.getString("conversation_state")))
            .assignedTo(rs.getString("assigned_to"))
            .lastProformaId(rs.getObject("last_proforma_id", Long.class))
            .lastMessagePreview(rs.getString("last_message_preview"))
            .lastMessageAt(rs.getObject("last_message_at", OffsetDateTime.class))
            .createdAt(rs.getObject("created_at", OffsetDateTime.class))
            .updatedAt(rs.getObject("updated_at", OffsetDateTime.class))
            .build();

    private final RowMapper<WhatsAppConversationSummary> summaryMapper = (rs, rowNum) -> WhatsAppConversationSummary.builder()
            .id(rs.getLong("id"))
            .contactId(rs.getLong("contact_id"))
            .waId(rs.getString("wa_id"))
            .phoneNumber(rs.getString("phone_number"))
            .profileName(rs.getString("profile_name"))
            .status(rs.getString("status"))
            .automationMode(rs.getString("automation_mode"))
            .assignedTo(rs.getString("assigned_to"))
            .lastProformaId(rs.getObject("last_proforma_id", Long.class))
            .lastMessagePreview(rs.getString("last_message_preview"))
            .lastMessageAt(rs.getObject("last_message_at", OffsetDateTime.class))
            .unreadInboundCount(rs.getLong("unread_inbound_count"))
            .build();

    @Override
    public WhatsAppConversation findOrCreateOpenConversation(WhatsAppContact contact) {
        Optional<WhatsAppConversation> existing = jdbcClient.sql("""
                        SELECT * FROM whatsapp_conversations
                        WHERE contact_id = :contactId AND status <> 'ARCHIVED'
                        ORDER BY updated_at DESC
                        LIMIT 1
                        """)
                .param("contactId", contact.getId())
                .query(mapper)
                .optional();
        if (existing.isPresent()) return existing.get();

        String insert = """
                INSERT INTO whatsapp_conversations (contact_id, wa_id, phone_number, profile_name, status, automation_mode, conversation_state)
                VALUES (:contactId, :waId, :phoneNumber, :profileName, 'OPEN', 'BOT', 'IDLE')
                RETURNING *
                """;
        return jdbcClient.sql(insert)
                .param("contactId", contact.getId())
                .param("waId", contact.getWaId())
                .param("phoneNumber", contact.getPhoneNumber())
                .param("profileName", contact.getProfileName())
                .query(mapper)
                .single();
    }

    @Override
    public Optional<WhatsAppConversation> findById(Long id) {
        return jdbcClient.sql("SELECT * FROM whatsapp_conversations WHERE id = :id")
                .param("id", id)
                .query(mapper)
                .optional();
    }

    @Override
    public List<WhatsAppConversationSummary> findPage(WhatsAppConversationFilter filter) {
        Map<String, Object> params = new HashMap<>();
        String where = buildWhere(filter, params);
        params.put("limit", filter.getSize());
        params.put("offset", filter.getPage() * filter.getSize());
        String sql = """
                SELECT c.*,
                       COALESCE((
                            SELECT count(*) FROM whatsapp_messages m
                            WHERE m.conversation_id = c.id
                              AND m.direction = 'INBOUND'
                              AND m.status = 'RECEIVED'
                       ), 0) AS unread_inbound_count
                FROM whatsapp_conversations c
                %s
                ORDER BY c.last_message_at DESC NULLS LAST, c.updated_at DESC
                LIMIT :limit OFFSET :offset
                """.formatted(where);
        return jdbcClient.sql(sql).params(params).query(summaryMapper).list();
    }

    @Override
    public long count(WhatsAppConversationFilter filter) {
        Map<String, Object> params = new HashMap<>();
        String where = buildWhere(filter, params);
        Long count = jdbcClient.sql("SELECT count(*) FROM whatsapp_conversations c " + where)
                .params(params)
                .query(Long.class)
                .single();
        return count == null ? 0 : count;
    }

    @Override
    public void updateLastMessage(Long conversationId, String preview, OffsetDateTime messageAt) {
        jdbcClient.sql("""
                UPDATE whatsapp_conversations
                SET last_message_preview = :preview, last_message_at = :messageAt, updated_at = now()
                WHERE id = :id
                """)
                .param("preview", trimPreview(preview))
                .param("messageAt", messageAt)
                .param("id", conversationId)
                .update();
    }

    @Override
    public void updateStatus(Long conversationId, WhatsAppEnums.ConversationStatus status) {
        jdbcClient.sql("UPDATE whatsapp_conversations SET status = :status, updated_at = now() WHERE id = :id")
                .param("status", status.name())
                .param("id", conversationId)
                .update();
    }

    @Override
    public void updateAutomationMode(Long conversationId, WhatsAppEnums.AutomationMode mode) {
        jdbcClient.sql("UPDATE whatsapp_conversations SET automation_mode = :mode, updated_at = now() WHERE id = :id")
                .param("mode", mode.name())
                .param("id", conversationId)
                .update();
    }


    @Override
    public void updateConversationState(Long conversationId, WhatsAppEnums.ConversationState state) {
        jdbcClient.sql("UPDATE whatsapp_conversations SET conversation_state = :state, updated_at = now() WHERE id = :id")
                .param("state", state.name())
                .param("id", conversationId)
                .update();
    }

    @Override
    public void assignTo(Long conversationId, String username) {
        jdbcClient.sql("UPDATE whatsapp_conversations SET assigned_to = :username, updated_at = now() WHERE id = :id")
                .param("username", username)
                .param("id", conversationId)
                .update();
    }

    @Override
    public void linkProforma(Long conversationId, Long proformaId) {
        jdbcClient.sql("UPDATE whatsapp_conversations SET last_proforma_id = :proformaId, status = 'QUOTED', updated_at = now() WHERE id = :id")
                .param("proformaId", proformaId)
                .param("id", conversationId)
                .update();
    }

    private WhatsAppEnums.ConversationState resolveConversationState(String raw) {
        if (raw == null || raw.isBlank()) return WhatsAppEnums.ConversationState.IDLE;
        try {
            return WhatsAppEnums.ConversationState.valueOf(raw);
        } catch (Exception ignored) {
            return WhatsAppEnums.ConversationState.IDLE;
        }
    }

    private String trimPreview(String value) {
        if (value == null) return null;
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private String buildWhere(WhatsAppConversationFilter filter, Map<String, Object> params) {
        List<String> clauses = new ArrayList<>();
        if (filter.getQuery() != null && !filter.getQuery().isBlank()) {
            clauses.add("(lower(c.wa_id) LIKE :query OR lower(c.phone_number) LIKE :query OR lower(COALESCE(c.profile_name,'')) LIKE :query OR lower(COALESCE(c.last_message_preview,'')) LIKE :query)");
            params.put("query", "%" + filter.getQuery().toLowerCase() + "%");
        }
        if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
            clauses.add("c.status = :status");
            params.put("status", filter.getStatus().toUpperCase());
        }
        if (filter.getAssignedTo() != null && !filter.getAssignedTo().isBlank()) {
            clauses.add("c.assigned_to = :assignedTo");
            params.put("assignedTo", filter.getAssignedTo());
        }
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }
}
