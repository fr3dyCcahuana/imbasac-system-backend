package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    private Long id;

    // 1. Datos esenciales
    private String sku;               // Código interno
    private String name;              // Descripción
    private String productType;       // BIEN / SERVICIO
    private String category;          // Categoría / familia
    private String presentation;      // UNIDAD / PAR / SET / KIT
    private BigDecimal factor;        // Contenido (2 si es par, etc.)

    // 2. Info opcional recomendada
    private String originType;        // NACIONAL / IMPORTADO / FABRICA
    private String originCountry;     // País
    private String factoryCode;       // Código de fábrica / proveedor
    private String compatibility;     // Texto de compatibilidad

    // 3. Código de barras
    private String barcode;           // EAN/UPC

    // 4. Ubicación
    private String warehouseLocation; // Ej: "31-A-3"

    // 5. Precios
    private BigDecimal priceA;
    private BigDecimal priceB;
    private BigDecimal priceC;
    private BigDecimal priceD;

    // Costo referencia
    private BigDecimal costReference;

    // Auditoría
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
