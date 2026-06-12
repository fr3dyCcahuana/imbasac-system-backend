package com.paulfernandosr.possystembackend.stockreservation.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.stockreservation.domain.port.output.ProductStockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StockReservationExpiryJob {

    private final ProductStockReservationRepository reservationRepository;

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Lima")
    @Transactional
    public void expirePreviousDays() {
        reservationRepository.expirePreviousDays();
    }
}
