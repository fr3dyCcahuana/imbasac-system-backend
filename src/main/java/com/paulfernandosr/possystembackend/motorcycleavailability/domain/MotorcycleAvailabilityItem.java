package com.paulfernandosr.possystembackend.motorcycleavailability.domain;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MotorcycleAvailabilityItem {
    private Long productId;
    private String sku;
    private String name;
    private String brand;
    private String model;
    private String engineCapacity;
    private String warehouseLocation;
    private String mainImageUrl;
    private List<String> colors;
    private long availableUnits;
    private long reservedUnits;
    private BigDecimal cashPrice;
    private BigDecimal initialFrom;
    private boolean includesCardPlate;
    private String status;
    private boolean canCreateContract;
}
