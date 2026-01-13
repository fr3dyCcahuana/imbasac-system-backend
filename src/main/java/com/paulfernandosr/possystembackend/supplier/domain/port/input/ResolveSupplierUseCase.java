package com.paulfernandosr.possystembackend.supplier.domain.port.input;

import com.paulfernandosr.possystembackend.supplier.domain.Supplier;

public interface ResolveSupplierUseCase {
    Supplier resolveSupplier(Supplier supplier);
}
