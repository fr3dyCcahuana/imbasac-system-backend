package com.paulfernandosr.possystembackend.whatsapp.application;

import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.input.SendWhatsAppMessageUseCase;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.*;
import com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output.MetaWhatsAppMessageGateway.InteractiveButton;
import com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.output.MetaWhatsAppMessageGateway.InteractiveListRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class SendWhatsAppMessageService implements SendWhatsAppMessageUseCase {
    private final WhatsAppConversationRepository conversationRepository;
    private final WhatsAppContactRepository contactRepository;
    private final WhatsAppMessageRepository messageRepository;
    private final WhatsAppMessageGateway gateway;
    private final WhatsAppIntegrationProperties properties;

    @Override
    @Transactional
    public WhatsAppMessage sendTextToConversation(Long conversationId, String body) {
        WhatsAppConversation conversation = getConversation(conversationId);
        WhatsAppMessageSendResult result = sendText(conversation.getWaId(), body);
        return persistOutbound(conversation, body, WhatsAppEnums.MessageType.TEXT, result, null);
    }

    @Transactional
    public WhatsAppMessage sendButtonsToConversation(Long conversationId, String body, List<InteractiveButton> buttons) {
        WhatsAppConversation conversation = getConversation(conversationId);
        WhatsAppMessageSendResult result = sendButtons(conversation.getWaId(), body, buttons);
        return persistOutbound(conversation, body, WhatsAppEnums.MessageType.INTERACTIVE, result, null);
    }

    @Transactional
    public WhatsAppMessage sendButtonsWithImageHeaderToConversation(Long conversationId, String body, String imageUrl, List<InteractiveButton> buttons) {
        WhatsAppConversation conversation = getConversation(conversationId);
        WhatsAppMessageSendResult result = sendButtonsWithImageHeader(conversation.getWaId(), body, imageUrl, buttons);
        return persistOutbound(conversation, body, WhatsAppEnums.MessageType.INTERACTIVE, result, null);
    }

    @Transactional
    public WhatsAppMessage sendListToConversation(Long conversationId, String body, String buttonText, String sectionTitle, List<InteractiveListRow> rows) {
        WhatsAppConversation conversation = getConversation(conversationId);
        WhatsAppMessageSendResult result = sendList(conversation.getWaId(), body, buttonText, sectionTitle, rows);
        return persistOutbound(conversation, body, WhatsAppEnums.MessageType.INTERACTIVE, result, null);
    }

    @Transactional
    public WhatsAppMessage sendImageToConversation(Long conversationId, String imageUrl, String caption) {
        WhatsAppConversation conversation = getConversation(conversationId);
        WhatsAppMessageSendResult result = sendImage(conversation.getWaId(), imageUrl, caption);
        String preview = caption == null || caption.isBlank() ? imageUrl : caption;
        return persistOutbound(conversation, preview, WhatsAppEnums.MessageType.IMAGE, result, null);
    }

    @Override
    @Transactional
    public WhatsAppMessage sendTextToWaId(String waId, String body) {
        WhatsAppContact contact = contactRepository.upsertByWaId(waId, waId, null);
        WhatsAppConversation conversation = conversationRepository.findOrCreateOpenConversation(contact);
        WhatsAppMessageSendResult result = sendText(waId, body);
        return persistOutbound(conversation, body, WhatsAppEnums.MessageType.TEXT, result, null);
    }

    @Override
    @Transactional
    public WhatsAppMessage sendTemplateToWaId(String waId, String templateName, String languageCode) {
        WhatsAppContact contact = contactRepository.upsertByWaId(waId, waId, null);
        WhatsAppConversation conversation = conversationRepository.findOrCreateOpenConversation(contact);
        WhatsAppMessageSendResult result = sendTemplate(waId, templateName, languageCode);
        return persistOutbound(conversation, "Plantilla: " + templateName, WhatsAppEnums.MessageType.TEMPLATE, result, templateName);
    }


    private WhatsAppMessageSendResult sendText(String waId, String body) {
        if (isSimulationWaId(waId)) return simulationResult("text", body);
        return gateway.sendText(waId, body);
    }

    private WhatsAppMessageSendResult sendButtons(String waId, String body, List<InteractiveButton> buttons) {
        if (isSimulationWaId(waId)) return simulationResult("buttons", body);
        return gateway.sendButtons(waId, body, buttons);
    }

    private WhatsAppMessageSendResult sendButtonsWithImageHeader(String waId, String body, String imageUrl, List<InteractiveButton> buttons) {
        if (isSimulationWaId(waId)) return simulationResult("image-buttons", body + " | " + imageUrl);
        return gateway.sendButtonsWithImageHeader(waId, body, imageUrl, buttons);
    }

    private WhatsAppMessageSendResult sendList(String waId, String body, String buttonText, String sectionTitle, List<InteractiveListRow> rows) {
        if (isSimulationWaId(waId)) return simulationResult("list", body);
        return gateway.sendList(waId, body, buttonText, sectionTitle, rows);
    }

    private WhatsAppMessageSendResult sendImage(String waId, String imageUrl, String caption) {
        if (isSimulationWaId(waId)) return simulationResult("image", (caption == null ? "" : caption) + " | " + imageUrl);
        return gateway.sendImageByLink(waId, imageUrl, caption);
    }

    private WhatsAppMessageSendResult sendTemplate(String waId, String templateName, String languageCode) {
        if (isSimulationWaId(waId)) return simulationResult("template", templateName);
        return gateway.sendTemplate(waId, templateName, languageCode);
    }

    private boolean isSimulationWaId(String waId) {
        return properties != null && properties.getDebug() != null && properties.getDebug().isSimulationWaId(waId);
    }

    private WhatsAppMessageSendResult simulationResult(String type, String preview) {
        return WhatsAppMessageSendResult.builder()
                .waMessageId("sim-" + type + "-" + System.nanoTime())
                .status("ACCEPTED")
                .rawResponse("{\"simulation\":true,\"type\":\"" + type + "\"}")
                .build();
    }

    private WhatsAppConversation getConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Conversación WhatsApp no encontrada."));
    }

    private WhatsAppMessage persistOutbound(WhatsAppConversation conversation,
                                            String preview,
                                            WhatsAppEnums.MessageType type,
                                            WhatsAppMessageSendResult result,
                                            String templateName) {
        OffsetDateTime now = OffsetDateTime.now();
        WhatsAppMessage message = WhatsAppMessage.builder()
                .conversationId(conversation.getId())
                .contactId(conversation.getContactId())
                .waMessageId(result.getWaMessageId())
                .direction(WhatsAppEnums.MessageDirection.OUTBOUND)
                .type(type)
                .status(parseGatewayStatus(result.getStatus()))
                .textBody(preview)
                .templateName(templateName)
                .rawPayload(result.getRawResponse())
                .messageAt(now)
                .build();
        WhatsAppMessage saved = messageRepository.save(message);
        conversationRepository.updateLastMessage(conversation.getId(), preview(preview), now);
        return saved;
    }

    private String preview(String value) {
        if (value == null) return null;
        return value.length() > 250 ? value.substring(0, 250) : value;
    }

    private WhatsAppEnums.MessageStatus parseGatewayStatus(String status) {
        if (status == null) return WhatsAppEnums.MessageStatus.QUEUED;
        try {
            return WhatsAppEnums.MessageStatus.valueOf(status.toUpperCase());
        } catch (Exception ignored) {
            return WhatsAppEnums.MessageStatus.ACCEPTED;
        }
    }
}
