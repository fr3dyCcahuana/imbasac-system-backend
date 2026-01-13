package com.paulfernandosr.possystembackend.supplier.application;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.supplier.domain.Supplier;
import com.paulfernandosr.possystembackend.supplier.domain.port.input.GetPageOfSuppliersUseCase;
import com.paulfernandosr.possystembackend.supplier.domain.port.output.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPageOfSuppliersService implements GetPageOfSuppliersUseCase {

    private final SupplierRepository supplierRepository;

    @Override
    public Page<Supplier> getPageOfSuppliers(String query, Pageable pageable) {
        return supplierRepository.findPage(query, pageable);
    }
}
