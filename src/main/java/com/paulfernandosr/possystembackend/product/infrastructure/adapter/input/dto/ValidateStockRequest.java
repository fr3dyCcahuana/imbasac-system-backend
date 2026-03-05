package com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class ValidateStockRequest {

    @NotEmpty
    private List<Long> ids;

    private Boolean includeSerialUnits; // default false
    private Integer serialLimit;        // default 50
}