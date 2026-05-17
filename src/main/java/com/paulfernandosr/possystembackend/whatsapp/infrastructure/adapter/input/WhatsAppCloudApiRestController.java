package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppCloudApiStatus;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.output.WhatsAppCloudApiHealthGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/whatsapp/cloud-api")
public class WhatsAppCloudApiRestController {
    private final WhatsAppCloudApiHealthGateway healthGateway;

    @GetMapping("/status")
    public ResponseEntity<SuccessResponse<WhatsAppCloudApiStatus>> status() {
        return ResponseEntity.ok(SuccessResponse.ok(healthGateway.checkConnection()));
    }
}
