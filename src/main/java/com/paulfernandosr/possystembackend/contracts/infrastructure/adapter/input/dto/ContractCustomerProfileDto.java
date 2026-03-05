package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractCustomerProfileDto {

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
