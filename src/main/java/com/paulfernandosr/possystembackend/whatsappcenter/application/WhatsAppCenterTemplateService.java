package com.paulfernandosr.possystembackend.whatsappcenter.application;

import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.*;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.output.PostgresWhatsAppCenterQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WhatsAppCenterTemplateService {
    private final PostgresWhatsAppCenterQueryRepository repository;

    public PageResponse<TemplateEventResponse> events(int page, int size, String templateName, String eventType, String from, String to) {
        return repository.templateEvents(page, size, templateName, eventType, from, to);
    }
}
