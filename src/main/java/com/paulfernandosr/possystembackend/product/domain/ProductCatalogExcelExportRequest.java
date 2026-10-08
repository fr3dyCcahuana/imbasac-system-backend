package com.paulfernandosr.possystembackend.product.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ProductCatalogExcelExportRequest {
    private String query;
    private String brand;
    private String model;
    private String category;
    private String stock = "ALL";
    private Boolean includeStock = false;
    private List<String> priceTypes = new ArrayList<>();
}
