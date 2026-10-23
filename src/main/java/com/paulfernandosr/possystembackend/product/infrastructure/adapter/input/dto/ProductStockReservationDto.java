package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class ProductStockReservationDto {
    private Long reservationId;
    private Long proformaId;
    private String proformaSeries;
    private Long proformaNumber;
    private Long reservedBy;
    private String reservedByName;
    private BigDecimal quantity;
    private LocalDateTime expiresAt;
}
