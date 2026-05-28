package com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.whatsappcenter.application.WhatsAppCenterWebhookEventService;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/whatsapp-center/webhook-events")
@RequiredArgsConstructor
public class WhatsAppCenterWebhookEventController {
    private final WhatsAppCenterWebhookEventService service;

    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<WebhookEventResponse>>> events(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String eventKind,
            @RequestParam(required = false) String fieldName,
            @RequestParam(required = false) String wabaId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.events(page, size, eventKind, fieldName, wabaId, from, to)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<WebhookEventDetailResponse>> event(@PathVariable Long id) {
        return ResponseEntity.ok(SuccessResponse.ok(service.event(id)));
    }
}
