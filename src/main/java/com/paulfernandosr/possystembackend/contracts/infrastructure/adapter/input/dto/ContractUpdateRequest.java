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
public class ContractUpdateRequest {

    private Long stationId;

    private LocalDate issueDate;

    private String currency;
    private BigDecimal exchangeRate;

    private PriceList priceList;

    private Long customerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;

    private PaymentType paymentType;

    private Long productId;
    private Long serialUnitId;

    private BigDecimal cashPrice;

    private BigDecimal interestRateMonthly;
    private Integer installments;
    private BigDecimal initialAmount;
    private LocalDate firstDueDate;

    private String notes;

    private ContractGuarantorDto guarantor;
    private ContractCustomerProfileDto customerProfile;

    /**
     * Motivo obligatorio para auditoría de cambios.
     */
    private String editReason;
}
