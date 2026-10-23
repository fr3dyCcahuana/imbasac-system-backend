package com.paulfernandosr.possystembackend.stockreservation.domain.model;

import lombok.*;

import java.math.BigDecimal;

/** Explicit quantities confirmed by the seller in the reservation-conflict dialog. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationReassignment {
    private Long reservationId;
    private BigDecimal quantity;
}
