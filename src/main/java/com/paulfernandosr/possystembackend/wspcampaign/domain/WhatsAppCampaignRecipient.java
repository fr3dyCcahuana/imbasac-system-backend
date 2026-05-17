package com.paulfernandosr.possystembackend.wspcampaign.domain;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppCampaignRecipient {
    private Long id;
    private Long campaignId;
    private Long contactId;
    private String waId;
    private String phoneNumber;
    private String profileName;
    private WhatsAppCampaignRecipientStatus status;
    private String waMessageId;
    private String errorMessage;
    private OffsetDateTime sentAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
