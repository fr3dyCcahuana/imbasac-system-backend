package com.paulfernandosr.possystembackend.campaign.domain;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppCampaign {
    private Long id;
    private String name;
    private String description;
    private WhatsAppCampaignStatus status;
    private WhatsAppCampaignRecipientMode recipientMode;
    private Boolean onlyOptedIn;
    private String templateName;
    private String languageCode;
    private String imageUrl;
    private String bodyParametersJson;
    private Integer totalRecipients;
    private Integer sentCount;
    private Integer failedCount;
    private Integer skippedCount;
    private String createdBy;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
