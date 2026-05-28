package com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.whatsappcenter.application.WhatsAppCenterMessageService;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.MessageStatusEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/whatsapp-center/messages")
@RequiredArgsConstructor
public class WhatsAppCenterMessageController {
    private final WhatsAppCenterMessageService service;

    @GetMapping("/{waMessageId}/status-events")
    public ResponseEntity<SuccessResponse<List<MessageStatusEventResponse>>> statusEvents(@PathVariable String waMessageId) {
        return ResponseEntity.ok(SuccessResponse.ok(service.statusEvents(waMessageId)));
    }
}
