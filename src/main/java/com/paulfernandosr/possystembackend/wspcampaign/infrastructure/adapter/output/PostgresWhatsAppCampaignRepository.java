package com.paulfernandosr.possystembackend.wspcampaign.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.wspcampaign.domain.*;
import com.paulfernandosr.possystembackend.wspcampaign.domain.port.output.WhatsAppCampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class PostgresWhatsAppCampaignRepository implements WhatsAppCampaignRepository {
    private final JdbcClient jdbcClient;

    private final RowMapper<WhatsAppCampaign> campaignMapper = (rs, rowNum) -> WhatsAppCampaign.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .status(WhatsAppCampaignStatus.valueOf(rs.getString("status")))
            .recipientMode(WhatsAppCampaignRecipientMode.valueOf(rs.getString("recipient_mode")))
            .onlyOptedIn(rs.getBoolean("only_opted_in"))
            .templateName(rs.getString("template_name"))
            .languageCode(rs.getString("language_code"))
            .imageUrl(rs.getString("image_url"))
            .bodyParametersJson(rs.getString("body_parameters"))
            .totalRecipients(rs.getInt("total_recipients"))
            .sentCount(rs.getInt("sent_count"))
            .failedCount(rs.getInt("failed_count"))
            .skippedCount(rs.getInt("skipped_count"))
            .createdBy(rs.getString("created_by"))
            .startedAt(rs.getObject("started_at", OffsetDateTime.class))
            .completedAt(rs.getObject("completed_at", OffsetDateTime.class))
            .errorMessage(rs.getString("error_message"))
            .createdAt(rs.getObject("created_at", OffsetDateTime.class))
            .updatedAt(rs.getObject("updated_at", OffsetDateTime.class))
            .build();

    private final RowMapper<WhatsAppCampaignRecipient> recipientMapper = (rs, rowNum) -> WhatsAppCampaignRecipient.builder()
            .id(hasColumn(rs, "id") ? rs.getLong("id") : null)
            .campaignId(hasColumn(rs, "campaign_id") ? rs.getObject("campaign_id", Long.class) : null)
            .contactId(rs.getObject("contact_id", Long.class))
            .waId(rs.getString("wa_id"))
            .phoneNumber(rs.getString("phone_number"))
            .profileName(rs.getString("profile_name"))
            .status(hasColumn(rs, "status") ? WhatsAppCampaignRecipientStatus.valueOf(rs.getString("status")) : WhatsAppCampaignRecipientStatus.PENDING)
            .waMessageId(hasColumn(rs, "wa_message_id") ? rs.getString("wa_message_id") : null)
            .errorMessage(hasColumn(rs, "error_message") ? rs.getString("error_message") : null)
            .sentAt(hasColumn(rs, "sent_at") ? rs.getObject("sent_at", OffsetDateTime.class) : null)
            .createdAt(hasColumn(rs, "created_at") ? rs.getObject("created_at", OffsetDateTime.class) : null)
            .updatedAt(hasColumn(rs, "updated_at") ? rs.getObject("updated_at", OffsetDateTime.class) : null)
            .build();


    private final RowMapper<WhatsAppCampaignTemplate> templateMapper = (rs, rowNum) -> WhatsAppCampaignTemplate.builder()
            .id(rs.getLong("id"))
            .templateName(rs.getString("template_name"))
            .languageCode(rs.getString("language_code"))
            .category(rs.getString("category"))
            .status(rs.getString("status"))
            .bodyText(rs.getString("body_text"))
            .createdAt(rs.getObject("created_at", OffsetDateTime.class))
            .updatedAt(rs.getObject("updated_at", OffsetDateTime.class))
            .build();

    private final RowMapper<WhatsAppCampaignRecipientSupport> supportMapper = (rs, rowNum) -> WhatsAppCampaignRecipientSupport.builder()
            .campaignId(rs.getLong("campaign_id"))
            .campaignName(rs.getString("campaign_name"))
            .campaignStatus(rs.getString("campaign_status"))
            .templateName(rs.getString("template_name"))
            .languageCode(rs.getString("language_code"))
            .recipientId(rs.getLong("recipient_id"))
            .recipientStatus(rs.getString("recipient_status"))
            .waId(rs.getString("wa_id"))
            .phoneNumber(rs.getString("phone_number"))
            .profileName(rs.getString("profile_name"))
            .waMessageId(rs.getString("wa_message_id"))
            .recipientErrorMessage(rs.getString("recipient_error_message"))
            .sentAt(rs.getObject("sent_at", OffsetDateTime.class))
            .contactId(rs.getObject("contact_id", Long.class))
            .marketingOptIn(rs.getObject("marketing_opt_in", Boolean.class))
            .marketingOptOutAt(rs.getObject("marketing_opt_out_at", OffsetDateTime.class))
            .conversationId(rs.getObject("conversation_id", Long.class))
            .conversationStatus(rs.getString("conversation_status"))
            .automationMode(rs.getString("automation_mode"))
            .lastMessagePreview(rs.getString("last_message_preview"))
            .lastMessageAt(rs.getObject("last_message_at", OffsetDateTime.class))
            .messageId(rs.getObject("message_id", Long.class))
            .messageStatus(rs.getString("message_status"))
            .messageErrorCode(rs.getString("message_error_code"))
            .messageErrorTitle(rs.getString("message_error_title"))
            .messageErrorDetails(rs.getString("message_error_details"))
            .messageAt(rs.getObject("message_at", OffsetDateTime.class))
            .build();

    private boolean hasColumn(java.sql.ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public WhatsAppCampaign saveCampaign(WhatsAppCampaign campaign) {
        String sql = """
                INSERT INTO whatsapp_campaigns (
                    name, description, status, recipient_mode, only_opted_in,
                    template_name, language_code, image_url, body_parameters,
                    total_recipients, sent_count, failed_count, skipped_count, created_by
                ) VALUES (
                    :name, :description, :status, :recipientMode, :onlyOptedIn,
                    :templateName, :languageCode, :imageUrl, CAST(:bodyParameters AS jsonb),
                    :totalRecipients, 0, 0, 0, :createdBy
                )
                RETURNING *
                """;
        return jdbcClient.sql(sql)
                .param("name", campaign.getName())
                .param("description", campaign.getDescription())
                .param("status", campaign.getStatus().name())
                .param("recipientMode", campaign.getRecipientMode().name())
                .param("onlyOptedIn", Boolean.TRUE.equals(campaign.getOnlyOptedIn()))
                .param("templateName", campaign.getTemplateName())
                .param("languageCode", campaign.getLanguageCode())
                .param("imageUrl", campaign.getImageUrl())
                .param("bodyParameters", campaign.getBodyParametersJson())
                .param("totalRecipients", campaign.getTotalRecipients() == null ? 0 : campaign.getTotalRecipients())
                .param("createdBy", campaign.getCreatedBy())
                .query(campaignMapper)
                .single();
    }

    @Override
    public Optional<WhatsAppCampaign> findCampaignById(Long id) {
        return jdbcClient.sql("SELECT * FROM whatsapp_campaigns WHERE id = :id")
                .param("id", id)
                .query(campaignMapper)
                .optional();
    }

    @Override
    public List<WhatsAppCampaign> listCampaigns(int page, int size) {
        return jdbcClient.sql("""
                SELECT * FROM whatsapp_campaigns
                ORDER BY created_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """)
                .param("limit", size)
                .param("offset", page * size)
                .query(campaignMapper)
                .list();
    }

    @Override
    public long countCampaigns() {
        Long count = jdbcClient.sql("SELECT count(*) FROM whatsapp_campaigns").query(Long.class).single();
        return count == null ? 0 : count;
    }

    @Override
    public void updateStatus(Long campaignId, WhatsAppCampaignStatus status, String errorMessage) {
        jdbcClient.sql("""
                UPDATE whatsapp_campaigns
                   SET status = :status, error_message = :errorMessage, updated_at = now()
                 WHERE id = :id
                """)
                .param("status", status.name())
                .param("errorMessage", errorMessage)
                .param("id", campaignId)
                .update();
    }

    @Override
    public void markStarted(Long campaignId) {
        jdbcClient.sql("""
                UPDATE whatsapp_campaigns
                   SET status = 'RUNNING', started_at = now(), updated_at = now()
                 WHERE id = :id
                """)
                .param("id", campaignId)
                .update();
    }

    @Override
    public void markCompleted(Long campaignId, WhatsAppCampaignStatus status, int sent, int failed, int skipped, String errorMessage) {
        jdbcClient.sql("""
                UPDATE whatsapp_campaigns
                   SET status = :status,
                       sent_count = :sent,
                       failed_count = :failed,
                       skipped_count = :skipped,
                       error_message = :errorMessage,
                       completed_at = now(),
                       updated_at = now()
                 WHERE id = :id
                """)
                .param("status", status.name())
                .param("sent", sent)
                .param("failed", failed)
                .param("skipped", skipped)
                .param("errorMessage", errorMessage)
                .param("id", campaignId)
                .update();
    }

    @Override
    public List<WhatsAppCampaignRecipient> previewRecipients(WhatsAppCampaignRecipientMode mode,
                                                              boolean onlyOptedIn,
                                                              Collection<Long> contactIds,
                                                              Collection<String> waIds,
                                                              int limit) {
        if (mode == WhatsAppCampaignRecipientMode.WA_IDS) {
            return previewWaIdRecipients(waIds, limit);
        }

        String base = """
                SELECT NULL::bigint AS id,
                       NULL::bigint AS campaign_id,
                       c.id AS contact_id,
                       c.wa_id,
                       c.phone_number,
                       c.profile_name,
                       'PENDING' AS status,
                       NULL::varchar AS wa_message_id,
                       NULL::text AS error_message,
                       NULL::timestamptz AS sent_at,
                       NULL::timestamptz AS created_at,
                       NULL::timestamptz AS updated_at
                  FROM whatsapp_contacts c
                 WHERE c.wa_id IS NOT NULL
                   AND c.wa_id <> ''
                   AND c.marketing_opt_out_at IS NULL
                """;
        StringBuilder sql = new StringBuilder(base);
        Map<String, Object> params = new HashMap<>();
        if (onlyOptedIn || mode == WhatsAppCampaignRecipientMode.ONLY_OPTED_IN) {
            sql.append(" AND COALESCE(c.marketing_opt_in, false) = true ");
        }
        if (mode == WhatsAppCampaignRecipientMode.CONTACT_IDS) {
            sql.append(" AND c.id IN (:contactIds) ");
            params.put("contactIds", contactIds == null || contactIds.isEmpty() ? List.of(-1L) : contactIds);
        }
        sql.append(" ORDER BY c.updated_at DESC, c.id DESC LIMIT :limit");
        params.put("limit", limit);

        var spec = jdbcClient.sql(sql.toString());
        for (var entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }
        return spec.query(recipientMapper).list();
    }

    private List<WhatsAppCampaignRecipient> previewWaIdRecipients(Collection<String> waIds, int limit) {
        List<String> normalizedWaIds = waIds == null ? List.of() : waIds.stream()
                .map(this::normalizeWaId)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(Math.max(1, limit))
                .toList();

        if (normalizedWaIds.isEmpty()) {
            return List.of();
        }

        List<WhatsAppCampaignRecipient> existingContacts = jdbcClient.sql("""
                SELECT NULL::bigint AS id,
                       NULL::bigint AS campaign_id,
                       c.id AS contact_id,
                       c.wa_id,
                       c.phone_number,
                       c.profile_name,
                       'PENDING' AS status,
                       NULL::varchar AS wa_message_id,
                       NULL::text AS error_message,
                       NULL::timestamptz AS sent_at,
                       NULL::timestamptz AS created_at,
                       NULL::timestamptz AS updated_at
                  FROM whatsapp_contacts c
                 WHERE c.wa_id IN (:waIds)
                """)
                .param("waIds", normalizedWaIds)
                .query(recipientMapper)
                .list();

        Map<String, WhatsAppCampaignRecipient> byWaId = new HashMap<>();
        for (WhatsAppCampaignRecipient recipient : existingContacts) {
            byWaId.put(normalizeWaId(recipient.getWaId()), recipient);
        }

        List<WhatsAppCampaignRecipient> result = new ArrayList<>();
        for (String waId : normalizedWaIds) {
            WhatsAppCampaignRecipient recipient = byWaId.get(waId);
            if (recipient != null) {
                result.add(recipient);
                continue;
            }
            result.add(WhatsAppCampaignRecipient.builder()
                    .contactId(null)
                    .waId(waId)
                    .phoneNumber(waId)
                    .profileName("Número específico")
                    .status(WhatsAppCampaignRecipientStatus.PENDING)
                    .build());
        }
        return result;
    }

    @Override
    public void replaceRecipients(Long campaignId, List<WhatsAppCampaignRecipient> recipients) {
        jdbcClient.sql("DELETE FROM whatsapp_campaign_recipients WHERE campaign_id = :campaignId")
                .param("campaignId", campaignId)
                .update();
        for (WhatsAppCampaignRecipient recipient : recipients) {
            jdbcClient.sql("""
                    INSERT INTO whatsapp_campaign_recipients (
                        campaign_id, contact_id, wa_id, phone_number, profile_name, status
                    ) VALUES (
                        :campaignId, :contactId, :waId, :phoneNumber, :profileName, 'PENDING'
                    )
                    """)
                    .param("campaignId", campaignId)
                    .param("contactId", recipient.getContactId())
                    .param("waId", recipient.getWaId())
                    .param("phoneNumber", recipient.getPhoneNumber())
                    .param("profileName", recipient.getProfileName())
                    .update();
        }
        jdbcClient.sql("""
                UPDATE whatsapp_campaigns
                   SET total_recipients = :total, updated_at = now()
                 WHERE id = :campaignId
                """)
                .param("total", recipients.size())
                .param("campaignId", campaignId)
                .update();
    }

    @Override
    public List<WhatsAppCampaignRecipient> findPendingRecipients(Long campaignId) {
        return jdbcClient.sql("""
                SELECT * FROM whatsapp_campaign_recipients
                 WHERE campaign_id = :campaignId
                   AND status = 'PENDING'
                 ORDER BY id ASC
                """)
                .param("campaignId", campaignId)
                .query(recipientMapper)
                .list();
    }

    @Override
    public List<WhatsAppCampaignRecipient> findRecipients(Long campaignId, int page, int size) {
        return jdbcClient.sql("""
                SELECT * FROM whatsapp_campaign_recipients
                 WHERE campaign_id = :campaignId
                 ORDER BY id ASC
                 LIMIT :limit OFFSET :offset
                """)
                .param("campaignId", campaignId)
                .param("limit", size)
                .param("offset", page * size)
                .query(recipientMapper)
                .list();
    }

    @Override
    public void markRecipientSent(Long recipientId, String waMessageId) {
        jdbcClient.sql("""
                UPDATE whatsapp_campaign_recipients
                   SET status = 'SENT', wa_message_id = :waMessageId, sent_at = now(), updated_at = now()
                 WHERE id = :id
                """)
                .param("waMessageId", waMessageId)
                .param("id", recipientId)
                .update();
    }

    @Override
    public void markRecipientFailed(Long recipientId, String errorMessage) {
        jdbcClient.sql("""
                UPDATE whatsapp_campaign_recipients
                   SET status = 'FAILED', error_message = :errorMessage, updated_at = now()
                 WHERE id = :id
                """)
                .param("errorMessage", trim(errorMessage, 1800))
                .param("id", recipientId)
                .update();
    }

    @Override
    public void markRecipientSkipped(Long recipientId, String reason) {
        jdbcClient.sql("""
                UPDATE whatsapp_campaign_recipients
                   SET status = 'SKIPPED', error_message = :reason, updated_at = now()
                 WHERE id = :id
                """)
                .param("reason", trim(reason, 1800))
                .param("id", recipientId)
                .update();
    }

    @Override
    public int resetAuthFailedRecipientsToPending(Long campaignId) {
        return jdbcClient.sql("""
                UPDATE whatsapp_campaign_recipients
                   SET status = 'PENDING',
                       wa_message_id = NULL,
                       error_message = NULL,
                       sent_at = NULL,
                       updated_at = now()
                 WHERE campaign_id = :campaignId
                   AND status = 'FAILED'
                   AND (
                        lower(coalesce(error_message, '')) LIKE '%token%'
                     OR lower(coalesce(error_message, '')) LIKE '%401%'
                     OR lower(coalesce(error_message, '')) LIKE '%unauthorized%'
                     OR lower(coalesce(error_message, '')) LIKE '%authentication%'
                     OR lower(coalesce(error_message, '')) LIKE '%autenticación%'
                     OR lower(coalesce(error_message, '')) LIKE '%revocado%'
                   )
                """)
                .param("campaignId", campaignId)
                .update();
    }


    @Override
    public List<WhatsAppCampaignTemplate> listTemplates() {
        return jdbcClient.sql("""
                SELECT id, template_name, language_code, category, status, body_text, created_at, updated_at
                  FROM whatsapp_templates
                 ORDER BY template_name ASC, language_code ASC
                """)
                .query(templateMapper)
                .list();
    }

    @Override
    public WhatsAppCampaignTemplate upsertTemplate(WhatsAppCampaignTemplate template) {
        return jdbcClient.sql("""
                INSERT INTO whatsapp_templates (
                    template_name, language_code, category, status, body_text, created_at, updated_at
                ) VALUES (
                    :templateName, :languageCode, :category, :status, :bodyText, now(), now()
                )
                ON CONFLICT (lower(template_name), language_code)
                DO UPDATE SET
                    category = EXCLUDED.category,
                    status = EXCLUDED.status,
                    body_text = EXCLUDED.body_text,
                    updated_at = now()
                RETURNING id, template_name, language_code, category, status, body_text, created_at, updated_at
                """)
                .param("templateName", template.getTemplateName())
                .param("languageCode", template.getLanguageCode())
                .param("category", template.getCategory())
                .param("status", template.getStatus())
                .param("bodyText", template.getBodyText())
                .query(templateMapper)
                .single();
    }

    @Override
    public Optional<WhatsAppCampaignTemplate> findTemplateByNameAndLanguage(String templateName, String languageCode) {
        return jdbcClient.sql("""
                SELECT id, template_name, language_code, category, status, body_text, created_at, updated_at
                  FROM whatsapp_templates
                 WHERE lower(template_name) = lower(:templateName)
                   AND language_code = :languageCode
                 LIMIT 1
                """)
                .param("templateName", templateName)
                .param("languageCode", languageCode)
                .query(templateMapper)
                .optional();
    }

    @Override
    public Optional<WhatsAppCampaignRecipientSupport> findRecipientSupport(Long campaignId, Long recipientId) {
        return jdbcClient.sql("""
                SELECT camp.id AS campaign_id,
                       camp.name AS campaign_name,
                       camp.status AS campaign_status,
                       camp.template_name,
                       camp.language_code,
                       r.id AS recipient_id,
                       r.status AS recipient_status,
                       r.wa_id,
                       r.phone_number,
                       r.profile_name,
                       r.wa_message_id,
                       r.error_message AS recipient_error_message,
                       r.sent_at,
                       c.id AS contact_id,
                       c.marketing_opt_in,
                       c.marketing_opt_out_at,
                       conv.id AS conversation_id,
                       conv.status AS conversation_status,
                       conv.automation_mode,
                       conv.last_message_preview,
                       conv.last_message_at,
                       msg.id AS message_id,
                       msg.status AS message_status,
                       msg.error_code AS message_error_code,
                       msg.error_title AS message_error_title,
                       msg.error_details AS message_error_details,
                       msg.message_at
                  FROM whatsapp_campaign_recipients r
                  JOIN whatsapp_campaigns camp ON camp.id = r.campaign_id
             LEFT JOIN whatsapp_contacts c
                    ON c.id = r.contact_id
                    OR (r.contact_id IS NULL AND c.wa_id = r.wa_id)
             LEFT JOIN LATERAL (
                    SELECT x.*
                      FROM whatsapp_conversations x
                     WHERE x.contact_id = c.id
                     ORDER BY x.last_message_at DESC NULLS LAST, x.id DESC
                     LIMIT 1
                  ) conv ON true
             LEFT JOIN LATERAL (
                    SELECT m.*
                      FROM whatsapp_messages m
                     WHERE (r.wa_message_id IS NOT NULL AND m.wa_message_id = r.wa_message_id)
                        OR (conv.id IS NOT NULL AND m.conversation_id = conv.id AND m.template_name = camp.template_name)
                     ORDER BY m.message_at DESC NULLS LAST, m.id DESC
                     LIMIT 1
                  ) msg ON true
                 WHERE r.campaign_id = :campaignId
                   AND r.id = :recipientId
                """)
                .param("campaignId", campaignId)
                .param("recipientId", recipientId)
                .query(supportMapper)
                .optional();
    }

    private String normalizeWaId(String value) {
        if (value == null) return null;
        return value.replace("+", "")
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
                .trim();
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
