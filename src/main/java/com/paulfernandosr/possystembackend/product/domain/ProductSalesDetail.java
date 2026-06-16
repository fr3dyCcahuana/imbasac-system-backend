package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSalesDetail {

    private Long id;
    private String sku;
    private String barcode;
    private String name;
    private String brand;
    private String model;
    private String category;
    private String warehouseLocation;
    private String presentation;
    private java.math.BigDecimal factor;

    private Boolean manageBySerial;
    private String compatibility;
    private Boolean giftAllowed;

    private Boolean affectsStock;
    private Boolean facturableSunat;

    private String priceList;             // "A|B|C|D"
    private java.math.BigDecimal price;   // price_a/b/c/d según priceList

    private java.math.BigDecimal regularPrice;

    private Long offerId;
    private String offerCode;
    private String offerName;
    private java.math.BigDecimal offerPrice;
    private java.math.BigDecimal offerMinQuantity;
    private java.math.BigDecimal offerDiscountPercent;

    private java.math.BigDecimal stockAvailable;

    private java.util.List<ProductImage> images;
    private java.util.List<ProductSerialUnit> availableSerialUnits;
}
