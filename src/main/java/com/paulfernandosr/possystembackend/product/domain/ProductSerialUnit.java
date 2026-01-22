package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSerialUnit {

    private Long id;

    private Long productId;
    private Long purchaseItemId;
    private Long saleItemId;

    // ✅ NUEVO (2026): vínculo a ajuste manual de stock (facturableSunat=false)
    private Long stockAdjustmentId;

    private String vin;
    private String serialNumber;
    private String engineNumber;

    private String color;
    private Short yearMake;
    private Short yearModel;
    private String vehicleClass;

    private String status;       // EN_ALMACEN / RESERVADO / VENDIDO / DEVUELTO / BAJA
    private String locationCode;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
