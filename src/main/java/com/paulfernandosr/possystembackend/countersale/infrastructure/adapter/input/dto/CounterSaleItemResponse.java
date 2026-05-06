package com.paulfernandosr.possystembackend.countersale.infrastructure.adapter.input.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CounterSaleItemResponse {
    private Long counterSaleItemId;
    private Integer lineNumber;
    private Long productId;
    private String sku;
    private String description;
    private String productLocation;
    private String presentation;
    private BigDecimal factor;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountPercent;
    private BigDecimal discountAmount;
    private String lineKind;
    private String giftReason;
    private Boolean affectsStock;
    private BigDecimal unitCostSnapshot;
    private BigDecimal totalCostSnapshot;
    private BigDecimal revenueTotal;
    private String productCategory;
    private LocalDateTime createdAt;
    private VehicleDetailsResponse vehicleDetails;
    private List<CounterSaleSerialUnitResponse> serialUnits;
}
