package com.paulfernandosr.possystembackend.purchase.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseProduct {
    private Long id;
    private String name;
    private String description;
    private String category;
    private String brand;
    private String model;
    private String sku;
    private String barcode;

    private BigDecimal stockOnHand;
    private BigDecimal costReference;
    private BigDecimal priceA;
    private BigDecimal priceB;
    private BigDecimal priceC;
    private BigDecimal priceD;

    private Boolean enabled;
    private Boolean facturableSunat;
    private Boolean manageBySerial;
    private Boolean affectsStock;
    private Boolean giftAllowed;

    private String productType;
    private String presentation;
    private BigDecimal factor;

    private String originType;
    private String originCountry;
    private String factoryCode;
    private String compatibility;
    private String warehouseLocation;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private PurchaseProductStock stock;
    private PurchaseVehicleSpecs vehicleSpecs;
    private List<PurchaseProductImage> images;
}
