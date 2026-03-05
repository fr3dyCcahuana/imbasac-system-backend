package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto.ProductStockValidationDto;

import java.util.Collection;
import java.util.List;

public interface ValidateProductStockUseCase {
    Collection<ProductStockValidationDto> validate(List<Long> ids, boolean includeSerialUnits, int serialLimit);
}