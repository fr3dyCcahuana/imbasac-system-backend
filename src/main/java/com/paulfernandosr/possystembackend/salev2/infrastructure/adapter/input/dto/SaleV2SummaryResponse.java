package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleV2SummaryResponse {
    private Long saleId;
    private String docType;
    private String series;
    private Long number;
    private LocalDate issueDate;

    private String customerDocNumber;
    private String customerName;

    private String paymentType;
    private BigDecimal total;

    private String status;
    private String sunatStatus;
    private String sunatResponseCode;
    private String sunatResponseDescription;
    private java.time.LocalDateTime sunatSentAt;

    private String editStatus;
    private Integer editCount;
    private java.time.LocalDateTime lastEditedAt;
    private String lastEditedByUsername;

    private Boolean canEditBeforeSunat;
    private Boolean canEmitSunat;

    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;

    private List<SaleV2SummaryItemResponse> items;
}
