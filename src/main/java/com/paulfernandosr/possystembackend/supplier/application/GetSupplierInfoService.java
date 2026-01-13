package com.paulfernandosr.possystembackend.supplier.application;

import com.paulfernandosr.possystembackend.supplier.domain.Supplier;
import com.paulfernandosr.possystembackend.supplier.domain.exception.SupplierNotFoundException;
import com.paulfernandosr.possystembackend.supplier.domain.port.input.GetSupplierInfoUseCase;
import com.paulfernandosr.possystembackend.supplier.domain.port.output.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetSupplierInfoService implements GetSupplierInfoUseCase {

    private final SupplierRepository supplierRepository;

    @Override
    public Supplier getSupplierInfoById(Long supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new SupplierNotFoundException("Supplier not found with identification: " + supplierId));
    }
}
