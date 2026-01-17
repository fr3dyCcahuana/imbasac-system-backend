package com.paulfernandosr.possystembackend.purchase.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseSerialUnit {

    private String vin;
    private String serialNumber;
    private String engineNumber;

    private String color;
    private Integer yearMake;
    private Integer yearModel;
    private String vehicleClass;
    private String locationCode;
}
