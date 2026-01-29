package com.paulfernandosr.possystembackend.proformav2.domain;

import com.paulfernandosr.possystembackend.proformav2.domain.model.ProformaStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Proforma {
    private Long id;

    private Long stationId;
    private Long createdBy;

    private String series;
    private Long number;
    private LocalDate issueDate;

    private Character priceList; // A/B/C/D
    private String currency;     // PEN/USD

    private Long customerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;

    private String notes;

    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal total;
    private String taxStatus;      // GRAVADA | NO_GRAVADA
    private BigDecimal igvRate;   // 18.00
    private BigDecimal igvAmount; // taxTotal
    private Boolean igvIncluded;
    private ProformaStatus status;
    private String cashierUsername;
    private String cashierFirstName;
    private String cashierLastName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
