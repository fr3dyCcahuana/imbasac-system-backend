package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.ProductVehicleSpecs;
import com.paulfernandosr.possystembackend.product.domain.exception.ProductVehicleSpecsNotFoundException;
import com.paulfernandosr.possystembackend.product.domain.port.input.GetProductVehicleSpecsUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductVehicleSpecsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetProductVehicleSpecsService implements GetProductVehicleSpecsUseCase {

    private final ProductVehicleSpecsRepository repository;

    @Override
    public ProductVehicleSpecs getByProductId(Long productId) {
        return repository.findByProductId(productId)
                .orElseThrow(() -> new ProductVehicleSpecsNotFoundException(
                        "Vehicle specs not found for product: " + productId
                ));
    }
}
