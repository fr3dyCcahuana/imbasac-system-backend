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
    private Long sourceProformaNumber;  // numero visible de proforma que origina la venta
    private Long sourceProformaId;      // alias compatible: historicamente el frontend envio el numero visible aqui
}
