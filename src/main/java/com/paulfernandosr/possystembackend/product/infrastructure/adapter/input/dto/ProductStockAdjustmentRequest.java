package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductStockAdjustmentRequest {

    @NotBlank
    private String movementType; // IN_ADJUST / OUT_ADJUST

    @NotNull
    private BigDecimal quantity;

    private BigDecimal unitCost; // obligatorio en IN_ADJUST (se valida en servicio)

    private String reason;
    private String note;

    private List<SerialUnitAdjustmentRequest> serialUnits;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SerialUnitAdjustmentRequest {
        private Long id;
        private String vin;
        private String chassisNumber;
        private String engineNumber;
        private String color;
        private Integer yearMake;
        private String duaNumber;
        private Integer duaItem;
        }
}
