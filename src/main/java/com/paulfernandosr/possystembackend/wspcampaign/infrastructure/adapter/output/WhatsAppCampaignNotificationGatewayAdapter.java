package com.paulfernandosr.possystembackend.wspcampaign.infrastructure.adapter.output;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppMessageSendResult;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppMessageGateway;
import com.paulfernandosr.possystembackend.wspcampaign.domain.WhatsAppCampaignNotificationResult;
import com.paulfernandosr.possystembackend.wspcampaign.domain.port.output.WhatsAppCampaignNotificationGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WhatsAppCampaignNotificationGatewayAdapter implements WhatsAppCampaignNotificationGateway {
    private final WhatsAppMessageGateway messageGateway;

    @Override
    public WhatsAppCampaignNotificationResult sendTemplateNotification(String toWaId,
                                                                       String templateName,
                                                                       String languageCode,
                                                                       String imageUrl,
                                                                       List<String> bodyParameters) {
        WhatsAppMessageSendResult result = messageGateway.sendTemplateWithImage(
                toWaId,
                templateName,
                languageCode,
                imageUrl,
                bodyParameters
        );
        return WhatsAppCampaignNotificationResult.builder()
                .waMessageId(result == null ? null : result.getWaMessageId())
                .status(result == null ? null : result.getStatus())
                .rawResponse(result == null ? null : result.getRawResponse())
                .build();
    }
}
