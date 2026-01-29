package com.paulfernandosr.possystembackend.purchase.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseSerialUnit {

    /**
     * Para MOTOCICLETAS (y solo MOTOCICLETAS): VIN.
     */
    private String vin;

    /**
     * Para MOTOCICLETAS: número de chasis.
     *
     * Compatibilidad: el frontend antiguo enviaba "serialNumber".
     */
    @JsonAlias({"serialNumber"})
    private String chassisNumber;

    /**
     * Para MOTOR y MOTOCICLETAS: número de motor (obligatorio).
     */
    private String engineNumber;

    /**
     * Para MOTOR y MOTOCICLETAS: color (obligatorio).
     */
    private String color;

    /**
     * Para MOTOR y MOTOCICLETAS: año de fabricación (obligatorio).
     */
    private Integer yearMake;

    /**
     * Para ingresos por compra/lote: DUA (obligatorio).
     */
    private String duaNumber;

    /**
     * Para ingresos por compra/lote: ítem de DUA (obligatorio, > 0).
     */
    private Integer duaItem;
}
