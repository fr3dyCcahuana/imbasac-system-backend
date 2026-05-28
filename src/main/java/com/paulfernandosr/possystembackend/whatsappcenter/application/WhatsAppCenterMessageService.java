package com.paulfernandosr.possystembackend.whatsappcenter.application;

import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.MessageStatusEventResponse;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.output.PostgresWhatsAppCenterQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WhatsAppCenterMessageService {
    private final PostgresWhatsAppCenterQueryRepository repository;

    public List<MessageStatusEventResponse> statusEvents(String waMessageId) {
        return repository.messageStatusEvents(waMessageId);
    }
}
