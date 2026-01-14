package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import java.math.BigDecimal;

public interface SaleSessionAccumulatorRepository {
    void addSaleIncomeAndDiscount(Long saleSessionId, BigDecimal saleTotal, BigDecimal discountTotal);

    void subtractSaleIncomeAndDiscount(Long saleSessionId, BigDecimal saleTotal, BigDecimal discountTotal);
}
