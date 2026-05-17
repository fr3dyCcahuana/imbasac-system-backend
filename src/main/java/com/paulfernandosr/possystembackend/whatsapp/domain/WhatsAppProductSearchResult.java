package com.paulfernandosr.possystembackend.whatsapp.domain;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppProductSearchResult {
    private Long productId;
    private String sku;
    private String name;
    private String category;
    private String brand;
    private String model;
    private String presentation;
    private String warehouseLocation;
    private Boolean manageBySerial;
    private BigDecimal stockQuantity;
    private BigDecimal selectedPrice;
    private String selectedPriceList;
    private String mainImageUrl;
}
