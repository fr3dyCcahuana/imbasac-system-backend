package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    // ✅ NUEVO: serial/VIN
    private Boolean manageBySerial;

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

    private BigDecimal costReference;

    // ✅ NUEVO (2026): stock real (on hand) para listados/detalle.
    // Se calcula desde product_stock (no serial) o desde conteo de seriales EN_ALMACEN (serial).
    private BigDecimal stockOnHand;

    // ✅ NUEVO: flags venta/SUNAT/stock
    private Boolean facturableSunat;
    private Boolean affectsStock;
    private Boolean giftAllowed;

    // ✅ NUEVO (2026): ficha técnica para categorías vehiculares (MOTOR / MOTOCICLETAS)
    // NOTA: No se persiste en la tabla product; se guarda en product_vehicle_specs.
    private ProductVehicleSpecs vehicleSpecs;

    // ✅ NUEVO (2026): imágenes asociadas (solo se cargan en el detalle /products/{id})
    // NOTA: No se persiste en la tabla product; se guarda en product_image.
    private List<ProductImage> images;

    // Auditoría
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
