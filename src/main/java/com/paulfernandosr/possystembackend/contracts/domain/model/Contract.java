package com.paulfernandosr.possystembackend.contracts.domain.model;

import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentType;
import com.paulfernandosr.possystembackend.salev2.domain.model.PriceList;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contract {

    private Long id;

    private Long stationId;
    private Long createdBy;

    private String series;
    private Long number;

    private LocalDate issueDate;

    private String currency;
    private BigDecimal exchangeRate;

    private PriceList priceList;

    // snapshot cliente
    private Long customerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;

    private PaymentType paymentType;

    // financieros
    private BigDecimal cashPrice;
    private BigDecimal interestRateMonthly; // % mensual (default 3.5)
    private Integer installments;           // 1..6
    private BigDecimal initialAmount;

    private BigDecimal financedAmount;
    private BigDecimal interestAmount;
    private BigDecimal totalAmount;

    private ContractStatus status;

    private Long saleId;

    private String notes;
}
