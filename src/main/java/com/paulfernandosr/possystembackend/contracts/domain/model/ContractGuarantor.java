package com.paulfernandosr.possystembackend.contracts.domain.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractGuarantor {

    private Long id;
    private Long contractId;

    private String docType;
    private String docNumber;
    private String fullName;

    private String address;
    private String phone;

    private String occupation;
    private String companyName;
    private BigDecimal monthlyIncome;
}
