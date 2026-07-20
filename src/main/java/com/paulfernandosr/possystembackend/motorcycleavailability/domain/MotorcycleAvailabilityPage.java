package com.paulfernandosr.possystembackend.motorcycleavailability.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MotorcycleAvailabilityPage {
    private MotorcycleAvailabilityResponse response;
    private int page;
    private int size;
    private int numberOfElements;
    private long totalElements;
    private int totalPages;
}
