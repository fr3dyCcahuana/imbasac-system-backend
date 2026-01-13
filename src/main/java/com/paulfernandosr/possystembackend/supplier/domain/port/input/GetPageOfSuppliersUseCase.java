package com.paulfernandosr.possystembackend.supplier.domain.port.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.supplier.domain.Supplier;

public interface GetPageOfSuppliersUseCase {
    Page<Supplier> getPageOfSuppliers(String query, Pageable pageable);
}
