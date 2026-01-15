package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetPageOfProductSerialUnitsUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductSerialUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPageOfProductSerialUnitsService implements GetPageOfProductSerialUnitsUseCase {

    private final ProductSerialUnitRepository productSerialUnitRepository;

    @Override
    public Page<ProductSerialUnit> getPage(Long productId, String query, String status, Pageable pageable) {
        return productSerialUnitRepository.findPage(productId, query, status, pageable);
    }
}
