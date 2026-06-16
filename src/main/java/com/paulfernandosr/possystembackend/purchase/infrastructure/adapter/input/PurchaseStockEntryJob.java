package com.paulfernandosr.possystembackend.purchase.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.purchase.domain.port.output.PurchaseRepository;
import com.paulfernandosr.possystembackend.purchase.application.PurchaseStockEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class PurchaseStockEntryJob {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");

    private final PurchaseRepository purchaseRepository;
    private final PurchaseStockEntryService stockEntryService;

    @Scheduled(cron = "0 10 0 * * *", zone = "America/Lima")
    public void loadDuePurchases() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        for (Long purchaseId : purchaseRepository.findPendingStockEntryPurchaseIds(today)) {
            stockEntryService.loadStockIfPending(purchaseId, "SYSTEM_JOB");
        }
    }
}
