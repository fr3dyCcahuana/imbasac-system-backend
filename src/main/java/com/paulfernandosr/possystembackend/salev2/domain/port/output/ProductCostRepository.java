package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.CostPolicy;

import java.math.BigDecimal;

public interface ProductCostRepository {

    BigDecimal getUnitCost(Long productId, CostPolicy policy);
}
