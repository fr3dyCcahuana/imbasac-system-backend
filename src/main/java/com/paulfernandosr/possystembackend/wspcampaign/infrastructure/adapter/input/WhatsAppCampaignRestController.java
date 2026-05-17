package com.paulfernandosr.possystembackend.campaign.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.campaign.domain.WhatsAppCampaign;
import com.paulfernandosr.possystembackend.campaign.domain.WhatsAppCampaignPreview;
import com.paulfernandosr.possystembackend.campaign.domain.WhatsAppCampaignRecipient;
import com.paulfernandosr.possystembackend.campaign.infrastructure.adapter.input.dto.WhatsAppCampaignCreateRequest;
import com.paulfernandosr.possystembackend.campaign.infrastructure.adapter.input.dto.WhatsAppCampaignPreviewRequest;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.campaign.application.WhatsAppCampaignService;
import com.paulfernandosr.possystembackend.whatsapp.campaign.domain.*;
import com.paulfernandosr.possystembackend.whatsapp.campaign.infrastructure.adapter.input.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/whatsapp/campaigns")
public class WhatsAppCampaignRestController {
    private final WhatsAppCampaignService campaignService;

    @PostMapping("/preview")
    public ResponseEntity<SuccessResponse<WhatsAppCampaignPreview>> preview(@RequestBody(required = false) WhatsAppCampaignPreviewRequest request) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.preview(request)));
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<WhatsAppCampaign>> create(@Valid @RequestBody WhatsAppCampaignCreateRequest request) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.createCampaign(request)));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<SuccessResponse<WhatsAppCampaign>> start(@PathVariable Long id) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.startCampaign(id)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<WhatsAppCampaign>> get(@PathVariable Long id) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.getCampaign(id)));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<List<WhatsAppCampaign>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.listCampaigns(page, size)));
    }

    @GetMapping("/{id}/recipients")
    public ResponseEntity<SuccessResponse<List<WhatsAppCampaignRecipient>>> recipients(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.getRecipients(id, page, size)));
    }
}
