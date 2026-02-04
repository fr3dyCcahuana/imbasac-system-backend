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

        String cat = product.getCategory() == null ? "" : product.getCategory().trim().toUpperCase();
        if (!cat.equals("MOTOR") && !cat.equals("MOTOCICLETAS")) {
            // Hard guard: por reglas de negocio, solo estas categorías pueden ser serializadas
            throw new InvalidProductException("Solo MOTOR y MOTOCICLETAS pueden tener manageBySerial=true.");
        }

        // Normaliza blanks -> null
        unit.setVin(norm(unit.getVin()));
        unit.setChassisNumber(norm(unit.getChassisNumber()));
        unit.setEngineNumber(norm(unit.getEngineNumber()));
        unit.setDuaNumber(norm(unit.getDuaNumber()));

        if ("MOTOR".equals(cat)) {
            if (unit.getEngineNumber() == null) {
                throw new InvalidProductSerialUnitException("Para categoría MOTOR, engineNumber es obligatorio.");
            }
        } else {
            // MOTOCICLETAS
            if (unit.getVin() == null || unit.getChassisNumber() == null || unit.getEngineNumber() == null) {
                throw new InvalidProductSerialUnitException("Para categoría MOTOCICLETAS, vin + chassisNumber + engineNumber son obligatorios.");
            }
        }

        // DUA: si envías uno, envía ambos
        if (unit.getDuaNumber() != null || unit.getDuaItem() != null) {
            if (unit.getDuaNumber() == null || unit.getDuaItem() == null) {
                throw new InvalidProductSerialUnitException("DUA incompleta: debe enviar duaNumber y duaItem.");
            }
            if (unit.getDuaItem() <= 0) {
                throw new InvalidProductSerialUnitException("duaItem debe ser mayor a 0.");
            }
        }

        unit.setProductId(productId);

        if (unit.getStatus() == null || unit.getStatus().isBlank()) {
            unit.setStatus("EN_ALMACEN");
        }

        return productSerialUnitRepository.create(unit);
    }

    private String norm(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
