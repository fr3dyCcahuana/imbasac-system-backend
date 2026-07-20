package com.paulfernandosr.possystembackend.motorcycleavailability.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MotorcycleAvailabilityStats {
    private long availableModels;
    private long availableUnits;
    private long reservedUnits;
    private long outOfStockModels;
}
