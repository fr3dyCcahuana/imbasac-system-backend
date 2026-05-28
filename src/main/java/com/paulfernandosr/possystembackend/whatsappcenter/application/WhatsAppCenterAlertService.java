package com.paulfernandosr.possystembackend.whatsappcenter.application;

import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.*;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.output.PostgresWhatsAppCenterQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WhatsAppCenterAlertService {
    private final PostgresWhatsAppCenterQueryRepository repository;

    public PageResponse<AccountAlertResponse> accountAlerts(int page, int size, String severity, String status, String alertType, String from, String to) {
        return repository.accountAlerts(page, size, severity, status, alertType, from, to);
    }
}
