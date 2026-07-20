package com.paulfernandosr.possystembackend.motorcycleavailability.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MotorcycleAvailabilityUnit {
    private Long serialUnitId;
    private String vin;
    private String chassisNumber;
    private String engineNumber;
    private String color;
    private Integer yearMake;
    private String status;
    private Long contractId;
    private boolean canCreateContract;
}
