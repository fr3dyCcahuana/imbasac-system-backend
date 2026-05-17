package com.paulfernandosr.possystembackend.wspcampaign.domain.port.output;

import com.paulfernandosr.possystembackend.wspcampaign.domain.WhatsAppCampaignNotificationResult;

import java.util.List;

public interface WhatsAppCampaignNotificationGateway {
    WhatsAppCampaignNotificationResult sendTemplateNotification(
            String toWaId,
            String templateName,
            String languageCode,
            String imageUrl,
            List<String> bodyParameters
    );
}
