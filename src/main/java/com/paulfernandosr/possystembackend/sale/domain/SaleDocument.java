package com.paulfernandosr.possystembackend.sale.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleDocument {
    private String serial;
    private String number;
    private SaleType saleType;
    private BigDecimal taxableTotal;
    private BigDecimal igvTotal;
    private BigDecimal totalDiscount;
    private String station;
    private LocalDateTime issuedAt;
    private String issuedBy;
    private String hashCode;
    private Collection<Item> items;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String productName;
        private String unitOfMeasure;
        private int quantity;
        private BigDecimal unitPrice;
    }
}
