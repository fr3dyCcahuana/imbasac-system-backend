package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import java.math.BigDecimal;

public interface SalePaymentRepository {
    void insert(Long saleId, String method, BigDecimal amount);
}
