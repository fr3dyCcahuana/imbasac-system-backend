package com.paulfernandosr.possystembackend.wspcampaign.domain;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppCampaignRecipientSupport {
    private Long campaignId;
    private String campaignName;
    private String campaignStatus;
    private String templateName;
    private String languageCode;

    private Long recipientId;
    private String recipientStatus;
    private String waId;
    private String phoneNumber;
    private String profileName;
    private String waMessageId;
    private String recipientErrorMessage;
    private OffsetDateTime sentAt;

    private Long contactId;
    private Boolean marketingOptIn;
    private OffsetDateTime marketingOptOutAt;

    private Long conversationId;
    private String conversationStatus;
    private String automationMode;
    private String lastMessagePreview;
    private OffsetDateTime lastMessageAt;

    private Long messageId;
    private String messageStatus;
    private String messageErrorCode;
    private String messageErrorTitle;
    private String messageErrorDetails;
    private OffsetDateTime messageAt;
}
