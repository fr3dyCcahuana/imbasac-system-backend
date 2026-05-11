package com.paulfernandosr.possystembackend.product.domain.port.input;

import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto.ProductSerialUnitCorrectionRequest;

public interface CorrectProductSerialUnitUseCase {

    ProductSerialUnit correct(Long productId,
                              Long serialUnitId,
                              ProductSerialUnitCorrectionRequest request,
                              String username);
}
