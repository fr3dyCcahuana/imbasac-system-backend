package com.paulfernandosr.possystembackend.wspcampaign.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.wspcampaign.domain.*;
import com.paulfernandosr.possystembackend.wspcampaign.infrastructure.adapter.input.dto.WhatsAppCampaignCreateRequest;
import com.paulfernandosr.possystembackend.wspcampaign.infrastructure.adapter.input.dto.WhatsAppCampaignPreviewRequest;
import com.paulfernandosr.possystembackend.wspcampaign.infrastructure.adapter.input.dto.WhatsAppCampaignTargetRequest;
import com.paulfernandosr.possystembackend.wspcampaign.infrastructure.adapter.input.dto.WhatsAppCampaignTemplateUpsertRequest;
import com.paulfernandosr.possystembackend.whatsapp.application.WhatsAppIntegrationProperties;
import com.paulfernandosr.possystembackend.wspcampaign.domain.port.output.WhatsAppCampaignRepository;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppCampaignService {
    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*(\\d+)\\s*}}");

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
        validateTarget(target);
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
        if (request == null) throw new ResponseStatusException(BAD_REQUEST, "Solicitud vacía.");

        var target = normalizeTarget(request.getTarget());
        validateTarget(target);

        String templateName = normalizeTemplateName(request.getTemplateName());
        String languageCode = normalizeLanguage(request.getLanguageCode());
        List<String> bodyParameters = request.getBodyParameters() == null ? List.of() : request.getBodyParameters();

        validateCreateRequest(request, templateName, languageCode, bodyParameters);

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

        String bodyParametersJson = toJson(bodyParameters);
        WhatsAppCampaign campaign = WhatsAppCampaign.builder()
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .status(WhatsAppCampaignStatus.DRAFT)
                .recipientMode(target.getMode())
                .onlyOptedIn(onlyOptedIn)
                .templateName(templateName)
                .languageCode(languageCode)
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

        validateTemplateCatalogOrThrow(
                campaign.getTemplateName(),
                campaign.getLanguageCode(),
                parseBodyParameters(campaign.getBodyParametersJson())
        );

        campaignRepository.updateStatus(campaignId, WhatsAppCampaignStatus.QUEUED, null);
        executor.submit(() -> runCampaign(campaignId));
        return campaignRepository.findCampaignById(campaignId).orElse(campaign);
    }

    @Transactional
    public WhatsAppCampaign retryAuthFailedCampaign(Long campaignId) {
        assertCampaignEnabled();
        WhatsAppCampaign campaign = campaignRepository.findCampaignById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Campaña WhatsApp no encontrada."));

        if (campaign.getStatus() != WhatsAppCampaignStatus.FAILED
                && campaign.getStatus() != WhatsAppCampaignStatus.COMPLETED_WITH_ERRORS) {
            throw new ResponseStatusException(CONFLICT, "Solo puedes reintentar campañas FAILED o COMPLETED_WITH_ERRORS.");
        }

        validateTemplateCatalogOrThrow(
                campaign.getTemplateName(),
                campaign.getLanguageCode(),
                parseBodyParameters(campaign.getBodyParametersJson())
        );

        int reset = campaignRepository.resetAuthFailedRecipientsToPending(campaignId);
        if (reset <= 0) {
            throw new ResponseStatusException(CONFLICT, "No hay destinatarios fallidos por token/autenticación para reintentar.");
        }

        campaignRepository.updateStatus(campaignId, WhatsAppCampaignStatus.QUEUED, null);
        executor.submit(() -> runCampaign(campaignId));
        return campaignRepository.findCampaignById(campaignId).orElse(campaign);
    }

    public List<WhatsAppCampaign> listCampaigns(int page, int size) {
        return campaignRepository.listCampaigns(Math.max(page, 0), normalizeSize(size));
    }

    public List<WhatsAppCampaignTemplate> listTemplates() {
        return campaignRepository.listTemplates();
    }

    @Transactional
    public WhatsAppCampaignTemplate upsertTemplate(WhatsAppCampaignTemplateUpsertRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Solicitud vacía.");
        }

        String templateName = normalizeTemplateName(request.getTemplateName());
        String languageCode = normalizeLanguage(request.getLanguageCode());
        String category = defaultIfBlank(request.getCategory(), "MARKETING").toUpperCase(Locale.ROOT);
        String status = defaultIfBlank(request.getStatus(), "APPROVED").toUpperCase(Locale.ROOT);
        String bodyText = trimToNull(request.getBodyText());

        validateTemplateUpsert(templateName, languageCode, category, status, bodyText);

        WhatsAppCampaignTemplate template = WhatsAppCampaignTemplate.builder()
                .templateName(templateName)
                .languageCode(languageCode)
                .category(category)
                .status(status)
                .bodyText(bodyText)
                .build();

        return campaignRepository.upsertTemplate(template);
    }

    public WhatsAppCampaign getCampaign(Long id) {
        return campaignRepository.findCampaignById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Campaña WhatsApp no encontrada."));
    }

    public List<WhatsAppCampaignRecipient> getRecipients(Long campaignId, int page, int size) {
        getCampaign(campaignId);
        return campaignRepository.findRecipients(campaignId, Math.max(page, 0), normalizeSize(size));
    }

    public WhatsAppCampaignRecipientSupport getRecipientSupport(Long campaignId, Long recipientId) {
        getCampaign(campaignId);
        return campaignRepository.findRecipientSupport(campaignId, recipientId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Destinatario de campaña no encontrado."));
    }

    private void runCampaign(Long campaignId) {
        int sent = 0;
        int failed = 0;
        int skipped = 0;
        String finalError = null;
        boolean stoppedByAuthError = false;
        try {
            WhatsAppCampaign campaign = campaignRepository.findCampaignById(campaignId)
                    .orElseThrow(() -> new IllegalStateException("Campaña no encontrada: " + campaignId));
            validateTemplateCatalogOrThrow(
                    campaign.getTemplateName(),
                    campaign.getLanguageCode(),
                    parseBodyParameters(campaign.getBodyParametersJson())
            );
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

                    log.info(
                            "Enviando campaña WhatsApp. campaignId={}, recipientId={}, waId={}, template={}, language={}, imageUrl={}",
                            campaignId,
                            recipient.getId(),
                            recipient.getWaId(),
                            campaign.getTemplateName(),
                            campaign.getLanguageCode(),
                            campaign.getImageUrl()
                    );

                    WhatsAppMessageSendResult result = gateway.sendMarketingTemplateWithImage(
                            recipient.getWaId(),
                            campaign.getTemplateName(),
                            campaign.getLanguageCode(),
                            campaign.getImageUrl(),
                            bodyParameters
                    );

                    if (result == null) {
                        throw new IllegalStateException("WhatsAppMessageGateway devolvió respuesta vacía. Revisa la implementación de sendMarketingTemplateWithImage.");
                    }

                    persistOutboundTemplate(campaign, recipient, result);
                    campaignRepository.markRecipientSent(recipient.getId(), result.getWaMessageId());
                    sent++;
                    pauseBetweenMessages();
                } catch (WhatsAppCloudApiAuthException ex) {
                    String friendlyError = friendlyGatewayError(
                            ex,
                            campaign.getTemplateName(),
                            campaign.getLanguageCode()
                    );
                    finalError = friendlyError;
                    stoppedByAuthError = true;

                    log.error(
                            "Campaña WhatsApp detenida por token inválido. campaignId={}, recipientId={}, waId={}, errorCode={}, fbtraceId={}, error={}",
                            campaignId,
                            recipient.getId(),
                            recipient.getWaId(),
                            ex.getErrorCode(),
                            ex.getFbtraceId(),
                            friendlyError
                    );

                    campaignRepository.markRecipientFailed(recipient.getId(), friendlyError);
                    failed++;
                    break;
                } catch (Exception ex) {
                    String friendlyError = friendlyGatewayError(
                            ex,
                            campaign.getTemplateName(),
                            campaign.getLanguageCode()
                    );
                    if (finalError == null) {
                        finalError = friendlyError;
                    }
                    log.warn("No se pudo enviar campaña WhatsApp. campaignId={}, recipientId={}, waId={}, error={}",
                            campaignId, recipient.getId(), recipient.getWaId(), friendlyError);
                    campaignRepository.markRecipientFailed(recipient.getId(), friendlyError);
                    failed++;
                }
            }
        } catch (Exception ex) {
            failed++;
            finalError = friendlyGatewayError(ex, null, null);
            log.error("Campaña WhatsApp fallida. campaignId={}, error={}", campaignId, finalError, ex);
        } finally {
            WhatsAppCampaignStatus status;
            if (stoppedByAuthError) {
                status = WhatsAppCampaignStatus.FAILED;
            } else if (sent > 0 && failed == 0) {
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

    private void validateTemplateUpsert(String templateName, String languageCode, String category, String status, String bodyText) {
        if (isBlank(templateName)) {
            throw new ResponseStatusException(BAD_REQUEST, "El nombre técnico de la plantilla es obligatorio.");
        }
        if (!templateName.matches("^[a-z0-9_]{3,180}$")) {
            throw new ResponseStatusException(BAD_REQUEST, "Nombre de plantilla inválido. Usa solo minúsculas, números y guion bajo. Ejemplo: promo_mayo_2026.");
        }
        if (isBlank(languageCode)) {
            throw new ResponseStatusException(BAD_REQUEST, "El idioma de la plantilla es obligatorio.");
        }
        if (!languageCode.matches("^[a-z]{2}([_-][A-Z]{2})?$")) {
            throw new ResponseStatusException(BAD_REQUEST, "Idioma inválido. Usa formatos como es, es_PE o en_US.");
        }
        if (!List.of("MARKETING", "UTILITY", "AUTHENTICATION").contains(category)) {
            throw new ResponseStatusException(BAD_REQUEST, "Categoría inválida. Usa MARKETING, UTILITY o AUTHENTICATION.");
        }
        if (!List.of("APPROVED", "PENDING", "REJECTED", "ACTIVE").contains(status)) {
            throw new ResponseStatusException(BAD_REQUEST, "Estado inválido. Usa APPROVED, PENDING, REJECTED o ACTIVE.");
        }
        if (isBlank(bodyText)) {
            throw new ResponseStatusException(BAD_REQUEST, "El texto del cuerpo de la plantilla es obligatorio.");
        }
    }

    private void validateCreateRequest(WhatsAppCampaignCreateRequest request,
                                       String templateName,
                                       String languageCode,
                                       List<String> bodyParameters) {
        if (isBlank(request.getName())) throw new ResponseStatusException(BAD_REQUEST, "El nombre de campaña es obligatorio.");
        if (isBlank(templateName)) throw new ResponseStatusException(BAD_REQUEST, "La plantilla aprobada de Meta es obligatoria.");
        if (properties.getCampaign().isRequirePolicyConfirmation() && !Boolean.TRUE.equals(request.getConfirmPolicyCompliance())) {
            throw new ResponseStatusException(BAD_REQUEST, "Debes confirmar que los destinatarios aceptaron recibir promociones y que usarás una plantilla aprobada.");
        }
        validateTemplateCatalogOrThrow(templateName, languageCode, bodyParameters);
    }

    private void validateTemplateCatalogOrThrow(String templateName, String languageCode, List<String> bodyParameters) {
        WhatsAppCampaignTemplate template = campaignRepository.findTemplateByNameAndLanguage(templateName, languageCode)
                .orElseThrow(() -> new ResponseStatusException(
                        BAD_REQUEST,
                        "La plantilla '" + templateName + "' no está registrada para el idioma '" + languageCode + "'. " +
                                "Usa el nombre e idioma exactos de una plantilla aprobada en Meta y registrada en whatsapp_templates."
                ));

        String status = template.getStatus();
        if (status != null && !status.isBlank()
                && !"APPROVED".equalsIgnoreCase(status)
                && !"ACTIVE".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "La plantilla '" + templateName + "' existe, pero no está aprobada. Estado actual: " + status + "."
            );
        }

        int expectedVariables = expectedBodyVariables(template.getBodyText());
        int receivedVariables = bodyParameters == null ? 0 : bodyParameters.size();
        if (expectedVariables != receivedVariables) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "La plantilla '" + templateName + "' requiere exactamente " + expectedVariables +
                            " parámetro(s) de cuerpo, pero recibiste " + receivedVariables + ". " +
                            "Corrige los valores dinámicos antes de crear o iniciar la campaña."
            );
        }
    }

    private int expectedBodyVariables(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) return 0;
        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(bodyText);
        int max = 0;
        while (matcher.find()) {
            try {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            } catch (Exception ignored) {
                // Ignorar variables no numéricas.
            }
        }
        return max;
    }

    private void validateTarget(WhatsAppCampaignTargetRequest target) {
        if (target == null) throw new ResponseStatusException(BAD_REQUEST, "Debes definir destinatarios para la campaña.");
        if (target.getMode() == null) target.setMode(WhatsAppCampaignRecipientMode.ONLY_OPTED_IN);

        if (target.getMode() == WhatsAppCampaignRecipientMode.WA_IDS) {
            List<String> normalizedWaIds = target.getWaIds() == null ? List.of() : target.getWaIds().stream()
                    .map(this::normalizeWaId)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .toList();

            if (normalizedWaIds.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "Debes enviar al menos un WhatsApp ID cuando el modo es WA_IDS.");
            }

            for (String waId : normalizedWaIds) {
                if (!waId.matches("\\d{8,20}")) {
                    throw new ResponseStatusException(BAD_REQUEST, "WhatsApp ID inválido: " + waId + ". Usa formato internacional, por ejemplo 51999999999.");
                }
            }
            target.setWaIds(normalizedWaIds);
        }

        if (target.getMode() == WhatsAppCampaignRecipientMode.CONTACT_IDS &&
                (target.getContactIds() == null || target.getContactIds().isEmpty())) {
            throw new ResponseStatusException(BAD_REQUEST, "Debes enviar al menos un contactId cuando el modo es CONTACT_IDS.");
        }
    }

    private WhatsAppCampaignTargetRequest normalizeTarget(WhatsAppCampaignTargetRequest target) {
        WhatsAppCampaignTargetRequest normalized = target == null ? new WhatsAppCampaignTargetRequest() : target;
        if (normalized.getMode() == null) normalized.setMode(WhatsAppCampaignRecipientMode.ONLY_OPTED_IN);
        return normalized;
    }

    private boolean onlyOptedIn(WhatsAppCampaignTargetRequest target) {
        if (target.getMode() == WhatsAppCampaignRecipientMode.WA_IDS) {
            return Boolean.TRUE.equals(target.getOnlyOptedIn());
        }
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

    private String normalizeTemplateName(String value) {
        return value == null ? null : value.trim();
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

    private String friendlyGatewayError(Exception ex, String templateName, String languageCode) {
        String message = ex == null || ex.getMessage() == null ? "Error desconocido al enviar campaña WhatsApp." : ex.getMessage();

        if (ex instanceof WhatsAppCloudApiAuthException
                || message.contains("code=190")
                || message.contains("\"code\":190")
                || message.toLowerCase(Locale.ROOT).contains("authentication error")
                || message.toLowerCase(Locale.ROOT).contains("401 unauthorized")) {
            return "Campaña detenida: el token de WhatsApp Cloud API está inválido, vencido o fue revocado. " +
                    "Actualiza whatsapp.access-token con un token vigente, reinicia el backend y vuelve a iniciar la campaña. " +
                    "Los destinatarios no procesados quedan como PENDING para reintento. Detalle: " + trim(message, 600);
        }

        if (message.contains("#132001") || message.toLowerCase(Locale.ROOT).contains("template name does not exist")) {
            String name = templateName == null ? "la plantilla indicada" : "'" + templateName + "'";
            String language = languageCode == null ? "el idioma indicado" : "'" + languageCode + "'";
            return "Meta rechazó el envío porque " + name + " no existe o no está aprobada en la traducción " + language + ". " +
                    "Corrige el nombre técnico de la plantilla y el idioma antes de reenviar.";
        }

        if (message.contains("#132000") || (message.contains("#100") && message.toLowerCase(Locale.ROOT).contains("parameter"))) {
            return "Meta rechazó el envío porque la cantidad de parámetros no coincide con la plantilla aprobada. " +
                    "Ejemplo: hello_world requiere 0 parámetros. Revisa {{1}}, {{2}}, etc. Detalle: " + trim(message, 600);
        }

        return trim(message, 900);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
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
