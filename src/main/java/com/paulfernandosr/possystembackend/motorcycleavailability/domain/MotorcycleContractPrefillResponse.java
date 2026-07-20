package com.paulfernandosr.possystembackend.motorcycleavailability.domain;

import com.paulfernandosr.possystembackend.product.domain.Product;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MotorcycleContractPrefillResponse {
    private Product product;
    private MotorcycleAvailabilityUnit serialUnit;
    private String priceList;
    private BigDecimal cashPrice;
    private boolean canCreateContract;
}
