package com.paulfernandosr.possystembackend.campaign.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.campaign.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.campaign.domain.*;
import com.paulfernandosr.possystembackend.campaign.domain.port.output.WhatsAppCampaignRepository;
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
        if (mode == WhatsAppCampaignRecipientMode.WA_IDS) {
            sql.append(" AND c.wa_id IN (:waIds) ");
            params.put("waIds", waIds == null || waIds.isEmpty() ? List.of("__none__") : waIds);
        }
        sql.append(" ORDER BY c.updated_at DESC, c.id DESC LIMIT :limit");
        params.put("limit", limit);

        var spec = jdbcClient.sql(sql.toString());
        for (var entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }
        return spec.query(recipientMapper).list();
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

    private String trim(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
