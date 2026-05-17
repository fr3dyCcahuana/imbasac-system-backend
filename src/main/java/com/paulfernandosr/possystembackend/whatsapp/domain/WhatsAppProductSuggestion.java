package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppProductSuggestion {
    private Long id;
    private Long conversationId;
    private Long productId;
    private Integer position;
    private String productCode;
    private String productName;
    private BigDecimal unitPrice;
    private BigDecimal stockQuantity;
    private String priceList;
    private String rawSnapshot;
    private OffsetDateTime createdAt;
}
