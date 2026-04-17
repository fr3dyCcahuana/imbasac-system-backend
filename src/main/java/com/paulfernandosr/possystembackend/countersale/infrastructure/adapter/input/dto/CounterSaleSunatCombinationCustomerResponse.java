package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterSaleSunatCombinationCustomerResponse {
    private String name;
    private String docTypeCode;
    private String docNumber;
    private String address;
}
