package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

import java.math.BigDecimal;

/**
 * Ficha técnica del producto cuando se trata de unidades serializadas por categoría:
 * - category=MOTOR
 * - category=MOTOCICLETAS
 *
 * Se persiste en la tabla product_vehicle_specs.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVehicleSpecs {

    private Long productId;

    /**
     * MOTOR | MOTOCICLETA
     * Se deriva desde la categoría del producto. Puede venir null en el request.
     */
    private String vehicleType;

    // --------------------
    // Comunes (MOTOR y MOTOCICLETA)
    // --------------------
    private String brand;            // marca
    private String model;            // modelo
    private String bodywork;         // carrocería

    private BigDecimal engineCapacity; // capacidad motor
    private String fuel;               // combustible
    private Integer cylinders;         // número de cilindros

    private BigDecimal netWeight;      // peso neto
    private BigDecimal payload;        // carga útil
    private BigDecimal grossWeight;    // peso bruto

    // --------------------
    // Solo MOTOCICLETA
    // --------------------
    private String vehicleClass;     // clase
    private BigDecimal enginePower;  // potencia motor
    private String rollingForm;      // form. rodante

    private Integer seats;           // número asientos
    private Integer passengers;      // número pasajeros
    private Integer axles;           // número ejes
    private Integer wheels;          // número ruedas

    private BigDecimal length;       // largo
    private BigDecimal width;        // ancho
    private BigDecimal height;       // alto
}
