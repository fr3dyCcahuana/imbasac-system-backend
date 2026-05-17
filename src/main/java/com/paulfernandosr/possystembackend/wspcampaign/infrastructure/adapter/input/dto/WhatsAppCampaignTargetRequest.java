package com.paulfernandosr.possystembackend.campaign.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.campaign.domain.WhatsAppCampaignRecipientMode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class WhatsAppCampaignTargetRequest {
    private WhatsAppCampaignRecipientMode mode = WhatsAppCampaignRecipientMode.ONLY_OPTED_IN;
    private Boolean onlyOptedIn;
    private List<Long> contactIds;
    private List<String> waIds;
    private Integer limit;
}
