package com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.whatsappcenter.application.WhatsAppCenterAlertService;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/whatsapp-center/account-alerts")
@RequiredArgsConstructor
public class WhatsAppCenterAlertController {
    private final WhatsAppCenterAlertService service;

    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<AccountAlertResponse>>> accountAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.accountAlerts(page, size, severity, status, alertType, from, to)));
    }
}
