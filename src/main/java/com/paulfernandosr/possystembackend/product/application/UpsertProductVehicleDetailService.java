package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleDetail;
import com.paulfernandosr.possystembackend.product.domain.port.input.UpsertProductVehicleDetailUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductVehicleDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpsertProductVehicleDetailService implements UpsertProductVehicleDetailUseCase {

    private final ProductVehicleDetailRepository productVehicleDetailRepository;

    @Override
    public void upsertVehicleDetail(Long productId, ProductVehicleDetail detail) {
        detail.setProductId(productId);

        if (productVehicleDetailRepository.existsByProductId(productId)) {
            productVehicleDetailRepository.update(detail);
        } else {
            productVehicleDetailRepository.create(detail);
        }
    }
}
