package com.paulfernandosr.possystembackend.whatsappcenter.application;

import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.*;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.output.PostgresWhatsAppCenterQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WhatsAppCenterCampaignService {
    private final PostgresWhatsAppCenterQueryRepository repository;

    public PageResponse<CampaignResponse> campaigns(int page, int size, String q, String status, String from, String to) {
        return repository.campaigns(page, size, q, status, from, to);
    }

    public CampaignResponse campaign(Long campaignId) {
        return repository.campaign(campaignId);
    }

    public PageResponse<CampaignRecipientResponse> recipients(Long campaignId, int page, int size) {
        return repository.campaignRecipients(campaignId, page, size);
    }

    public PageResponse<CampaignEventResponse> events(Long campaignId, int page, int size) {
        return repository.campaignEvents(campaignId, page, size);
    }
}
