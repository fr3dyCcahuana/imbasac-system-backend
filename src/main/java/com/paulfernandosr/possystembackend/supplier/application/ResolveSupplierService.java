package com.paulfernandosr.possystembackend.supplier.application;

import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import com.paulfernandosr.possystembackend.supplier.domain.Supplier;
import com.paulfernandosr.possystembackend.supplier.domain.exception.InvalidSupplierException;
import com.paulfernandosr.possystembackend.supplier.domain.exception.SupplierNotFoundException;
import com.paulfernandosr.possystembackend.supplier.domain.port.input.ResolveSupplierUseCase;
import com.paulfernandosr.possystembackend.supplier.domain.port.output.SupplierProvider;
import com.paulfernandosr.possystembackend.supplier.domain.port.output.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ResolveSupplierService implements ResolveSupplierUseCase {

    private final SupplierRepository supplierRepository;
    private final SupplierProvider supplierProvider;

    @Override
    public Supplier resolveSupplier(Supplier supplier) {
        if (supplier == null || supplier.getDocumentType() == null || supplier.getDocumentNumber() == null) {
            throw new InvalidSupplierException("Supplier document is required");
        }
        if (supplier.getDocumentType() != DocumentType.RUC) {
            throw new InvalidSupplierException("Supplier resolve supports only RUC");
        }

        Optional<Supplier> found = supplierRepository.findByDocument(supplier.getDocumentType(), supplier.getDocumentNumber());
        if (found.isPresent()) {
            return found.get();
        }

        Supplier provided = supplierProvider.findByRuc(supplier.getDocumentNumber())
                .orElseThrow(() -> new SupplierNotFoundException("Supplier not found with RUC: " + supplier.getDocumentNumber()));

        provided.setEnabled(true);
        supplierRepository.create(provided);
        return provided;
    }
}
