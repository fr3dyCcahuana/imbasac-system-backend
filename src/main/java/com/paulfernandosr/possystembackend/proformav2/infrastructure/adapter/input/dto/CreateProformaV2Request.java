package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto;

import lombok.*;

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
