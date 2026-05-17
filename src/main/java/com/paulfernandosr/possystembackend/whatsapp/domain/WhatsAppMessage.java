package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppMessage {
    private Long id;
    private Long conversationId;
    private Long contactId;
    private String waMessageId;
    private WhatsAppEnums.MessageDirection direction;
    private WhatsAppEnums.MessageType type;
    private WhatsAppEnums.MessageStatus status;
    private String textBody;
    private String mediaId;
    private String mediaMimeType;
    private String mediaSha256;
    private String templateName;
    private String rawPayload;
    private String errorCode;
    private String errorTitle;
    private String errorDetails;
    private OffsetDateTime messageAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
