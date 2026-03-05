package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractDetailResponse {

    private Long contractId;

    private Long stationId;
    private Long createdBy;

    private String series;
    private Long number;
    private LocalDate issueDate;

    private String currency;
    private BigDecimal exchangeRate;

    private String priceList;

    private Long customerId;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerAddress;

    private String paymentType;

    private BigDecimal cashPrice;
    private BigDecimal interestRateMonthly;
    private Integer installments;
    private BigDecimal initialAmount;

    private BigDecimal financedAmount;
    private BigDecimal interestAmount;
    private BigDecimal totalAmount;

    private String status;

    private Long saleId;
    private String notes;

    private ContractItemDetail item;
    private ContractGuarantorDto guarantor;
    private ContractCustomerProfileDto customerProfile;
    private List<ContractInstallmentResponse> installmentsDetail;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ContractItemDetail {
        private Long productId;
        private Long serialUnitId;
        private String sku;
        private String description;
        private String brand;
        private String model;
        private String vin;
        private BigDecimal unitPrice;
    }
}
