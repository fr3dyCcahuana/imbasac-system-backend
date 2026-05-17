package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppCart {
    private Long id;
    private Long conversationId;
    private WhatsAppEnums.CartStatus status;
    private Long currentProductSuggestionId;
    private String customerDocumentType;
    private String customerDocumentNumber;
    private String customerName;
    private Long proformaId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
