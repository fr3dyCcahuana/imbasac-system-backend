package com.paulfernandosr.possystembackend.contracts.domain.port.output;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface AccountsReceivableOverrideRepository {

    Long findArIdBySaleId(Long saleId);

    void overrideTotals(Long arId, BigDecimal totalAmount, LocalDate dueDate);

    void recalculateCustomerAccount(Long customerId);
}
