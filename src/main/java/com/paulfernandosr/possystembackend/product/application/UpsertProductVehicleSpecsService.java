package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.ProductVehicleSpecs;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductException;
import com.paulfernandosr.possystembackend.product.domain.exception.ProductNotFoundException;
import com.paulfernandosr.possystembackend.product.domain.port.input.UpsertProductVehicleSpecsUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductVehicleSpecsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpsertProductVehicleSpecsService implements UpsertProductVehicleSpecsUseCase {

    private final ProductRepository productRepository;
    private final ProductVehicleSpecsRepository specsRepository;

    @Override
    public void upsert(Long productId, ProductVehicleSpecs specs) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId.toString()));

        Boolean manageBySerial = product.getManageBySerial();
        if (manageBySerial == null || !manageBySerial) {
            throw new InvalidProductException("El producto no está configurado para control por serie/VIN (manageBySerial=false)."
            );
        }

        String category = product.getCategory();
        if (!ProductVehicleSpecsRules.isVehicleCategory(category)) {
            throw new InvalidProductException("La ficha técnica aplica solo para category MOTOR o MOTOCICLETAS. Category actual: " + category);
        }

        specs.setProductId(productId);

        ProductVehicleSpecsRules.validateRequired(category, specs);

        if (specsRepository.existsByProductId(productId)) {
            specsRepository.update(specs);
        } else {
            specsRepository.create(specs);
        }
    }
}
