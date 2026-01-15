package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.salev2.domain.model.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2CreateRequest {

    private Long stationId;
    private Long saleSessionId;

    private DocType docType;          // SIMPLE/BOLETA/FACTURA
    private String series;            // e.g. B001
    private LocalDate issueDate;

    private String currency;          // PEN
    private BigDecimal exchangeRate;

    private PriceList priceList;      // A/B/C/D

    private Long customerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;

    private TaxStatus taxStatus;      // GRAVADA/NO_GRAVADA
    private String taxReason;
    private BigDecimal igvRate;       // 18.00

    /**
     * NUEVO:
     * true  => unitPrice incluye IGV (solo válido si taxStatus=GRAVADA)
     * false => unitPrice NO incluye IGV
     */
    private Boolean igvIncluded;

    private PaymentType paymentType;  // CONTADO/CREDITO
    private Integer creditDays;
    private LocalDate dueDate;

    private String notes;

    private List<Item> items;
    private Payment payment;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private Long productId;
        private BigDecimal quantity;

        /**
         * Descuento solo permitido si docType=SIMPLE.
         * En BOLETA/FACTURA debe ser 0.
         */
        private BigDecimal discountPercent;

        private LineKind lineKind;        // VENDIDO/OBSEQUIO
        private String giftReason;

        /**
         * NUEVO:
         * Si viene, se usa tal cual (snapshot de proforma / override de precio)
         */
        private BigDecimal unitPriceOverride;

        // Para productos manage_by_serial=true:
        private List<Long> serialUnitIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Payment {
        private PaymentMethod method; // EFECTIVO/YAPE/TRANSFERENCIA/OTRO
    }
}
