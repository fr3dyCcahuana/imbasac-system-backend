package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import com.paulfernandosr.possystembackend.salev2.domain.model.CustomerAccountSnapshot;

public interface CustomerAccountRepository {
    void ensureExists(Long customerId);
    CustomerAccountSnapshot findByCustomerId(Long customerId);
    void touchLastSaleAt(Long customerId);
    void touchLastPaymentAt(Long customerId);
    void recalculate(Long customerId);
}
