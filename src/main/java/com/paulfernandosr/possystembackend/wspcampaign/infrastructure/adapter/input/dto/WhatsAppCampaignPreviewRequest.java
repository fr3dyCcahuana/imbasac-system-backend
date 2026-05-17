package com.paulfernandosr.possystembackend.campaign.infrastructure.adapter.input.dto;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WhatsAppCampaignPreviewRequest {
    @Valid
    private WhatsAppCampaignTargetRequest target = new WhatsAppCampaignTargetRequest();
}
