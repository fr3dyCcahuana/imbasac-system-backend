package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProformaV2SummaryResponse {
    private Long proformaId;
    private String docType;
    private String series;
    private Long number;
    private LocalDate issueDate;

    private String customerDocNumber;
    private String customerName;

    private String paymentType;
    private Integer creditDays;
    private LocalDate dueDate;

    private BigDecimal total;
    private String status;
    private Long convertedSaleId;

    // Creador de la proforma
    private Long createdBy;
    private String createdByName;
    private Long createdByRoleId;
    private String createdByRoleName;

    // Indica si la proforma fue editada (edited_at no nulo)
    private boolean edited;

    // Comprobante electrónico generado al convertir (cuando aplica)
    private String saleDocType;
    private String saleSeries;
    private Long saleNumber;
}
