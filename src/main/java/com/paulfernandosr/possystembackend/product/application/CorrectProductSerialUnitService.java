package com.paulfernandosr.possystembackend.product.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.product.domain.Product;
import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductException;
import com.paulfernandosr.possystembackend.product.domain.exception.InvalidProductSerialUnitException;
import com.paulfernandosr.possystembackend.product.domain.exception.ProductNotFoundException;
import com.paulfernandosr.possystembackend.product.domain.port.input.CorrectProductSerialUnitUseCase;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductSerialUnitEditAuditRepository;
import com.paulfernandosr.possystembackend.product.domain.port.output.ProductSerialUnitRepository;
import com.paulfernandosr.possystembackend.product.infrastructure.adapter.input.dto.ProductSerialUnitCorrectionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CorrectProductSerialUnitService implements CorrectProductSerialUnitUseCase {

    private final ProductRepository productRepository;
    private final ProductSerialUnitRepository productSerialUnitRepository;
    private final ProductSerialUnitEditAuditRepository productSerialUnitEditAuditRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ProductSerialUnit correct(Long productId,
                                     Long serialUnitId,
                                     ProductSerialUnitCorrectionRequest request,
                                     String username) {

        if (productId == null) {
            throw new InvalidProductSerialUnitException("productId es obligatorio.");
        }
        if (serialUnitId == null) {
            throw new InvalidProductSerialUnitException("serialUnitId es obligatorio.");
        }
        validateReason(request);

        ProductSerialUnit before = productSerialUnitRepository.lockById(serialUnitId)
                .orElseThrow(() -> new InvalidProductSerialUnitException("Unidad serial no encontrada: " + serialUnitId));

        if (!productId.equals(before.getProductId())) {
            throw new InvalidProductSerialUnitException("La unidad serial no pertenece al producto indicado.");
        }

        Product product = productRepository.findById(before.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(String.valueOf(before.getProductId())));

        validateProduct(product);
        validateEditableState(before);

        ProductSerialUnit corrected = ProductSerialUnit.builder()
                .id(before.getId())
                .productId(before.getProductId())
                .purchaseItemId(before.getPurchaseItemId())
                .saleItemId(before.getSaleItemId())
                .stockAdjustmentId(before.getStockAdjustmentId())
                .status(before.getStatus())
                .vin(preserveIfNull(request.getVin(), before.getVin()))
                .chassisNumber(preserveIfNull(request.getChassisNumber(), before.getChassisNumber()))
                .engineNumber(preserveIfNull(request.getEngineNumber(), before.getEngineNumber()))
                .color(preserveIfNull(request.getColor(), before.getColor()))
                .yearMake(request.getYearMake() != null ? request.getYearMake() : before.getYearMake())
                .duaNumber(preserveIfNull(request.getDuaNumber(), before.getDuaNumber()))
                .duaItem(request.getDuaItem() != null ? request.getDuaItem() : before.getDuaItem())
                .createdAt(before.getCreatedAt())
                .updatedAt(before.getUpdatedAt())
                .build();

        validateByCategory(product, corrected);

        ProductSerialUnit after = productSerialUnitRepository.updateCorrection(corrected);

        productSerialUnitEditAuditRepository.insert(
                serialUnitId,
                null,
                nzs(username, "system"),
                request.getReason().trim(),
                toJson(before),
                toJson(after)
        );

        return after;
    }

    private void validateReason(ProductSerialUnitCorrectionRequest request) {
        if (request == null) {
            throw new InvalidProductSerialUnitException("Request vacío.");
        }
        if (request.getReason() == null || request.getReason().trim().length() < 5) {
            throw new InvalidProductSerialUnitException("reason es obligatorio y debe tener mínimo 5 caracteres.");
        }
    }

    private void validateProduct(Product product) {
        if (product == null) {
            throw new ProductNotFoundException("Producto no encontrado.");
        }

        Boolean manageBySerial = product.getManageBySerial();
        if (manageBySerial == null || !manageBySerial) {
            throw new InvalidProductException("El producto no está configurado para control por serie/VIN (manageBySerial=false).");
        }

        String category = category(product);
        if (!"MOTOR".equals(category) && !"MOTOCICLETAS".equals(category)) {
            throw new InvalidProductException("Solo MOTOR y MOTOCICLETAS pueden corregir datos serializados.");
        }
    }

    private void validateEditableState(ProductSerialUnit unit) {
        String status = nzs(unit.getStatus()).toUpperCase();

        if ("BAJA".equals(status)) {
            throw new InvalidProductSerialUnitException("No se puede corregir una unidad serial en estado BAJA.");
        }

        if (unit.getSaleItemId() != null || "VENDIDO".equals(status)) {
            throw new InvalidProductSerialUnitException("No se puede corregir una unidad serial vendida o asociada a una venta.");
        }

        if (productSerialUnitRepository.existsCounterSaleLink(unit.getId())) {
            throw new InvalidProductSerialUnitException("No se puede corregir una unidad serial asociada a una venta de ventanilla.");
        }

        productSerialUnitRepository.findContractCorrectionBlockReason(unit.getId())
                .ifPresent(reason -> {
                    throw new InvalidProductSerialUnitException(reason);
                });

        if (!"EN_ALMACEN".equals(status) && !"RESERVADO".equals(status)) {
            throw new InvalidProductSerialUnitException("Solo se puede corregir unidades seriales EN_ALMACEN o RESERVADO sin venta asociada.");
        }
    }

    private void validateByCategory(Product product, ProductSerialUnit unit) {
        String category = category(product);

        if ("MOTOCICLETAS".equals(category)) {
            require(unit.getVin(), "vin es obligatorio para MOTOCICLETAS.");
            require(unit.getChassisNumber(), "chassisNumber es obligatorio para MOTOCICLETAS.");
            require(unit.getEngineNumber(), "engineNumber es obligatorio para MOTOCICLETAS.");
            validateCommonVehicleFields(unit);
            return;
        }

        if ("MOTOR".equals(category)) {
            // En producción también puede existir error humano en VIN/chasis.
            // Por eso NO se prohíbe corregir vin/chassisNumber para MOTOR.
            // Se mantienen obligatorios los datos técnicos comunes y el número de motor.
            require(unit.getEngineNumber(), "engineNumber es obligatorio para MOTOR.");
            validateCommonVehicleFields(unit);
        }
    }

    private void validateCommonVehicleFields(ProductSerialUnit unit) {
        require(unit.getColor(), "color es obligatorio.");

        if (unit.getYearMake() == null) {
            throw new InvalidProductSerialUnitException("yearMake es obligatorio.");
        }
        if (unit.getYearMake() < 1900 || unit.getYearMake() > 2100) {
            throw new InvalidProductSerialUnitException("yearMake no es válido.");
        }

        require(unit.getDuaNumber(), "duaNumber es obligatorio.");
        if (unit.getDuaItem() == null) {
            throw new InvalidProductSerialUnitException("duaItem es obligatorio.");
        }
        if (unit.getDuaItem() <= 0) {
            throw new InvalidProductSerialUnitException("duaItem debe ser mayor a 0.");
        }
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidProductSerialUnitException(message);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar auditoría de unidad serial.", e);
        }
    }

    private String category(Product product) {
        return nzs(product.getCategory()).trim().toUpperCase();
    }

    private String preserveIfNull(String newValue, String currentValue) {
        if (newValue == null) {
            return currentValue;
        }

        return norm(newValue);
    }

    private String norm(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nzs(String value) {
        return value == null ? "" : value;
    }

    private String nzs(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
