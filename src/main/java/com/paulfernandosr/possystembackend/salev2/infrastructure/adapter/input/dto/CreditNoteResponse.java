package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditNoteResponse {
    private Long creditNoteId;
    private Long saleId;
    private String docType;
    private String series;
    private Long number;
    private LocalDate issueDate;
    private String creditNoteTypeCode;
    private String creditNoteTypeDescription;
    private String reason;
    private Boolean returnedToStock;

    private BigDecimal subtotal;
    private BigDecimal igvAmount;
    private BigDecimal total;

    private String sunatStatus;
    private String sunatCode;
    private String sunatDescription;
    private String hashCode;
    private String xmlPath;
    private String cdrPath;
    private String pdfPath;
    private LocalDateTime emittedAt;
    private Boolean accepted;
    private Boolean rejected;
    private Boolean communicationError;
    private Boolean retryable;

    private List<CreditNoteItemResponse> items;
}
