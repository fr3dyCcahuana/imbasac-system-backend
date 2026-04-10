package com.paulfernandosr.possystembackend.countersale.domain.port.output;

import java.math.BigDecimal;

public interface CounterSalePaymentRepository {
    void insert(Long counterSaleId, String method, BigDecimal amount);
    void deleteByCounterSaleId(Long counterSaleId);
}
