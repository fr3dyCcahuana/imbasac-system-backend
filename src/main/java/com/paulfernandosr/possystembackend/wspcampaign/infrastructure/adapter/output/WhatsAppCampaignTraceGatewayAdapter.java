package com.paulfernandosr.possystembackend.wspcampaign.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppContactRepository;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppConversationRepository;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppMessageRepository;
import com.paulfernandosr.possystembackend.wspcampaign.domain.WhatsAppCampaign;
import com.paulfernandosr.possystembackend.wspcampaign.domain.WhatsAppCampaignNotificationResult;
import com.paulfernandosr.possystembackend.wspcampaign.domain.WhatsAppCampaignRecipient;
import com.paulfernandosr.possystembackend.wspcampaign.domain.port.output.WhatsAppCampaignTraceGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class WhatsAppCampaignTraceGatewayAdapter implements WhatsAppCampaignTraceGateway {
    private final WhatsAppContactRepository contactRepository;
    private final WhatsAppConversationRepository conversationRepository;
    private final WhatsAppMessageRepository messageRepository;

    @Override
    public void recordOutboundTemplate(WhatsAppCampaign campaign,
                                       WhatsAppCampaignRecipient recipient,
                                       WhatsAppCampaignNotificationResult result,
                                       List<String> bodyParameters) {
        WhatsAppContact contact = contactRepository.upsertByWaId(
                recipient.getWaId(),
                firstNonBlank(recipient.getPhoneNumber(), recipient.getWaId()),
                recipient.getProfileName()
        );
        WhatsAppConversation conversation = conversationRepository.findOrCreateOpenConversation(contact);
        OffsetDateTime now = OffsetDateTime.now();
        String preview = buildPreview(campaign, bodyParameters);

        WhatsAppMessage message = WhatsAppMessage.builder()
                .conversationId(conversation.getId())
                .contactId(contact.getId())
                .waMessageId(result == null ? null : result.getWaMessageId())
                .direction(WhatsAppEnums.MessageDirection.OUTBOUND)
                .type(WhatsAppEnums.MessageType.TEMPLATE)
                .status(parseStatus(result == null ? null : result.getStatus()))
                .textBody(preview)
                .templateName(campaign.getTemplateName())
                .rawPayload(result == null ? null : result.getRawResponse())
                .messageAt(now)
                .build();
        messageRepository.save(message);
        conversationRepository.updateLastMessage(conversation.getId(), preview, now);
    }

    private WhatsAppEnums.MessageStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return WhatsAppEnums.MessageStatus.ACCEPTED;
        try {
            return WhatsAppEnums.MessageStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return WhatsAppEnums.MessageStatus.ACCEPTED;
        }
    }

    private String buildPreview(WhatsAppCampaign campaign, List<String> bodyParameters) {
        String params = bodyParameters == null || bodyParameters.isEmpty() ? "" : " | Params: " + String.join(" / ", bodyParameters);
        String value = "Campaña: " + campaign.getName() + " | Plantilla: " + campaign.getTemplateName() + params;
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
