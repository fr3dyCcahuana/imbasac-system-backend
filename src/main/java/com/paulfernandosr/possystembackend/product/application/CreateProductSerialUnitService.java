package com.paulfernandosr.possystembackend.product.application;

import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductException;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductSerialUnitException;
import com.paulfernandosr.possystembackend.product.domain.exception.ProductNotFoundException;
import com.paulfernandosr.possystembackend.product.domain.port.input.CreateProductSerialUnitUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductSerialUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateProductSerialUnitService implements CreateProductSerialUnitUseCase {

    private final ProductRepository productRepository;
    private final ProductSerialUnitRepository productSerialUnitRepository;

    @Override
    public ProductSerialUnit create(Long productId, ProductSerialUnit unit) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId.toString()));

        Boolean manageBySerial = product.getManageBySerial();
        if (manageBySerial == null || !manageBySerial) {
            throw new InvalidProductException("El producto no está configurado para control por serie/VIN (manageBySerial=false).");
        }

        if ((unit.getVin() == null || unit.getVin().isBlank())
                && (unit.getSerialNumber() == null || unit.getSerialNumber().isBlank())
                && (unit.getEngineNumber() == null || unit.getEngineNumber().isBlank())) {
            throw new InvalidProductSerialUnitException("Debe registrar al menos uno: vin, serialNumber o engineNumber.");
        }

        unit.setProductId(productId);

        if (unit.getStatus() == null || unit.getStatus().isBlank()) {
            unit.setStatus("EN_ALMACEN");
        }

        return productSerialUnitRepository.create(unit);
    }
}
