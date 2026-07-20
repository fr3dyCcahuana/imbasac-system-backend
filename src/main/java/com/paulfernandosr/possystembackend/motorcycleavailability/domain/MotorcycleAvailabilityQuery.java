package com.paulfernandosr.possystembackend.motorcycleavailability.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MotorcycleAvailabilityQuery {
    private String query;
    private String brand;
    private String engineCapacity;
    private String warehouseLocation;
    private String status;
    private int page;
    private int size;
}
