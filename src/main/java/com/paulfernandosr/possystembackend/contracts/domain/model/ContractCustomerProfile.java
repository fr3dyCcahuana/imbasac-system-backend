package com.paulfernandosr.possystembackend.contracts.domain.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractCustomerProfile {

    private Long id;
    private Long contractId;

    private String maritalStatus;
    private String nationality;

    private String district;
    private String province;

    private String housingType;
    private BigDecimal rentAmount;

    private String employerName;
    private String employerAddress;
    private String employmentTime;

    private BigDecimal netIncome;
    private BigDecimal spouseIncome;
    private BigDecimal otherIncome;

    private BigDecimal totalIncome;

    private String customerReferences;
}
