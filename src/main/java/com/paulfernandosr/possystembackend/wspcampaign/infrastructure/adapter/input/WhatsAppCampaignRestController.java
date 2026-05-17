package com.paulfernandosr.possystembackend.wspcampaign.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.wspcampaign.domain.WhatsAppCampaign;
import com.paulfernandosr.possystembackend.wspcampaign.domain.WhatsAppCampaignPreview;
import com.paulfernandosr.possystembackend.wspcampaign.domain.WhatsAppCampaignRecipient;
import com.paulfernandosr.possystembackend.wspcampaign.domain.WhatsAppCampaignRecipientSupport;
import com.paulfernandosr.possystembackend.wspcampaign.domain.WhatsAppCampaignTemplate;
import com.paulfernandosr.possystembackend.wspcampaign.infrastructure.adapter.input.dto.WhatsAppCampaignCreateRequest;
import com.paulfernandosr.possystembackend.wspcampaign.infrastructure.adapter.input.dto.WhatsAppCampaignPreviewRequest;
import com.paulfernandosr.possystembackend.wspcampaign.infrastructure.adapter.input.dto.WhatsAppCampaignTemplateUpsertRequest;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.wspcampaign.application.WhatsAppCampaignService;
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

    @GetMapping("/templates")
    public ResponseEntity<SuccessResponse<List<WhatsAppCampaignTemplate>>> templates() {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.listTemplates()));
    }

    @PostMapping("/templates")
    public ResponseEntity<SuccessResponse<WhatsAppCampaignTemplate>> upsertTemplate(
            @Valid @RequestBody WhatsAppCampaignTemplateUpsertRequest request
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.upsertTemplate(request)));
    }

    @PostMapping("/preview")
    public ResponseEntity<SuccessResponse<WhatsAppCampaignPreview>> preview(@RequestBody(required = false) WhatsAppCampaignPreviewRequest request) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.preview(request)));
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<WhatsAppCampaign>> create(@Valid @RequestBody WhatsAppCampaignCreateRequest request) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.createCampaign(request)));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<List<WhatsAppCampaign>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.listCampaigns(page, size)));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<SuccessResponse<WhatsAppCampaign>> start(@PathVariable Long id) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.startCampaign(id)));
    }

    @PostMapping("/{id}/retry-auth-failed")
    public ResponseEntity<SuccessResponse<WhatsAppCampaign>> retryAuthFailed(@PathVariable Long id) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.retryAuthFailedCampaign(id)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<WhatsAppCampaign>> get(@PathVariable Long id) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.getCampaign(id)));
    }

    @GetMapping("/{id}/recipients")
    public ResponseEntity<SuccessResponse<List<WhatsAppCampaignRecipient>>> recipients(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.getRecipients(id, page, size)));
    }

    @GetMapping("/{id}/recipients/{recipientId}/support")
    public ResponseEntity<SuccessResponse<WhatsAppCampaignRecipientSupport>> recipientSupport(
            @PathVariable Long id,
            @PathVariable Long recipientId
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(campaignService.getRecipientSupport(id, recipientId)));
    }
}
