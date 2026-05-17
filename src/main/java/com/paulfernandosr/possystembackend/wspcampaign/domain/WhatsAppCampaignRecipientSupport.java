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
    private Long recipientId;
    private String waId;
    private String phoneNumber;
    private String profileName;
    private WhatsAppCampaignRecipientStatus recipientStatus;
    private String waMessageId;
    private String recipientErrorMessage;
    private OffsetDateTime recipientSentAt;

    private Long contactId;
    private String contactProfileName;
    private Boolean marketingOptIn;
    private OffsetDateTime marketingOptInAt;
    private OffsetDateTime marketingOptOutAt;

    private Long conversationId;
    private String conversationStatus;
    private String automationMode;
    private String conversationState;
    private String assignedTo;
    private String lastMessagePreview;
    private OffsetDateTime lastMessageAt;

    private Long messageId;
    private String messageStatus;
    private String messageType;
    private String templateName;
    private String errorCode;
    private String errorTitle;
    private String errorDetails;
    private OffsetDateTime messageAt;
}
