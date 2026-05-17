package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppCartItem {
    private Long id;
    private Long cartId;
    private Long productId;
    private String productCode;
    private String productName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private String priceList;
    private OffsetDateTime createdAt;
}
