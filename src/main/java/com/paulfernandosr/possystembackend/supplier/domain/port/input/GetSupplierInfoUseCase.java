package com.paulfernandosr.possystembackend.supplier.domain.port.input;

import com.paulfernandosr.possystembackend.supplier.domain.Supplier;

public interface GetSupplierInfoUseCase {
    Supplier getSupplierInfoById(Long supplierId);
}
