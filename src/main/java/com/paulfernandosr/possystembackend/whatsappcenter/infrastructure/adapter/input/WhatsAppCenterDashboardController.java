package com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.whatsappcenter.application.WhatsAppCenterDashboardService;
import com.paulfernandosr.possystembackend.whatsappcenter.infrastructure.adapter.input.dto.WhatsAppCenterDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/whatsapp-center/dashboard")
@RequiredArgsConstructor
public class WhatsAppCenterDashboardController {
    private final WhatsAppCenterDashboardService service;

    @GetMapping("/summary")
    public ResponseEntity<SuccessResponse<DashboardSummaryResponse>> summary() {
        return ResponseEntity.ok(SuccessResponse.ok(service.summary()));
    }

    @GetMapping("/daily-metrics")
    public ResponseEntity<SuccessResponse<List<DailyMetricResponse>>> dailyMetrics(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return ResponseEntity.ok(SuccessResponse.ok(service.dailyMetrics(from, to)));
    }
}
