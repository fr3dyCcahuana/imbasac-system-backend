package com.paulfernandosr.possystembackend.campaign.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.campaign.domain.*;
import com.paulfernandosr.possystembackend.campaign.infrastructure.adapter.input.dto.WhatsAppCampaignCreateRequest;
import com.paulfernandosr.possystembackend.campaign.infrastructure.adapter.input.dto.WhatsAppCampaignPreviewRequest;
import com.paulfernandosr.possystembackend.campaign.infrastructure.adapter.input.dto.WhatsAppCampaignTargetRequest;
import com.paulfernandosr.possystembackend.whatsapp.application.WhatsAppIntegrationProperties;
import com.paulfernandosr.possystembackend.whatsapp.campaign.domain.*;
import com.paulfernandosr.possystembackend.campaign.domain.port.output.WhatsAppCampaignRepository;
import com.paulfernandosr.possystembackend.whatsapp.campaign.infrastructure.adapter.input.dto.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppCampaignService {
    private final WhatsAppCampaignRepository campaignRepository;
    private final WhatsAppIntegrationProperties properties;
    private final ObjectMapper objectMapper;
    private final WhatsAppMessageGateway gateway;
    private final WhatsAppContactRepository contactRepository;
    private final WhatsAppConversationRepository conversationRepository;
    private final WhatsAppMessageRepository messageRepository;

    /** Un worker sencillo y aislado para no mezclar campañas con el flujo conversacional. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("whatsapp-campaign-worker");
        thread.setDaemon(true);
        return thread;
    });

    public WhatsAppCampaignPreview preview(WhatsAppCampaignPreviewRequest request) {
        var target = normalizeTarget(request == null ? null : request.getTarget());
        int limit = safeLimit(target.getLimit());
        boolean filteredOptIn = onlyOptedIn(target) || target.getMode() == WhatsAppCampaignRecipientMode.ONLY_OPTED_IN;
        List<WhatsAppCampaignRecipient> selected = campaignRepository.previewRecipients(
                target.getMode(),
                filteredOptIn,
                target.getContactIds(),
                target.getWaIds(),
                limit
        );
        return WhatsAppCampaignPreview.builder()
                .selectedRecipients(selected.size())
                .limitedTo(limit)
                .optedInRecipients(filteredOptIn ? selected.size() : 0)
                .notOptedInRecipients(filteredOptIn ? 0 : selected.size())
                .sample(selected.stream().limit(10).toList())
                .build();
    }

    @Transactional
    public WhatsAppCampaign createCampaign(WhatsAppCampaignCreateRequest request) {
        assertCampaignEnabled();
        validateCreateRequest(request);
        var target = normalizeTarget(request.getTarget());
        int limit = safeLimit(target.getLimit());
        boolean onlyOptedIn = onlyOptedIn(target);
        List<WhatsAppCampaignRecipient> recipients = campaignRepository.previewRecipients(
                target.getMode(),
                onlyOptedIn,
                target.getContactIds(),
                target.getWaIds(),
                limit
        );
        if (recipients.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "No hay destinatarios para la campaña.");
        }

        String bodyParametersJson = toJson(request.getBodyParameters() == null ? List.of() : request.getBodyParameters());
        WhatsAppCampaign campaign = WhatsAppCampaign.builder()
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .status(WhatsAppCampaignStatus.DRAFT)
                .recipientMode(target.getMode())
                .onlyOptedIn(onlyOptedIn)
                .templateName(request.getTemplateName().trim())
                .languageCode(normalizeLanguage(request.getLanguageCode()))
                .imageUrl(trimToNull(request.getImageUrl()))
                .bodyParametersJson(bodyParametersJson)
                .totalRecipients(recipients.size())
                .createdBy(trimToNull(request.getCreatedBy()) == null ? "SYSTEM" : request.getCreatedBy().trim())
                .build();
        WhatsAppCampaign saved = campaignRepository.saveCampaign(campaign);
        campaignRepository.replaceRecipients(saved.getId(), recipients);
        return campaignRepository.findCampaignById(saved.getId()).orElse(saved);
    }

    public WhatsAppCampaign startCampaign(Long campaignId) {
        assertCampaignEnabled();
        WhatsAppCampaign campaign = campaignRepository.findCampaignById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Campaña WhatsApp no encontrada."));
        if (campaign.getStatus() != WhatsAppCampaignStatus.DRAFT && campaign.getStatus() != WhatsAppCampaignStatus.QUEUED) {
            throw new ResponseStatusException(CONFLICT, "La campaña no está en estado DRAFT/QUEUED.");
        }
        campaignRepository.updateStatus(campaignId, WhatsAppCampaignStatus.QUEUED, null);
        executor.submit(() -> runCampaign(campaignId));
        return campaignRepository.findCampaignById(campaignId).orElse(campaign);
    }

    public List<WhatsAppCampaign> listCampaigns(int page, int size) {
        return campaignRepository.listCampaigns(Math.max(page, 0), normalizeSize(size));
    }

    public WhatsAppCampaign getCampaign(Long id) {
        return campaignRepository.findCampaignById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Campaña WhatsApp no encontrada."));
    }

    public List<WhatsAppCampaignRecipient> getRecipients(Long campaignId, int page, int size) {
        getCampaign(campaignId);
        return campaignRepository.findRecipients(campaignId, Math.max(page, 0), normalizeSize(size));
    }

    private void runCampaign(Long campaignId) {
        int sent = 0;
        int failed = 0;
        int skipped = 0;
        String finalError = null;
        try {
            WhatsAppCampaign campaign = campaignRepository.findCampaignById(campaignId)
                    .orElseThrow(() -> new IllegalStateException("Campaña no encontrada: " + campaignId));
            campaignRepository.markStarted(campaignId);
            List<WhatsAppCampaignRecipient> recipients = campaignRepository.findPendingRecipients(campaignId);
            List<String> bodyParameters = parseBodyParameters(campaign.getBodyParametersJson());

            for (WhatsAppCampaignRecipient recipient : recipients) {
                try {
                    if (recipient.getWaId() == null || recipient.getWaId().isBlank()) {
                        campaignRepository.markRecipientSkipped(recipient.getId(), "Contacto sin wa_id.");
                        skipped++;
                        continue;
                    }
                    WhatsAppMessageSendResult result = gateway.sendMarketingTemplateWithImage(
                            recipient.getWaId(),
                            campaign.getTemplateName(),
                            campaign.getLanguageCode(),
                            campaign.getImageUrl(),
                            bodyParameters
                    );
                    persistOutboundTemplate(campaign, recipient, result);
                    campaignRepository.markRecipientSent(recipient.getId(), result.getWaMessageId());
                    sent++;
                    pauseBetweenMessages();
                } catch (Exception ex) {
                    log.warn("No se pudo enviar campaña WhatsApp. campaignId={}, recipientId={}, waId={}, error={}",
                            campaignId, recipient.getId(), recipient.getWaId(), ex.getMessage());
                    campaignRepository.markRecipientFailed(recipient.getId(), ex.getMessage());
                    failed++;
                }
            }
        } catch (Exception ex) {
            failed++;
            finalError = ex.getMessage();
            log.error("Campaña WhatsApp fallida. campaignId={}", campaignId, ex);
        } finally {
            WhatsAppCampaignStatus status;
            if (sent > 0 && failed == 0) {
                status = WhatsAppCampaignStatus.COMPLETED;
            } else if (sent > 0) {
                status = WhatsAppCampaignStatus.COMPLETED_WITH_ERRORS;
            } else {
                status = WhatsAppCampaignStatus.FAILED;
            }
            campaignRepository.markCompleted(campaignId, status, sent, failed, skipped, finalError);
        }
    }

    private void persistOutboundTemplate(WhatsAppCampaign campaign,
                                         WhatsAppCampaignRecipient recipient,
                                         WhatsAppMessageSendResult result) {
        WhatsAppContact contact = contactRepository.upsertByWaId(recipient.getWaId(), recipient.getPhoneNumber(), recipient.getProfileName());
        WhatsAppConversation conversation = conversationRepository.findOrCreateOpenConversation(contact);
        String preview = "Campaña: " + campaign.getName() + " | Plantilla: " + campaign.getTemplateName();
        WhatsAppMessage message = WhatsAppMessage.builder()
                .conversationId(conversation.getId())
                .contactId(conversation.getContactId())
                .waMessageId(result.getWaMessageId())
                .direction(WhatsAppEnums.MessageDirection.OUTBOUND)
                .type(WhatsAppEnums.MessageType.TEMPLATE)
                .status(parseGatewayStatus(result.getStatus()))
                .textBody(preview)
                .templateName(campaign.getTemplateName())
                .rawPayload(result.getRawResponse())
                .messageAt(OffsetDateTime.now())
                .build();
        messageRepository.save(message);
        conversationRepository.updateLastMessage(conversation.getId(), preview, OffsetDateTime.now());
    }

    private WhatsAppEnums.MessageStatus parseGatewayStatus(String status) {
        if (status == null) return WhatsAppEnums.MessageStatus.ACCEPTED;
        try {
            return WhatsAppEnums.MessageStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return WhatsAppEnums.MessageStatus.ACCEPTED;
        }
    }

    private void pauseBetweenMessages() {
        long delay = Math.max(0, properties.getCampaign().getDelayMillisBetweenMessages());
        if (delay <= 0) return;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void assertCampaignEnabled() {
        if (!properties.getCampaign().isEnabled()) {
            throw new ResponseStatusException(FORBIDDEN, "Campañas WhatsApp deshabilitadas por configuración.");
        }
    }

    private void validateCreateRequest(WhatsAppCampaignCreateRequest request) {
        if (request == null) throw new ResponseStatusException(BAD_REQUEST, "Solicitud vacía.");
        if (isBlank(request.getName())) throw new ResponseStatusException(BAD_REQUEST, "El nombre de campaña es obligatorio.");
        if (isBlank(request.getTemplateName())) throw new ResponseStatusException(BAD_REQUEST, "La plantilla aprobada de Meta es obligatoria.");
        if (properties.getCampaign().isRequirePolicyConfirmation() && !Boolean.TRUE.equals(request.getConfirmPolicyCompliance())) {
            throw new ResponseStatusException(BAD_REQUEST, "Debes confirmar que los destinatarios aceptaron recibir promociones y que usarás una plantilla aprobada.");
        }
    }

    private WhatsAppCampaignTargetRequest normalizeTarget(WhatsAppCampaignTargetRequest target) {
        WhatsAppCampaignTargetRequest normalized = target == null ? new WhatsAppCampaignTargetRequest() : target;
        if (normalized.getMode() == null) normalized.setMode(WhatsAppCampaignRecipientMode.ONLY_OPTED_IN);
        return normalized;
    }

    private boolean onlyOptedIn(WhatsAppCampaignTargetRequest target) {
        if (target.getOnlyOptedIn() != null) return target.getOnlyOptedIn();
        return properties.getCampaign().isOnlyOptedInByDefault() || target.getMode() == WhatsAppCampaignRecipientMode.ONLY_OPTED_IN;
    }

    private int safeLimit(Integer requested) {
        int max = Math.max(1, properties.getCampaign().getMaxRecipientsPerCampaign());
        if (requested == null || requested <= 0) return max;
        return Math.min(requested, max);
    }

    private int normalizeSize(int size) {
        if (size <= 0) return 20;
        return Math.min(size, 100);
    }

    private String normalizeLanguage(String value) {
        return isBlank(value) ? "es" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "No se pudieron serializar parámetros de plantilla.", ex);
        }
    }

    private List<String> parseBodyParameters(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

}
