package com.paulfernandosr.possystembackend.salev2.domain.port.output;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface AccountsReceivableRepository {

    Long insert(Long saleId, Long customerId, LocalDate issueDate, LocalDate dueDate,
                BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal balanceAmount, String status);

    LockedAr lockById(Long arId);

    LockedAr lockBySaleId(Long saleId);

    void deleteBySaleId(Long saleId);
    void updateAmountsAndStatus(Long arId, BigDecimal paidAmount, BigDecimal balanceAmount, String status);
    void markOverdueForCustomer(Long customerId);

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class LockedAr {
        private Long id;
        private Long customerId;
        private LocalDate issueDate;
        private LocalDate dueDate;
        private BigDecimal totalAmount;
        private BigDecimal paidAmount;
        private BigDecimal balanceAmount;
        private String status;
    }
}
