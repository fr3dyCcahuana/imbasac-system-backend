package com.paulfernandosr.possystembackend.purchase.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Purchase {

    private Long id;

    // Documento
    private String documentType;
    private String documentSeries;
    private String documentNumber;

    private LocalDate issueDate;
    private LocalDate entryDate;
    private LocalDate dueDate;

    // Moneda y forma de pago
    private String currency;                  // PEN, USD, etc.
    private BigDecimal exchangeRate;         // Tipo de cambio

    private String paymentType;              // CONTADO / CREDITO
    private Integer creditDays;

    // Proveedor
    private String supplierRuc;
    private String supplierBusinessName;
    private String supplierAddress;

    // IGV, descuentos, flete
    private BigDecimal igvRate;              // %
    private Boolean igvIncluded;
    private Boolean applyIgvToCost;

    private String discountType;             // PORCENTAJE / MONTO / null
    private BigDecimal discountValue;

    private BigDecimal freightAmount;
    private BigDecimal perceptionAmount;

    // Totales
    private BigDecimal subtotal;             // Base imponible
    private BigDecimal igvAmount;
    private BigDecimal total;

    private String status;                   // REGISTRADA / ANULADA
    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String deliveryGuideSeries;
    private String deliveryGuideNumber;
    private String deliveryGuideCompany;
    // Detalle
    private List<PurchaseItem> items;
}
