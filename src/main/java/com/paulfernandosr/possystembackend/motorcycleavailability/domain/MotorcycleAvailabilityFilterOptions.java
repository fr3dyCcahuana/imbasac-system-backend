package com.paulfernandosr.possystembackend.motorcycleavailability.domain;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MotorcycleAvailabilityFilterOptions {
    private List<String> brands;
    private List<String> engineCapacities;
    private List<String> warehouseLocations;
    private List<String> statuses;
}
