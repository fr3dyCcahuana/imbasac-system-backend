package com.paulfernandosr.possystembackend.wspcampaign.domain.port.output;

import com.paulfernandosr.possystembackend.wspcampaign.domain.WhatsAppCampaign;
import com.paulfernandosr.possystembackend.wspcampaign.domain.WhatsAppCampaignNotificationResult;
import com.paulfernandosr.possystembackend.wspcampaign.domain.WhatsAppCampaignRecipient;

import java.util.List;

public interface WhatsAppCampaignTraceGateway {
    void recordOutboundTemplate(WhatsAppCampaign campaign,
                                WhatsAppCampaignRecipient recipient,
                                WhatsAppCampaignNotificationResult result,
                                List<String> bodyParameters);
}
