package com.paulfernandosr.possystembackend.wspcampaign.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppCampaignNotificationResult {
    private String waMessageId;
    private String status;
    private String rawResponse;
}
