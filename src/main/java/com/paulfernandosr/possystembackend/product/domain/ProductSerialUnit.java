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

    // ✅ vínculo a ajuste manual de stock (facturableSunat=false)
    private Long stockAdjustmentId;

    // -------------------------
    // Identificadores
    // -------------------------
    // MOTOCICLETAS: suele venir VIN + CHASIS + MOTOR
    // MOTOR: suele venir solo MOTOR (engineNumber)
    private String vin;
    private String chassisNumber;
    private String engineNumber;

    // -------------------------
    // Atributos
    // -------------------------
    private String color;
    private Integer yearMake;

    // -------------------------
    // Importación (DUA)
    // -------------------------
    private String duaNumber;
    private Integer duaItem;

    private String status;       // EN_ALMACEN / RESERVADO / VENDIDO / DEVUELTO / BAJA

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
