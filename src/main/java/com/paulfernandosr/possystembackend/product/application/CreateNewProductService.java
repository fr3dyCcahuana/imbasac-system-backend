package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.ProductVehicleSpecs;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductException;
import com.paulfernandosr.possystembackend.product.domain.port.input.CreateNewProductUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductVehicleSpecsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateNewProductService implements CreateNewProductUseCase {

    private final ProductRepository productRepository;
    private final ProductVehicleSpecsRepository specsRepository;

    @Override
    @Transactional
    public Product createNewProduct(Product product) {
        // Reglas: si manageBySerial=true y category=MOTOR|MOTOCICLETAS, la ficha técnica es obligatoria.
        boolean requiresSpecs = Boolean.TRUE.equals(product.getManageBySerial())
                && ProductVehicleSpecsRules.isVehicleCategory(product.getCategory());

        if (requiresSpecs) {
            ProductVehicleSpecs specs = product.getVehicleSpecs();
            if (specs == null) {
                throw new InvalidProductException("Debe registrar vehicleSpecs para productos serializados en category MOTOR o MOTOCICLETAS.");
            }
            // Valida y setea vehicleType derivado por category.
            ProductVehicleSpecsRules.validateRequired(product.getCategory(), specs);
        }

        Product created = productRepository.create(product);

        if (requiresSpecs) {
            ProductVehicleSpecs specs = product.getVehicleSpecs();
            specs.setProductId(created.getId());
            specsRepository.create(specs);
            created.setVehicleSpecs(specs);
        }

        return created;
    }
}