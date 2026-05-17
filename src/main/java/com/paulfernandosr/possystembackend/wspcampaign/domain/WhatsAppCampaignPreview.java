package com.paulfernandosr.possystembackend.campaign.domain;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppCampaignPreview {
    private int totalContacts;
    private int selectedRecipients;
    private int optedInRecipients;
    private int notOptedInRecipients;
    private int limitedTo;
    private List<WhatsAppCampaignRecipient> sample;
}
