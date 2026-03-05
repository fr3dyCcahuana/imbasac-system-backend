package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentType;
import com.paulfernandosr.possystembackend.salev2.domain.model.PriceList;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractCreateRequest {

    private Long stationId;

    private String series;          // C001
    private LocalDate issueDate;

    private String currency;        // PEN
    private BigDecimal exchangeRate;

    private PriceList priceList;    // A/B/C/D

    private Long customerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;

    private PaymentType paymentType;     // CONTADO/CREDITO

    // Moto + VIN
    private Long productId;
    private Long serialUnitId;

    // Precio al contado base
    private BigDecimal cashPrice;

    // Crédito
    private BigDecimal interestRateMonthly; // default 3.5
    private Integer installments;           // 1..6
    private BigDecimal initialAmount;       // libre
    private LocalDate firstDueDate;         // opcional (default issueDate + 30)

    private String notes;

    private ContractGuarantorDto guarantor;
    private ContractCustomerProfileDto customerProfile;
}
