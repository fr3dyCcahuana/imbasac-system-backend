package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppMessageSendResult {
    private String waMessageId;
    private String status;
    private String rawResponse;
}
