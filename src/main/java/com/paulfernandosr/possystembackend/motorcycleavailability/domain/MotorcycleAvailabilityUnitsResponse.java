package com.paulfernandosr.possystembackend.motorcycleavailability.domain;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MotorcycleAvailabilityUnitsResponse {
    private Long productId;
    private List<MotorcycleAvailabilityUnit> units;
}
