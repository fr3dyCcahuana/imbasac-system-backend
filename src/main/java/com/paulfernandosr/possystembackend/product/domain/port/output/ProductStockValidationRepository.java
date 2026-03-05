package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto.ProductStockValidationDto;

import java.util.Collection;
import java.util.List;

public interface ProductStockValidationRepository {
    Collection<ProductStockValidationDto> validate(List<Long> ids, boolean includeSerialUnits, int serialLimit);
}