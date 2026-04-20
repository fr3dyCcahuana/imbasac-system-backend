package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPurchasePricesInfo {

    private Long productId;
    private String sku;
    private String name;
    private String brand;
    private String model;
    private String category;

    private List<ProductPurchasePrice> purchasePrices;
    private List<ProductPriceTypeInfo> priceTypes;
}
