package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.input;

import com.fasterxml.jackson.databind.JsonNode;
import com.paulfernandosr.possystembackend.whatsapp.application.WhatsAppIntegrationProperties;
import com.paulfernandosr.possystembackend.whatsapp.domain.port.input.ProcessWhatsAppWebhookUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/whatsapp/webhook")
public class WhatsAppWebhookController {
    private final WhatsAppIntegrationProperties properties;
    private final ProcessWhatsAppWebhookUseCase processWebhookUseCase;

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        boolean valid = "subscribe".equals(mode)
                && properties.getVerifyToken() != null
                && properties.getVerifyToken().equals(verifyToken);

        if (!valid) {
            throw new ResponseStatusException(FORBIDDEN, "Token de verificación WhatsApp inválido.");
        }
        return ResponseEntity.ok(challenge == null ? "" : challenge);
    }

    /**
     * Importante: respondemos 200 a Meta aunque la lógica interna falle,
     * para evitar reintentos que duplican mensajes al cliente.
     */
    @PostMapping
    public ResponseEntity<Void> receiveWebhook(@RequestBody JsonNode payload) {
        log.info("WhatsApp webhook received: {}", payload);
        try {
            processWebhookUseCase.process(payload);
        } catch (Exception exception) {
            log.error("Error procesando webhook WhatsApp. Se responde 200 a Meta para evitar reintentos. error={}", exception.getMessage(), exception);
        }
        return ResponseEntity.ok().build();
    }
}
