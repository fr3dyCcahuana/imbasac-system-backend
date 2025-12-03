package com.paulfernandosr.possystembackend.product.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVehicleDetail {

    private Long productId;      // PK y FK a product.id

    private String vin;          // Número VIN
    private String serialNumber; // Serie / chasis
    private String engineNumber; // Motor
    private String color;
    private Short yearMake;      // Año de fabricación
    private Short yearModel;     // Año modelo
    private String vehicleClass; // MOTO LINEAL, SCOOTER, etc.
}
