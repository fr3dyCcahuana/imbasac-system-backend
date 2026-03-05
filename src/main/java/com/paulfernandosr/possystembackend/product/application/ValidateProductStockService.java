package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.port.input.ValidateProductStockUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductStockValidationRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto.ProductStockValidationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ValidateProductStockService implements ValidateProductStockUseCase {

    private final ProductStockValidationRepository repo;

    @Override
    public Collection<ProductStockValidationDto> validate(List<Long> ids, boolean includeSerialUnits, int serialLimit) {
        return repo.validate(ids, includeSerialUnits, serialLimit);
    }
}