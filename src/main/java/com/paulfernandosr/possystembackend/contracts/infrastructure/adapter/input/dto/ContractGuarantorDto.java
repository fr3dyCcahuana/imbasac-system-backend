package com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractGuarantorDto {

    private String docType;
    private String docNumber;
    private String fullName;

    private String address;
    private String phone;

    private String occupation;
    private String companyName;
    private BigDecimal monthlyIncome;
}
