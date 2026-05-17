package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppContact {
    private Long id;
    private String waId;
    private String phoneNumber;
    private String profileName;
    private Long customerId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
