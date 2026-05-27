package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerCreditWarningResponse {
    private boolean hasPendingCredits;
    private BigDecimal totalBalance;
    private List<CustomerCreditWarningItemResponse> items;
}
