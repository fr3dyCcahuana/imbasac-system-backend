package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductKardexEntry {

    private Long id;
    private LocalDateTime movementDate;

    private Long productId;
    private String sku;
    private String productName;
    private String category;
    private String brand;
    private String model;
    private String presentation;
    private Boolean manageBySerial;

    private String movementType;
    private String movementLabel;     // COMPRA, VENTA, VENTANILLA, AJUSTE, DEVOLUCION, OTRO
    private String direction;         // ENTRADA / SALIDA / NEUTRO

    private String sourceTable;
    private Long sourceId;
    private String sourceDocumentType;
    private String sourceSeries;
    private String sourceNumber;
    private LocalDate sourceIssueDate;
    private String sourceStatus;
    private Integer sourceLineNumber;

    private String counterpartType;   // PROVEEDOR / CLIENTE / INTERNO / OTRO
    private String counterpartDocumentNumber;
    private String counterpartName;

    private BigDecimal quantityIn;
    private BigDecimal quantityOut;
    private BigDecimal movementQuantity;

    private BigDecimal stockBefore;
    private BigDecimal stockAfter;

    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private BigDecimal averageCostAfter;

    private BigDecimal sourceUnitPrice;
    private BigDecimal sourceLineTotal;

    private String adjustmentReason;
    private String note;
}
