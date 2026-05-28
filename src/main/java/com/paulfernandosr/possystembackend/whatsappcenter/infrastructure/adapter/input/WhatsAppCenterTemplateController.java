package com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.whatsappcenter.application.WhatsAppCenterTemplateService;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/whatsapp-center/templates")
@RequiredArgsConstructor
public class WhatsAppCenterTemplateController {
    private final WhatsAppCenterTemplateService service;

    @GetMapping("/events")
    public ResponseEntity<SuccessResponse<PageResponse<TemplateEventResponse>>> events(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String templateName,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.events(page, size, templateName, eventType, from, to)));
    }
}
