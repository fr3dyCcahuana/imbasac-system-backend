package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterSaleSerialUnitResponse {
    private Long counterSaleItemId;
    private Long serialUnitId;
    private String status;
    private String vin;
    private String chassisNumber;
    private String engineNumber;
    private String color;
    private Integer yearMake;
}
