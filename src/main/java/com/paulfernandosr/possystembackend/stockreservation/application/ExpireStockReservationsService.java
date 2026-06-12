package com.paulfernandosr.possystembackend.stockreservation.application;

import com.paulfernandosr.possystembackend.stockreservation.domain.port.input.ExpireStockReservationsUseCase;
import com.paulfernandosr.possystembackend.stockreservation.domain.port.output.ProductStockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpireStockReservationsService implements ExpireStockReservationsUseCase {

    private final ProductStockReservationRepository reservationRepository;

    @Override
    @Transactional
    public int expirePreviousDays() {
        return reservationRepository.expirePreviousDays();
    }
}
