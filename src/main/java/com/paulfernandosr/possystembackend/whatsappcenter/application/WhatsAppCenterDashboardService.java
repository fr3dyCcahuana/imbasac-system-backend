package com.paulfernandosr.possystembackend.whatsappcenter.application;

import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.*;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.output.PostgresWhatsAppCenterQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WhatsAppCenterDashboardService {
    private final PostgresWhatsAppCenterQueryRepository repository;

    public DashboardSummaryResponse summary() {
        return repository.dashboardSummary();
    }

    public List<DailyMetricResponse> dailyMetrics(String from, String to) {
        return repository.dailyMetrics(from, to);
    }
}
