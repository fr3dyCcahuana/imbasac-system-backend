package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.row;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleProductRow {
    private Long id;
    private String serial;
    private String number;
    private LocalDateTime issueDatetime;
    private BigDecimal discount;
    private String comment;
    private String customerName;
    private String documentNumber;
    private String documentType;
    private String customerAddress;
    private boolean enabledCustomer;
    private String cashierFirstName;
    private String cashierLastName;
    private String cashierUsername;
    private String productName;
    private String productDescription;
    private String categoryName;
    private String categoryReference;
    private String categoryDescription;
    private String originCode;
    private String barcode;
    private BigDecimal price;
    private int quantity;
}
