package com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.whatsappcenter.application.WhatsAppCenterCampaignService;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/whatsapp-center/campaigns")
@RequiredArgsConstructor
public class WhatsAppCenterCampaignController {
    private final WhatsAppCenterCampaignService service;

    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<CampaignResponse>>> campaigns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.campaigns(page, size, q, status, from, to)));
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<SuccessResponse<CampaignResponse>> campaign(@PathVariable Long campaignId) {
        return ResponseEntity.ok(SuccessResponse.ok(service.campaign(campaignId)));
    }

    @GetMapping("/{campaignId}/recipients")
    public ResponseEntity<SuccessResponse<PageResponse<CampaignRecipientResponse>>> recipients(
            @PathVariable Long campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.recipients(campaignId, page, size)));
    }

    @GetMapping("/{campaignId}/events")
    public ResponseEntity<SuccessResponse<PageResponse<CampaignEventResponse>>> events(
            @PathVariable Long campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.events(campaignId, page, size)));
    }
}
