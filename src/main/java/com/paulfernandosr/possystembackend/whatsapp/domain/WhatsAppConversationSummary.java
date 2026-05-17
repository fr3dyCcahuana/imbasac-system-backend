package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppConversationSummary {
    private Long id;
    private Long contactId;
    private String waId;
    private String phoneNumber;
    private String profileName;
    private String status;
    private String automationMode;
    private String assignedTo;
    private Long lastProformaId;
    private String lastMessagePreview;
    private OffsetDateTime lastMessageAt;
    private long unreadInboundCount;
}
