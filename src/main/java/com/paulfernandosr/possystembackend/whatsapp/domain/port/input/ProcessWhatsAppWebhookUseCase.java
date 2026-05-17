package com.paulfernandosr.possystembackend.whatsapp.domain.port.input;

import com.fasterxml.jackson.databind.JsonNode;

public interface ProcessWhatsAppWebhookUseCase {
    void process(JsonNode payload);
}
