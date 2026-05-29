package com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PostgresWhatsAppCenterQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DashboardSummaryResponse dashboardSummary() {
        String sql = """
            SELECT
                (SELECT count(*) FROM whatsapp.conversation_current_v) AS total_conversations,
                (SELECT count(*) FROM whatsapp.conversation_current_v WHERE current_status IN ('OPEN','WAITING_CUSTOMER','WAITING_PRODUCT_SELECTION','WAITING_QUANTITY','WAITING_CUSTOMER_DATA','WAITING_ADDRESS')) AS open_conversations,
                (SELECT count(*) FROM whatsapp.conversation_current_v WHERE current_status = 'QUOTED') AS quoted_conversations,
                (SELECT count(*) FROM whatsapp.conversation_current_v WHERE current_status = 'STANDBY') AS standby_conversations,
                (SELECT count(*) FROM whatsapp.conversation_current_v WHERE current_status = 'CLOSED_SUCCESS') AS closed_success_conversations,
                (SELECT count(*) FROM whatsapp.conversation_current_v WHERE current_status = 'CLOSED_LOST') AS closed_lost_conversations,
                (SELECT count(*) FROM whatsapp.conversation_current_v WHERE current_status = 'PENDING_HUMAN') AS pending_human_conversations,
                (SELECT count(*) FROM whatsapp.messages WHERE created_at >= CURRENT_DATE) AS total_messages_today,
                (SELECT count(*) FROM whatsapp.messages WHERE direction = 'INBOUND' AND created_at >= CURRENT_DATE) AS inbound_messages_today,
                (SELECT count(*) FROM whatsapp.messages WHERE direction = 'OUTBOUND' AND created_at >= CURRENT_DATE) AS outbound_messages_today,
                (SELECT count(*) FROM whatsapp.conversation_events WHERE event_type = 'PROFORMA_CREATED' AND created_at >= CURRENT_DATE) AS proformas_created_today,
                (SELECT count(*) FROM whatsapp.account_alert_events WHERE upper(coalesce(alert_status, 'ACTIVE')) = 'ACTIVE') AS account_alerts_active,
                (SELECT count(*) FROM whatsapp.message_status_events WHERE status = 'failed' AND created_at >= CURRENT_DATE) AS failed_messages_today,
                (SELECT count(*) FROM whatsapp.campaign_current_v WHERE status = 'RUNNING') AS campaigns_running
        """;
        return jdbc.queryForObject(sql, new MapSqlParameterSource(), (rs, rowNum) -> DashboardSummaryResponse.builder()
                .totalConversations(rs.getLong("total_conversations"))
                .openConversations(rs.getLong("open_conversations"))
                .quotedConversations(rs.getLong("quoted_conversations"))
                .standbyConversations(rs.getLong("standby_conversations"))
                .closedSuccessConversations(rs.getLong("closed_success_conversations"))
                .closedLostConversations(rs.getLong("closed_lost_conversations"))
                .pendingHumanConversations(rs.getLong("pending_human_conversations"))
                .totalMessagesToday(rs.getLong("total_messages_today"))
                .inboundMessagesToday(rs.getLong("inbound_messages_today"))
                .outboundMessagesToday(rs.getLong("outbound_messages_today"))
                .proformasCreatedToday(rs.getLong("proformas_created_today"))
                .accountAlertsActive(rs.getLong("account_alerts_active"))
                .failedMessagesToday(rs.getLong("failed_messages_today"))
                .campaignsRunning(rs.getLong("campaigns_running"))
                .build());
    }

    public List<DailyMetricResponse> dailyMetrics(String from, String to) {
        MapSqlParameterSource params = paramsWithRange(from, to);
        String sql = """
            WITH days AS (
                SELECT generate_series(
                    date_trunc('day', COALESCE(:fromTs, now() - interval '14 days')),
                    date_trunc('day', COALESCE(:toTs, now())),
                    interval '1 day'
                )::date AS metric_date
            )
            SELECT
                d.metric_date,
                (SELECT count(*) FROM whatsapp.conversations c WHERE c.created_at::date = d.metric_date) AS conversations_created,
                (SELECT count(*) FROM whatsapp.messages m WHERE m.direction = 'INBOUND' AND m.created_at::date = d.metric_date) AS messages_inbound,
                (SELECT count(*) FROM whatsapp.messages m WHERE m.direction = 'OUTBOUND' AND m.created_at::date = d.metric_date) AS messages_outbound,
                (SELECT count(*) FROM whatsapp.conversation_events ce WHERE ce.event_type = 'PROFORMA_CREATED' AND ce.created_at::date = d.metric_date) AS proformas_created,
                (SELECT count(*) FROM whatsapp.conversation_events ce WHERE ce.status = 'CLOSED_SUCCESS' AND ce.created_at::date = d.metric_date) AS closed_success
            FROM days d
            ORDER BY d.metric_date ASC
        """;
        return jdbc.query(sql, params, (rs, rowNum) -> DailyMetricResponse.builder()
                .date(rs.getObject("metric_date", LocalDate.class))
                .conversationsCreated(rs.getLong("conversations_created"))
                .messagesInbound(rs.getLong("messages_inbound"))
                .messagesOutbound(rs.getLong("messages_outbound"))
                .proformasCreated(rs.getLong("proformas_created"))
                .closedSuccess(rs.getLong("closed_success"))
                .build());
    }

    public PageResponse<ConversationSummaryResponse> conversations(int page, int size, String q, String status, String from, String to, String waId, String phoneNumber, Long proformaId, Long sellerId) {
        MapSqlParameterSource params = pageParams(page, size);
        String cleanQ = blankToNull(q);
        String cleanStatus = blankToNull(status);
        String cleanWaId = blankToNull(waId);
        String cleanPhoneNumber = blankToNull(phoneNumber);
        OffsetDateTime fromTs = parseDateTime(from);
        OffsetDateTime toTs = parseDateTime(to);

        StringBuilder fromSql = new StringBuilder("""
            FROM whatsapp.conversation_current_v
            WHERE 1 = 1
        """);
        if (cleanStatus != null) {
            fromSql.append(" AND current_status = :status");
            params.addValue("status", cleanStatus, Types.VARCHAR);
        }
        if (cleanWaId != null) {
            fromSql.append(" AND wa_id = :waId");
            params.addValue("waId", cleanWaId, Types.VARCHAR);
        }
        if (cleanPhoneNumber != null) {
            fromSql.append(" AND phone_number ILIKE '%' || :phoneNumber || '%'");
            params.addValue("phoneNumber", cleanPhoneNumber, Types.VARCHAR);
        }
        if (proformaId != null) {
            fromSql.append(" AND proforma_id = :proformaId");
            params.addValue("proformaId", proformaId, Types.BIGINT);
        }
        if (sellerId != null) {
            fromSql.append(" AND seller_id = :sellerId");
            params.addValue("sellerId", sellerId, Types.BIGINT);
        }
        if (fromTs != null) {
            fromSql.append(" AND conversation_created_at >= :fromTs");
            params.addValue("fromTs", fromTs, Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (toTs != null) {
            fromSql.append(" AND conversation_created_at < :toTs");
            params.addValue("toTs", toTs, Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (cleanQ != null) {
            fromSql.append("""
                 AND (
                        wa_id ILIKE '%' || :q || '%'
                     OR phone_number ILIKE '%' || :q || '%'
                     OR profile_name ILIKE '%' || :q || '%'
                     OR last_message_preview ILIKE '%' || :q || '%'
                  )
            """);
            params.addValue("q", cleanQ, Types.VARCHAR);
        }
        String select = """
            SELECT conversation_id, contact_id, wa_id, phone_number, profile_name, current_status,
                   last_event_type, last_event_at, last_message_preview, last_message_at,
                   proforma_id, proforma_series, proforma_number, proforma_total,
                   seller_id, seller_name, conversation_created_at
        """;
        String fromSqlText = fromSql.toString();
        List<ConversationSummaryResponse> rows = jdbc.query(select + fromSqlText + """
            ORDER BY COALESCE(last_message_at, last_event_at, conversation_created_at) DESC
            LIMIT :limit OFFSET :offset
        """, params, conversationMapper());
        long total = count(fromSqlText, params);
        return page(rows, page, size, total);
    }

    public ConversationDetailResponse conversation(Long conversationId) {
        String sql = """
            SELECT conversation_id, contact_id, wa_id, phone_number, profile_name, current_status,
                   last_event_type, last_event_at, proforma_id, proforma_series, proforma_number,
                   proforma_total, seller_id, seller_name, conversation_created_at
            FROM whatsapp.conversation_current_v
            WHERE conversation_id = :conversationId
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("conversationId", conversationId);
        List<ConversationDetailResponse> rows = jdbc.query(sql, params, (rs, rowNum) -> ConversationDetailResponse.builder()
                .conversationId(rs.getLong("conversation_id"))
                .contact(ContactInfo.builder()
                        .contactId(getLong(rs, "contact_id"))
                        .waId(rs.getString("wa_id"))
                        .phoneNumber(rs.getString("phone_number"))
                        .profileName(rs.getString("profile_name"))
                        .build())
                .currentStatus(rs.getString("current_status"))
                .lastEventType(rs.getString("last_event_type"))
                .proforma(ProformaInfo.builder()
                        .proformaId(getLong(rs, "proforma_id"))
                        .series(rs.getString("proforma_series"))
                        .number(getLong(rs, "proforma_number"))
                        .total(rs.getBigDecimal("proforma_total"))
                        .build())
                .seller(SellerInfo.builder()
                        .sellerId(getLong(rs, "seller_id"))
                        .sellerName(rs.getString("seller_name"))
                        .build())
                .conversationCreatedAt(getOffset(rs, "conversation_created_at"))
                .lastEventAt(getOffset(rs, "last_event_at"))
                .build());
        return rows.isEmpty() ? null : rows.get(0);
    }

    public ConversationSummaryResponse conversationSummary(Long conversationId) {
        String sql = """
            SELECT conversation_id, contact_id, wa_id, phone_number, profile_name, current_status,
                   last_event_type, last_event_at, last_message_preview, last_message_at,
                   proforma_id, proforma_series, proforma_number, proforma_total,
                   seller_id, seller_name, conversation_created_at
            FROM whatsapp.conversation_current_v
            WHERE conversation_id = :conversationId
        """;
        List<ConversationSummaryResponse> rows = jdbc.query(
                sql,
                new MapSqlParameterSource("conversationId", conversationId),
                conversationMapper()
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public PageResponse<MessageResponse> messages(Long conversationId, int page, int size) {
        MapSqlParameterSource params = pageParams(page, size).addValue("conversationId", conversationId);
        String fromSql = " FROM whatsapp.messages m LEFT JOIN whatsapp.message_current_status_v s ON s.wa_message_id = m.wa_message_id WHERE m.conversation_id = :conversationId ";
        String select = """
            SELECT m.id AS message_id, m.conversation_id, m.direction, m.wa_message_id, m.from_wa_id,
                   m.to_wa_id, m.message_type, m.text_body, m.media_id, m.media_mime_type,
                   m.media_filename, m.media_caption, m.interactive_type, m.interactive_id,
                   m.interactive_title, m.location_latitude, m.location_longitude, m.location_name,
                   m.location_address,
                   COALESCE(
                       m.raw_message ->> 'imageUrl',
                       m.raw_message #>> '{delivery,imageUrl}',
                       CASE
                           WHEN m.media_id IS NOT NULL THEN '/api/whatsapp-center/media/' || m.media_id
                           ELSE NULL
                       END
                   ) AS media_url,
                   s.current_status, m.wa_timestamp, m.created_at
        """;
        List<MessageResponse> rows = jdbc.query(select + fromSql + " ORDER BY COALESCE(m.wa_timestamp, m.created_at) ASC, m.id ASC LIMIT :limit OFFSET :offset", params, messageMapper());
        return page(rows, page, size, count(fromSql, params));
    }

    public PageResponse<ConversationEventResponse> conversationEvents(Long conversationId, int page, int size) {
        MapSqlParameterSource params = pageParams(page, size).addValue("conversationId", conversationId);
        String fromSql = " FROM whatsapp.conversation_events WHERE conversation_id = :conversationId ";
        String sql = """
            SELECT id AS event_id, conversation_id, event_type, status, proforma_id, proforma_series,
                   proforma_number, proforma_total, seller_id, seller_name, notes, metadata, created_at
        """ + fromSql + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset";
        List<ConversationEventResponse> rows = jdbc.query(sql, params, conversationEventMapper());
        return page(rows, page, size, count(fromSql, params));
    }

    public List<MessageStatusEventResponse> messageStatusEvents(String waMessageId) {
        String sql = """
            SELECT id, wa_message_id, recipient_wa_id, status, meta_conversation_id, pricing_category,
                   pricing_model, billable, error_code, error_title, error_message, wa_timestamp, created_at
            FROM whatsapp.message_status_events
            WHERE wa_message_id = :waMessageId
            ORDER BY COALESCE(wa_timestamp, created_at) DESC, id DESC
        """;
        return jdbc.query(sql, new MapSqlParameterSource("waMessageId", waMessageId), messageStatusMapper());
    }

    public PageResponse<AgentRunResponse> agentRuns(Long conversationId, int page, int size) {
        MapSqlParameterSource params = pageParams(page, size).addValue("conversationId", conversationId);
        String fromSql = " FROM whatsapp.agent_runs WHERE conversation_id = :conversationId ";
        String sql = """
            SELECT id, conversation_id, input_message_id, intent, model_name, model_provider,
                   confidence, prompt_metadata, result_json, error_message, started_at, finished_at
        """ + fromSql + " ORDER BY started_at DESC, id DESC LIMIT :limit OFFSET :offset";
        List<AgentRunResponse> rows = jdbc.query(sql, params, agentRunMapper());
        return page(rows, page, size, count(fromSql, params));
    }

    public PageResponse<ProductSuggestionResponse> productSuggestions(Long conversationId, int page, int size) {
        MapSqlParameterSource params = pageParams(page, size).addValue("conversationId", conversationId);
        String fromSql = " FROM whatsapp.product_suggestions WHERE conversation_id = :conversationId ";
        String sql = """
            SELECT id, conversation_id, source_message_id, suggestion_group_id::text AS suggestion_group_id,
                   position, source_query, product_id, sku, product_name, product_snapshot, created_at
        """ + fromSql + " ORDER BY created_at DESC, position ASC LIMIT :limit OFFSET :offset";
        List<ProductSuggestionResponse> rows = jdbc.query(sql, params, productSuggestionMapper());
        return page(rows, page, size, count(fromSql, params));
    }

    public PageResponse<QuoteDraftEventResponse> quoteEvents(Long conversationId, int page, int size) {
        MapSqlParameterSource params = pageParams(page, size).addValue("conversationId", conversationId);
        String fromSql = " FROM whatsapp.quote_draft_events WHERE conversation_id = :conversationId ";
        String sql = """
            SELECT id, conversation_id, event_type, product_id, sku, product_name, quantity,
                   unit_price, line_total, customer_document_type, customer_document_number,
                   customer_name, department, province, district, address, official_proforma_id,
                   official_proforma_series, official_proforma_number, metadata, created_at
        """ + fromSql + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset";
        List<QuoteDraftEventResponse> rows = jdbc.query(sql, params, quoteEventMapper());
        return page(rows, page, size, count(fromSql, params));
    }

    public PageResponse<TemplateEventResponse> templateEvents(int page, int size, String templateName, String eventType, String from, String to) {
        MapSqlParameterSource params = pageParams(page, size);
        String cleanTemplateName = blankToNull(templateName);
        String cleanEventType = blankToNull(eventType);
        OffsetDateTime fromTs = parseDateTime(from);
        OffsetDateTime toTs = parseDateTime(to);
        StringBuilder fromSql = new StringBuilder("""
            FROM whatsapp.template_events
            WHERE 1 = 1
        """);
        if (cleanTemplateName != null) {
            fromSql.append(" AND message_template_name ILIKE '%' || :templateName || '%'");
            params.addValue("templateName", cleanTemplateName, Types.VARCHAR);
        }
        if (cleanEventType != null) {
            fromSql.append(" AND event_type = :eventType");
            params.addValue("eventType", cleanEventType, Types.VARCHAR);
        }
        if (fromTs != null) {
            fromSql.append(" AND received_at >= :fromTs");
            params.addValue("fromTs", fromTs, Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (toTs != null) {
            fromSql.append(" AND received_at < :toTs");
            params.addValue("toTs", toTs, Types.TIMESTAMP_WITH_TIMEZONE);
        }
        String sql = """
            SELECT id, waba_id, event_type, message_template_id, message_template_name,
                   message_template_language, previous_category, new_category, template_status,
                   reason, received_at
        """ + fromSql + " ORDER BY received_at DESC, id DESC LIMIT :limit OFFSET :offset";
        List<TemplateEventResponse> rows = jdbc.query(sql, params, templateEventMapper());
        return page(rows, page, size, count(fromSql.toString(), params));
    }

    public PageResponse<AccountAlertResponse> accountAlerts(int page, int size, String severity, String status, String alertType, String from, String to) {
        MapSqlParameterSource params = pageParams(page, size);
        String cleanSeverity = blankToNull(severity);
        String cleanStatus = blankToNull(status);
        String cleanAlertType = blankToNull(alertType);
        OffsetDateTime fromTs = parseDateTime(from);
        OffsetDateTime toTs = parseDateTime(to);
        StringBuilder fromSql = new StringBuilder("""
            FROM whatsapp.account_alert_events
            WHERE 1 = 1
        """);
        if (cleanSeverity != null) {
            fromSql.append(" AND alert_severity = :severity");
            params.addValue("severity", cleanSeverity, Types.VARCHAR);
        }
        if (cleanStatus != null) {
            fromSql.append(" AND alert_status = :status");
            params.addValue("status", cleanStatus, Types.VARCHAR);
        }
        if (cleanAlertType != null) {
            fromSql.append(" AND alert_type ILIKE '%' || :alertType || '%'");
            params.addValue("alertType", cleanAlertType, Types.VARCHAR);
        }
        if (fromTs != null) {
            fromSql.append(" AND received_at >= :fromTs");
            params.addValue("fromTs", fromTs, Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (toTs != null) {
            fromSql.append(" AND received_at < :toTs");
            params.addValue("toTs", toTs, Types.TIMESTAMP_WITH_TIMEZONE);
        }
        String sql = """
            SELECT id, waba_id, entity_type, entity_id, alert_severity, alert_status,
                   alert_type, alert_description, received_at
        """ + fromSql + " ORDER BY received_at DESC, id DESC LIMIT :limit OFFSET :offset";
        List<AccountAlertResponse> rows = jdbc.query(sql, params, accountAlertMapper());
        return page(rows, page, size, count(fromSql.toString(), params));
    }

    public PageResponse<WebhookEventResponse> webhookEvents(int page, int size, String eventKind, String fieldName, String wabaId, String from, String to) {
        MapSqlParameterSource params = pageParams(page, size);
        String cleanEventKind = blankToNull(eventKind);
        String cleanFieldName = blankToNull(fieldName);
        String cleanWabaId = blankToNull(wabaId);
        OffsetDateTime fromTs = parseDateTime(from);
        OffsetDateTime toTs = parseDateTime(to);
        StringBuilder fromSql = new StringBuilder("""
            FROM whatsapp.webhook_events
            WHERE 1 = 1
        """);
        if (cleanEventKind != null) {
            fromSql.append(" AND event_kind = :eventKind");
            params.addValue("eventKind", cleanEventKind, Types.VARCHAR);
        }
        if (cleanFieldName != null) {
            fromSql.append(" AND field_name = :fieldName");
            params.addValue("fieldName", cleanFieldName, Types.VARCHAR);
        }
        if (cleanWabaId != null) {
            fromSql.append(" AND waba_id = :wabaId");
            params.addValue("wabaId", cleanWabaId, Types.VARCHAR);
        }
        if (fromTs != null) {
            fromSql.append(" AND received_at >= :fromTs");
            params.addValue("fromTs", fromTs, Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (toTs != null) {
            fromSql.append(" AND received_at < :toTs");
            params.addValue("toTs", toTs, Types.TIMESTAMP_WITH_TIMEZONE);
        }
        String sql = """
            SELECT id, object_type, waba_id, entry_id, field_name, event_kind, wa_message_id,
                   signature_valid, received_at
        """ + fromSql + " ORDER BY received_at DESC, id DESC LIMIT :limit OFFSET :offset";
        List<WebhookEventResponse> rows = jdbc.query(sql, params, webhookEventMapper());
        return page(rows, page, size, count(fromSql.toString(), params));
    }

    public WebhookEventDetailResponse webhookEvent(Long id) {
        String sql = """
            SELECT id, object_type, waba_id, entry_id, field_name, event_kind, wa_message_id,
                   signature_valid, raw_payload, received_at
            FROM whatsapp.webhook_events
            WHERE id = :id
        """;
        List<WebhookEventDetailResponse> rows = jdbc.query(sql, new MapSqlParameterSource("id", id), webhookEventDetailMapper());
        return rows.isEmpty() ? null : rows.get(0);
    }

    public PageResponse<CampaignResponse> campaigns(int page, int size, String q, String status, String from, String to) {
        MapSqlParameterSource params = pageParams(page, size);
        String cleanQ = blankToNull(q);
        String cleanStatus = blankToNull(status);
        OffsetDateTime fromTs = parseDateTime(from);
        OffsetDateTime toTs = parseDateTime(to);
        StringBuilder fromSql = new StringBuilder("""
            FROM whatsapp.campaign_current_v
            WHERE 1 = 1
        """);
        if (cleanStatus != null) {
            fromSql.append(" AND status = :status");
            params.addValue("status", cleanStatus, Types.VARCHAR);
        }
        if (fromTs != null) {
            fromSql.append(" AND \"createdAt\" >= :fromTs");
            params.addValue("fromTs", fromTs, Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (toTs != null) {
            fromSql.append(" AND \"createdAt\" < :toTs");
            params.addValue("toTs", toTs, Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (cleanQ != null) {
            fromSql.append(" AND (name ILIKE '%' || :q || '%' OR description ILIKE '%' || :q || '%' OR \"templateName\" ILIKE '%' || :q || '%')");
            params.addValue("q", cleanQ, Types.VARCHAR);
        }
        String sql = """
            SELECT id AS campaign_id, name, description, "templateName" AS template_name,
                   "languageCode" AS language_code, "recipientMode" AS recipient_mode,
                   status AS current_status, "totalRecipients" AS total_recipients,
                   "sentCount" AS sent_count, "failedCount" AS failed_count,
                   "createdAt" AS created_at, COALESCE("completedAt", "startedAt", "createdAt") AS last_event_at
        """ + fromSql + " ORDER BY COALESCE(\"completedAt\", \"startedAt\", \"createdAt\") DESC, id DESC LIMIT :limit OFFSET :offset";
        List<CampaignResponse> rows = jdbc.query(sql, params, campaignMapper());
        return page(rows, page, size, count(fromSql.toString(), params));
    }

    public CampaignResponse campaign(Long campaignId) {
        String sql = """
            SELECT id AS campaign_id, name, description, "templateName" AS template_name,
                   "languageCode" AS language_code, "recipientMode" AS recipient_mode,
                   status AS current_status, "totalRecipients" AS total_recipients,
                   "sentCount" AS sent_count, "failedCount" AS failed_count,
                   "createdAt" AS created_at, COALESCE("completedAt", "startedAt", "createdAt") AS last_event_at
            FROM whatsapp.campaign_current_v
            WHERE id = :campaignId
        """;
        List<CampaignResponse> rows = jdbc.query(sql, new MapSqlParameterSource("campaignId", campaignId), campaignMapper());
        return rows.isEmpty() ? null : rows.get(0);
    }

    public PageResponse<CampaignRecipientResponse> campaignRecipients(Long campaignId, int page, int size) {
        MapSqlParameterSource params = pageParams(page, size).addValue("campaignId", campaignId);
        String fromSql = " FROM whatsapp.campaign_recipient_current_v WHERE \"campaignId\" = :campaignId ";
        String sql = """
            SELECT id, "campaignId" AS campaign_id, "contactId" AS contact_id, "waId" AS wa_id,
                   "phoneNumber" AS phone_number, "profileName" AS profile_name, status,
                   "waMessageId" AS wa_message_id, "errorMessage" AS error_message,
                   "sentAt" AS sent_at, "createdAt" AS created_at
        """ + fromSql + " ORDER BY id ASC LIMIT :limit OFFSET :offset";
        List<CampaignRecipientResponse> rows = jdbc.query(sql, params, campaignRecipientMapper());
        return page(rows, page, size, count(fromSql, params));
    }

    public PageResponse<CampaignEventResponse> campaignEvents(Long campaignId, int page, int size) {
        MapSqlParameterSource params = pageParams(page, size).addValue("campaignId", campaignId);
        String fromSql = " FROM whatsapp.campaign_events WHERE campaign_id = :campaignId ";
        String sql = """
            SELECT id, campaign_id, event_type, status, notes, metadata, created_at
        """ + fromSql + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset";
        List<CampaignEventResponse> rows = jdbc.query(sql, params, campaignEventMapper());
        return page(rows, page, size, count(fromSql, params));
    }

    private RowMapper<ConversationSummaryResponse> conversationMapper() {
        return (rs, rowNum) -> ConversationSummaryResponse.builder()
                .conversationId(getLong(rs, "conversation_id"))
                .contactId(getLong(rs, "contact_id"))
                .waId(rs.getString("wa_id"))
                .phoneNumber(rs.getString("phone_number"))
                .profileName(rs.getString("profile_name"))
                .currentStatus(rs.getString("current_status"))
                .lastEventType(rs.getString("last_event_type"))
                .lastEventAt(getOffset(rs, "last_event_at"))
                .lastMessagePreview(rs.getString("last_message_preview"))
                .lastMessageAt(getOffset(rs, "last_message_at"))
                .proformaId(getLong(rs, "proforma_id"))
                .proformaSeries(rs.getString("proforma_series"))
                .proformaNumber(getLong(rs, "proforma_number"))
                .proformaTotal(rs.getBigDecimal("proforma_total"))
                .sellerId(getLong(rs, "seller_id"))
                .sellerName(rs.getString("seller_name"))
                .conversationCreatedAt(getOffset(rs, "conversation_created_at"))
                .build();
    }

    private RowMapper<MessageResponse> messageMapper() {
        return (rs, rowNum) -> MessageResponse.builder()
                .messageId(getLong(rs, "message_id"))
                .conversationId(getLong(rs, "conversation_id"))
                .direction(rs.getString("direction"))
                .waMessageId(rs.getString("wa_message_id"))
                .fromWaId(rs.getString("from_wa_id"))
                .toWaId(rs.getString("to_wa_id"))
                .messageType(rs.getString("message_type"))
                .textBody(rs.getString("text_body"))
                .mediaId(rs.getString("media_id"))
                .mediaUrl(rs.getString("media_url"))
                .mediaMimeType(rs.getString("media_mime_type"))
                .mediaFilename(rs.getString("media_filename"))
                .mediaCaption(rs.getString("media_caption"))
                .interactiveType(rs.getString("interactive_type"))
                .interactiveId(rs.getString("interactive_id"))
                .interactiveTitle(rs.getString("interactive_title"))
                .locationLatitude(rs.getBigDecimal("location_latitude"))
                .locationLongitude(rs.getBigDecimal("location_longitude"))
                .locationName(rs.getString("location_name"))
                .locationAddress(rs.getString("location_address"))
                .currentStatus(rs.getString("current_status"))
                .waTimestamp(getOffset(rs, "wa_timestamp"))
                .createdAt(getOffset(rs, "created_at"))
                .build();
    }

    private RowMapper<ConversationEventResponse> conversationEventMapper() {
        return (rs, rowNum) -> ConversationEventResponse.builder()
                .eventId(getLong(rs, "event_id"))
                .conversationId(getLong(rs, "conversation_id"))
                .eventType(rs.getString("event_type"))
                .status(rs.getString("status"))
                .proformaId(getLong(rs, "proforma_id"))
                .proformaSeries(rs.getString("proforma_series"))
                .proformaNumber(getLong(rs, "proforma_number"))
                .proformaTotal(rs.getBigDecimal("proforma_total"))
                .sellerId(getLong(rs, "seller_id"))
                .sellerName(rs.getString("seller_name"))
                .notes(rs.getString("notes"))
                .metadata(json(rs, "metadata"))
                .createdAt(getOffset(rs, "created_at"))
                .build();
    }

    private RowMapper<MessageStatusEventResponse> messageStatusMapper() {
        return (rs, rowNum) -> MessageStatusEventResponse.builder()
                .id(getLong(rs, "id"))
                .waMessageId(rs.getString("wa_message_id"))
                .recipientWaId(rs.getString("recipient_wa_id"))
                .status(rs.getString("status"))
                .metaConversationId(rs.getString("meta_conversation_id"))
                .pricingCategory(rs.getString("pricing_category"))
                .pricingModel(rs.getString("pricing_model"))
                .billable(getBoolean(rs, "billable"))
                .errorCode(rs.getString("error_code"))
                .errorTitle(rs.getString("error_title"))
                .errorMessage(rs.getString("error_message"))
                .waTimestamp(getOffset(rs, "wa_timestamp"))
                .createdAt(getOffset(rs, "created_at"))
                .build();
    }

    private RowMapper<AgentRunResponse> agentRunMapper() {
        return (rs, rowNum) -> AgentRunResponse.builder()
                .id(getLong(rs, "id"))
                .conversationId(getLong(rs, "conversation_id"))
                .inputMessageId(getLong(rs, "input_message_id"))
                .intent(rs.getString("intent"))
                .modelName(rs.getString("model_name"))
                .modelProvider(rs.getString("model_provider"))
                .confidence(rs.getBigDecimal("confidence"))
                .promptMetadata(json(rs, "prompt_metadata"))
                .resultJson(json(rs, "result_json"))
                .errorMessage(rs.getString("error_message"))
                .startedAt(getOffset(rs, "started_at"))
                .finishedAt(getOffset(rs, "finished_at"))
                .build();
    }

    private RowMapper<ProductSuggestionResponse> productSuggestionMapper() {
        return (rs, rowNum) -> ProductSuggestionResponse.builder()
                .id(getLong(rs, "id"))
                .conversationId(getLong(rs, "conversation_id"))
                .sourceMessageId(getLong(rs, "source_message_id"))
                .suggestionGroupId(rs.getString("suggestion_group_id"))
                .position((Integer) rs.getObject("position"))
                .sourceQuery(rs.getString("source_query"))
                .productId(getLong(rs, "product_id"))
                .sku(rs.getString("sku"))
                .productName(rs.getString("product_name"))
                .productSnapshot(json(rs, "product_snapshot"))
                .createdAt(getOffset(rs, "created_at"))
                .build();
    }

    private RowMapper<QuoteDraftEventResponse> quoteEventMapper() {
        return (rs, rowNum) -> QuoteDraftEventResponse.builder()
                .id(getLong(rs, "id"))
                .conversationId(getLong(rs, "conversation_id"))
                .eventType(rs.getString("event_type"))
                .productId(getLong(rs, "product_id"))
                .sku(rs.getString("sku"))
                .productName(rs.getString("product_name"))
                .quantity(rs.getBigDecimal("quantity"))
                .unitPrice(rs.getBigDecimal("unit_price"))
                .lineTotal(rs.getBigDecimal("line_total"))
                .customerDocumentType(rs.getString("customer_document_type"))
                .customerDocumentNumber(rs.getString("customer_document_number"))
                .customerName(rs.getString("customer_name"))
                .department(rs.getString("department"))
                .province(rs.getString("province"))
                .district(rs.getString("district"))
                .address(rs.getString("address"))
                .officialProformaId(getLong(rs, "official_proforma_id"))
                .officialProformaSeries(rs.getString("official_proforma_series"))
                .officialProformaNumber(getLong(rs, "official_proforma_number"))
                .metadata(json(rs, "metadata"))
                .createdAt(getOffset(rs, "created_at"))
                .build();
    }

    private RowMapper<TemplateEventResponse> templateEventMapper() {
        return (rs, rowNum) -> TemplateEventResponse.builder()
                .id(getLong(rs, "id"))
                .wabaId(rs.getString("waba_id"))
                .eventType(rs.getString("event_type"))
                .messageTemplateId(rs.getString("message_template_id"))
                .messageTemplateName(rs.getString("message_template_name"))
                .messageTemplateLanguage(rs.getString("message_template_language"))
                .previousCategory(rs.getString("previous_category"))
                .newCategory(rs.getString("new_category"))
                .templateStatus(rs.getString("template_status"))
                .reason(rs.getString("reason"))
                .receivedAt(getOffset(rs, "received_at"))
                .build();
    }

    private RowMapper<AccountAlertResponse> accountAlertMapper() {
        return (rs, rowNum) -> AccountAlertResponse.builder()
                .id(getLong(rs, "id"))
                .wabaId(rs.getString("waba_id"))
                .entityType(rs.getString("entity_type"))
                .entityId(rs.getString("entity_id"))
                .alertSeverity(rs.getString("alert_severity"))
                .alertStatus(rs.getString("alert_status"))
                .alertType(rs.getString("alert_type"))
                .alertDescription(rs.getString("alert_description"))
                .receivedAt(getOffset(rs, "received_at"))
                .build();
    }

    private RowMapper<WebhookEventResponse> webhookEventMapper() {
        return (rs, rowNum) -> WebhookEventResponse.builder()
                .id(getLong(rs, "id"))
                .objectType(rs.getString("object_type"))
                .wabaId(rs.getString("waba_id"))
                .entryId(rs.getString("entry_id"))
                .fieldName(rs.getString("field_name"))
                .eventKind(rs.getString("event_kind"))
                .waMessageId(rs.getString("wa_message_id"))
                .signatureValid(getBoolean(rs, "signature_valid"))
                .receivedAt(getOffset(rs, "received_at"))
                .build();
    }

    private RowMapper<WebhookEventDetailResponse> webhookEventDetailMapper() {
        return (rs, rowNum) -> WebhookEventDetailResponse.builder()
                .id(getLong(rs, "id"))
                .objectType(rs.getString("object_type"))
                .wabaId(rs.getString("waba_id"))
                .entryId(rs.getString("entry_id"))
                .fieldName(rs.getString("field_name"))
                .eventKind(rs.getString("event_kind"))
                .waMessageId(rs.getString("wa_message_id"))
                .signatureValid(getBoolean(rs, "signature_valid"))
                .rawPayload(json(rs, "raw_payload"))
                .receivedAt(getOffset(rs, "received_at"))
                .build();
    }

    private RowMapper<CampaignResponse> campaignMapper() {
        return (rs, rowNum) -> CampaignResponse.builder()
                .campaignId(getLong(rs, "campaign_id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .templateName(rs.getString("template_name"))
                .languageCode(rs.getString("language_code"))
                .recipientMode(rs.getString("recipient_mode"))
                .currentStatus(rs.getString("current_status"))
                .totalRecipients((Integer) rs.getObject("total_recipients"))
                .sentCount((Integer) rs.getObject("sent_count"))
                .failedCount((Integer) rs.getObject("failed_count"))
                .createdAt(getOffset(rs, "created_at"))
                .lastEventAt(getOffset(rs, "last_event_at"))
                .build();
    }

    private RowMapper<CampaignRecipientResponse> campaignRecipientMapper() {
        return (rs, rowNum) -> CampaignRecipientResponse.builder()
                .id(getLong(rs, "id"))
                .campaignId(getLong(rs, "campaign_id"))
                .contactId(getLong(rs, "contact_id"))
                .waId(rs.getString("wa_id"))
                .phoneNumber(rs.getString("phone_number"))
                .profileName(rs.getString("profile_name"))
                .status(rs.getString("status"))
                .waMessageId(rs.getString("wa_message_id"))
                .errorMessage(rs.getString("error_message"))
                .sentAt(getOffset(rs, "sent_at"))
                .createdAt(getOffset(rs, "created_at"))
                .build();
    }

    private RowMapper<CampaignEventResponse> campaignEventMapper() {
        return (rs, rowNum) -> CampaignEventResponse.builder()
                .id(getLong(rs, "id"))
                .campaignId(getLong(rs, "campaign_id"))
                .eventType(rs.getString("event_type"))
                .status(rs.getString("status"))
                .notes(rs.getString("notes"))
                .metadata(json(rs, "metadata"))
                .createdAt(getOffset(rs, "created_at"))
                .build();
    }

    private MapSqlParameterSource pageParams(int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(0, page);
        return new MapSqlParameterSource()
                .addValue("limit", safeSize, Types.INTEGER)
                .addValue("offset", safePage * safeSize, Types.INTEGER);
    }

    private MapSqlParameterSource paramsWithRange(String from, String to) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        addRange(params, from, to);
        return params;
    }

    private void addRange(MapSqlParameterSource params, String from, String to) {
        params.addValue("fromTs", parseDateTime(from), Types.TIMESTAMP_WITH_TIMEZONE);
        params.addValue("toTs", parseDateTime(to), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private long count(String fromSql, MapSqlParameterSource params) {
        Long total = jdbc.queryForObject("SELECT count(*) " + fromSql, params, Long.class);
        return total == null ? 0 : total;
    }

    private <T> PageResponse<T> page(List<T> rows, int page, int size, long total) {
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(0, page);
        int totalPages = (int) Math.ceil((double) total / (double) safeSize);
        return PageResponse.<T>builder()
                .payload(rows)
                .metadata(PageMetadata.builder()
                        .page(safePage)
                        .size(safeSize)
                        .numberOfElements(rows.size())
                        .totalElements(total)
                        .totalPages(totalPages)
                        .build())
                .build();
    }

    private OffsetDateTime parseDateTime(String value) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        if (text.length() == 10) {
            return LocalDate.parse(text).atStartOfDay().atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(text);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Long getLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Boolean getBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private OffsetDateTime getOffset(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }

    private JsonNode json(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }
}
