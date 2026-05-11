package com.paulfernandosr.possystembackend.product.domain.port.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.product.domain.ProductSerialUnit;

import java.util.Optional;

public interface ProductSerialUnitRepository {

    ProductSerialUnit create(ProductSerialUnit unit);

    Optional<ProductSerialUnit> lockById(Long serialUnitId);

    ProductSerialUnit updateCorrection(ProductSerialUnit unit);

    boolean existsCounterSaleLink(Long serialUnitId);

    Optional<String> findContractCorrectionBlockReason(Long serialUnitId);

    Page<ProductSerialUnit> findPage(Long productId, String query, String status, Pageable pageable);

    // ✅ NUEVO (2026): soporte para ajustes manuales de stock
    Optional<ProductSerialUnit> findAvailableById(Long productId, Long serialUnitId);

    Optional<ProductSerialUnit> findAvailableByVin(Long productId, String vin);

    Optional<ProductSerialUnit> findAvailableByEngineNumber(Long productId, String engineNumber);

    Optional<ProductSerialUnit> findAvailableBySerialNumber(Long productId, String serialNumber);

    void markAsBaja(Long serialUnitId, Long stockAdjustmentId);
}
