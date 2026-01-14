package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface AccountsReceivablePaymentRepository {
    void insert(Long arId, BigDecimal amount, String method, LocalDateTime paidAt, String note);

    boolean existsByArId(Long arId);
}
