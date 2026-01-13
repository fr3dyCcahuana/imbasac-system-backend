package com.paulfernandosr.possystembackend.sale.domain;

import com.paulfernandosr.possystembackend.customer.domain.Customer;
import com.paulfernandosr.possystembackend.user.domain.User;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sale {
    private Long id;
    private String serial;
    private Long number;
    private SaleType type;
    private BigDecimal discount;
    private String comment;
    private Customer customer;
    private Collection<SaleItem> items;
    private SaleStatus status;
    private LocalDateTime issuedAt;
    private User issuedBy;

    public boolean isCanceled() {
        return SaleStatus.CANCELLED.equals(status);
    }

    public boolean isElectronic() {
        return SaleType.ELECTRONIC_INVOICE.equals(type)
                || SaleType.ELECTRONIC_RECEIPT.equals(type);
    }

    public String getSerial() {
        return type.getSerial();
    }
}
