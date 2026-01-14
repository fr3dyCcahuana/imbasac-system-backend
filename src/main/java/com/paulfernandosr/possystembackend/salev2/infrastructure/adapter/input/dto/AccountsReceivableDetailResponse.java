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
public class AccountsReceivableDetailResponse {
    private Long arId;

    private Long saleId;
    private String saleDocType;
    private String saleSeries;
    private Long saleNumber;

    private Long customerId;
    private String customerDocNumber;
    private String customerName;

    private LocalDate issueDate;
    private LocalDate dueDate;

    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;

    private String status;

    private List<AccountsReceivablePaymentInfo> payments;
}
