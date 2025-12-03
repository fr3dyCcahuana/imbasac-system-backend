package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleDetail;
import com.paulfernandosr.possystembackend.product.domain.exception.ProductVehicleDetailNotFoundException;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductVehicleDetailUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductVehicleDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetProductVehicleDetailService implements GetProductVehicleDetailUseCase {

    private final ProductVehicleDetailRepository productVehicleDetailRepository;

    @Override
    public ProductVehicleDetail getVehicleDetailByProductId(Long productId) {
        return productVehicleDetailRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductVehicleDetailNotFoundException(
                        "Vehicle detail not found for product: " + productId
                ));
    }
}
