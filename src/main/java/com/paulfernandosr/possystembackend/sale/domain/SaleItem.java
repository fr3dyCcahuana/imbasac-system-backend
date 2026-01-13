package com.paulfernandosr.possystembackend.sale.domain;

import com.paulfernandosr.possystembackend.product.domain.Product;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleItem {
    private Product product;
    private BigDecimal price;
    private int quantity;
}
