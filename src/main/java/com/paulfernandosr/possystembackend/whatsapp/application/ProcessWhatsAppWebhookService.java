package com.paulfernandosr.possystembackend.whatsapp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.model.WhatsAppIncomingCommand;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.input.ProcessWhatsAppWebhookUseCase;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessWhatsAppWebhookService implements ProcessWhatsAppWebhookUseCase {
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    private final ObjectMapper objectMapper;
    private final WhatsAppWebhookEventRepository eventRepository;
    private final WhatsAppContactRepository contactRepository;
    private final WhatsAppConversationRepository conversationRepository;
    private final WhatsAppMessageRepository messageRepository;
    private final InboundWhatsAppAutomationService automationService;

    @Override
    @Transactional
    public void process(JsonNode payload) {
        Long eventId = eventRepository.save(resolveEventType(payload), resolveAnyMessageId(payload), payload.toString());
        try {
            JsonNode entries = payload.path("entry");
            if (entries.isArray()) {
                for (JsonNode entry : entries) {
                    JsonNode changes = entry.path("changes");
                    if (changes.isArray()) {
                        for (JsonNode change : changes) {
                            JsonNode value = change.path("value");
                            processIncomingMessages(value);
                            processStatuses(value);
                        }
                    }
                }
            }
            eventRepository.markProcessed(eventId);
        } catch (Exception exception) {
            // OJO: si esto falla dentro de la misma transacción puede generar 25P02.
            // Por eso el controlador debe responder 200 cuando sea webhook de Meta, aunque se registre el error.
            try {
                eventRepository.markFailed(eventId, shortError(exception.getMessage()));
            } catch (Exception markFailedError) {
                log.warn("No se pudo marcar webhook como failed. eventId={}, error={}", eventId, markFailedError.getMessage());
            }
            throw exception;
        }
    }

    private void processIncomingMessages(JsonNode value) {
        JsonNode messages = value.path("messages");
        if (!messages.isArray()) return;

        for (JsonNode messageNode : messages) {
            String waId = messageNode.path("from").asText(null);
            if (waId == null || waId.isBlank()) continue;

            String waMessageId = messageNode.path("id").asText(null);
            if (waMessageId != null && messageRepository.findByWaMessageId(waMessageId).isPresent()) {
                log.info("WhatsApp webhook duplicado ignorado. waMessageId={}", waMessageId);
                continue;
            }

            String profileName = resolveProfileName(value, waId);
            WhatsAppContact contact = contactRepository.upsertByWaId(waId, waId, profileName);
            WhatsAppConversation conversation = conversationRepository.findOrCreateOpenConversation(contact);

            WhatsAppEnums.MessageType type = resolveType(messageNode.path("type").asText(null));
            WhatsAppIncomingCommand command = WhatsAppIncomingCommand.fromMessage(messageNode);
            String text = command.businessText();
            OffsetDateTime messageAt = resolveTimestamp(messageNode.path("timestamp").asText(null));

            WhatsAppMessage message = WhatsAppMessage.builder()
                    .conversationId(conversation.getId())
                    .contactId(contact.getId())
                    .waMessageId(waMessageId)
                    .direction(WhatsAppEnums.MessageDirection.INBOUND)
                    .type(type)
                    .status(WhatsAppEnums.MessageStatus.RECEIVED)
                    .textBody(text)
                    .mediaId(resolveMediaId(messageNode, type))
                    .mediaMimeType(resolveMediaMimeType(messageNode, type))
                    .mediaSha256(resolveMediaSha256(messageNode, type))
                    .rawPayload(messageNode.toString())
                    .messageAt(messageAt)
                    .build();
            messageRepository.save(message);
            conversationRepository.updateLastMessage(conversation.getId(), preview(text, type), messageAt);

            if (type == WhatsAppEnums.MessageType.TEXT || type == WhatsAppEnums.MessageType.INTERACTIVE) {
                automationService.handleIncomingCommand(conversation, command);
            } else if (type == WhatsAppEnums.MessageType.IMAGE || type == WhatsAppEnums.MessageType.DOCUMENT) {
                automationService.handleIncomingMedia(
                        conversation,
                        type,
                        resolveMediaId(messageNode, type),
                        resolveMediaMimeType(messageNode, type),
                        text
                );
            }
        }
    }

    private void processStatuses(JsonNode value) {
        JsonNode statuses = value.path("statuses");
        if (!statuses.isArray()) return;

        for (JsonNode statusNode : statuses) {
            String waMessageId = statusNode.path("id").asText(null);
            if (waMessageId == null || waMessageId.isBlank()) continue;

            WhatsAppEnums.MessageStatus status = resolveStatus(statusNode.path("status").asText(null));
            JsonNode firstError = statusNode.path("errors").isArray() && statusNode.path("errors").size() > 0
                    ? statusNode.path("errors").get(0)
                    : null;

            String errorCode = firstError == null ? null : firstError.path("code").asText(null);
            String errorTitle = firstError == null ? null : firstError.path("title").asText(null);
            String errorDetails = firstError == null ? null : firstError.path("error_data").path("details").asText(null);
            messageRepository.updateStatus(waMessageId, status, errorCode, errorTitle, errorDetails, statusNode.toString());
        }
    }

    private String resolveProfileName(JsonNode value, String waId) {
        JsonNode contacts = value.path("contacts");
        if (contacts.isArray()) {
            for (JsonNode contact : contacts) {
                if (waId.equals(contact.path("wa_id").asText(null))) {
                    String name = contact.path("profile").path("name").asText(null);
                    if (name != null && !name.isBlank()) return name;
                }
            }
        }
        return null;
    }

    private WhatsAppEnums.MessageType resolveType(String rawType) {
        if (rawType == null) return WhatsAppEnums.MessageType.UNKNOWN;
        try { return WhatsAppEnums.MessageType.valueOf(rawType.toUpperCase()); }
        catch (Exception ignored) { return WhatsAppEnums.MessageType.UNKNOWN; }
    }

    private WhatsAppEnums.MessageStatus resolveStatus(String rawStatus) {
        if (rawStatus == null) return WhatsAppEnums.MessageStatus.QUEUED;
        try { return WhatsAppEnums.MessageStatus.valueOf(rawStatus.toUpperCase()); }
        catch (Exception ignored) { return WhatsAppEnums.MessageStatus.QUEUED; }
    }

    private String resolveMediaId(JsonNode node, WhatsAppEnums.MessageType type) {
        if (type == WhatsAppEnums.MessageType.IMAGE || type == WhatsAppEnums.MessageType.DOCUMENT || type == WhatsAppEnums.MessageType.AUDIO || type == WhatsAppEnums.MessageType.VIDEO) {
            return node.path(type.name().toLowerCase()).path("id").asText(null);
        }
        return null;
    }

    private String resolveMediaMimeType(JsonNode node, WhatsAppEnums.MessageType type) {
        if (type == WhatsAppEnums.MessageType.IMAGE || type == WhatsAppEnums.MessageType.DOCUMENT || type == WhatsAppEnums.MessageType.AUDIO || type == WhatsAppEnums.MessageType.VIDEO) {
            return node.path(type.name().toLowerCase()).path("mime_type").asText(null);
        }
        return null;
    }

    private String resolveMediaSha256(JsonNode node, WhatsAppEnums.MessageType type) {
        if (type == WhatsAppEnums.MessageType.IMAGE || type == WhatsAppEnums.MessageType.DOCUMENT || type == WhatsAppEnums.MessageType.AUDIO || type == WhatsAppEnums.MessageType.VIDEO) {
            return node.path(type.name().toLowerCase()).path("sha256").asText(null);
        }
        return null;
    }

    private OffsetDateTime resolveTimestamp(String timestamp) {
        try {
            long epoch = Long.parseLong(timestamp);
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), LIMA_ZONE);
        } catch (Exception ignored) {
            return OffsetDateTime.now(LIMA_ZONE);
        }
    }

    private String preview(String text, WhatsAppEnums.MessageType type) {
        if (text == null || text.isBlank()) return "[" + type.name().toLowerCase() + "]";
        return text.length() > 180 ? text.substring(0, 180) : text;
    }

    private String shortError(String error) {
        if (error == null) return null;
        return error.length() > 900 ? error.substring(0, 900) : error;
    }

    private String resolveEventType(JsonNode payload) {
        JsonNode changes = payload.path("entry").path(0).path("changes").path(0);
        String field = changes.path("field").asText(null);
        return field == null ? "unknown" : field;
    }

    private String resolveAnyMessageId(JsonNode payload) {
        JsonNode value = payload.path("entry").path(0).path("changes").path(0).path("value");
        JsonNode messages = value.path("messages");
        if (messages.isArray() && messages.size() > 0) return messages.get(0).path("id").asText(null);
        JsonNode statuses = value.path("statuses");
        if (statuses.isArray() && statuses.size() > 0) return statuses.get(0).path("id").asText(null);
        return null;
    }
}
