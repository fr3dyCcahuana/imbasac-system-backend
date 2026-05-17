package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppConversation {
    private Long id;
    private Long contactId;
    private String waId;
    private String phoneNumber;
    private String profileName;
    private WhatsAppEnums.ConversationStatus status;
    private WhatsAppEnums.AutomationMode automationMode;
    private WhatsAppEnums.ConversationState conversationState;
    private String assignedTo;
    private Long lastProformaId;
    private String lastMessagePreview;
    private OffsetDateTime lastMessageAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
