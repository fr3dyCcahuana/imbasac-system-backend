package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.ProductVehicleSpecs;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductException;
import com.paulfernandosr.possystembackend.product.domain.port.input.UpdateProductInfoUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductVehicleSpecsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateProductInfoService implements UpdateProductInfoUseCase {

    private void requireText(String val, String field) {
        if (val == null || val.trim().isEmpty()) {
            throw new InvalidProductException(field + " es obligatorio para category MOTOR/MOTOCICLETAS.");
        }
    }

    private final ProductRepository productRepository;
    private final ProductVehicleSpecsRepository specsRepository;

    @Override
    @Transactional
    public void updateProductInfoById(Long productId, Product product) {
        boolean requiresSpecs = Boolean.TRUE.equals(product.getManageBySerial())
                && ProductVehicleSpecsRules.isVehicleCategory(product.getCategory());

        ProductVehicleSpecs specs = product.getVehicleSpecs();

        if (requiresSpecs) {
            // brand/model ahora viven en product (no en specs)
            requireText(product.getBrand(), "brand");
            requireText(product.getModel(), "model");
            boolean alreadyHasSpecs = specsRepository.existsByProductId(productId);

            if (specs == null && !alreadyHasSpecs) {
                // En update, permitimos omitir vehicleSpecs solo si ya existe registro previo.
                throw new InvalidProductException("Debe registrar vehicleSpecs para productos serializados en category MOTOR o MOTOCICLETAS.");
            }

            if (specs != null) {
                specs.setProductId(productId);
                ProductVehicleSpecsRules.validateRequired(product.getCategory(), specs);
            }
        }

        productRepository.updateById(productId, product);

        if (requiresSpecs && specs != null) {
            if (specsRepository.existsByProductId(productId)) {
                specsRepository.update(specs);
            } else {
                specsRepository.create(specs);
            }
        }
    }
}
