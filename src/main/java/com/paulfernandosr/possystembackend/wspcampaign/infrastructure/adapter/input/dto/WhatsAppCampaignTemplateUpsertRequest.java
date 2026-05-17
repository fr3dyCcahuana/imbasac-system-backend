package com.paulfernandosr.possystembackend.wspcampaign.infrastructure.adapter.input.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WhatsAppCampaignTemplateUpsertRequest {
    private String templateName;
    private String languageCode;
    private String category;
    private String status;
    private String bodyText;
}
