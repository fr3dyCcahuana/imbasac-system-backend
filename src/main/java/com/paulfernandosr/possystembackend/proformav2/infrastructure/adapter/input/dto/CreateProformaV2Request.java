package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.salev2.domain.model.TaxStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProformaV2Request {
    private Long stationId;
    private Long createdBy;

    private String series;
    private Character priceList; // A/B/C/D
    private String currency;     // PEN/USD (default PEN)
    private String issueDate;    // yyyy-MM-dd (optional)

    private TaxStatus taxStatus;     // GRAVADA | NO_GRAVADA
    private BigDecimal igvRate;      // 18.00
    private Boolean igvIncluded;     // true si el precio incluye IGV (solo GRAVADA)

    private Long customerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;

    private String notes;
    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private Long productId;
        private String description;      // opcional (si quieres sobreescribir)
        private String presentation;     // opcional
        private String factor;           // opcional
        private String quantity;         // requerido
        private String discountPercent;  // opcional
    }
}
