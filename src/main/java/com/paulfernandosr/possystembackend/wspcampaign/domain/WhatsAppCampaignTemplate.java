package com.paulfernandosr.possystembackend.wspcampaign.domain;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppCampaignTemplate {
    private Long id;
    private String templateName;
    private String languageCode;
    private String category;
    private String status;
    private String bodyText;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
