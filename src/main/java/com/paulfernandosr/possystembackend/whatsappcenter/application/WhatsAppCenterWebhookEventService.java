package com.paulfernandosr.possystembackend.whatsappcenter.application;

import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.*;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.output.PostgresWhatsAppCenterQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WhatsAppCenterWebhookEventService {
    private final PostgresWhatsAppCenterQueryRepository repository;

    public PageResponse<WebhookEventResponse> events(int page, int size, String eventKind, String fieldName, String wabaId, String from, String to) {
        return repository.webhookEvents(page, size, eventKind, fieldName, wabaId, from, to);
    }

    public WebhookEventDetailResponse event(Long id) {
        return repository.webhookEvent(id);
    }
}
