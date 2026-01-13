package com.paulfernandosr.possystembackend.supplier.application;

import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import com.paulfernandosr.possystembackend.supplier.domain.Supplier;
import com.paulfernandosr.possystembackend.supplier.domain.exception.InvalidSupplierException;
import com.paulfernandosr.possystembackend.supplier.domain.exception.SupplierAlreadyExistsException;
import com.paulfernandosr.possystembackend.supplier.domain.port.input.CreateNewSupplierUseCase;
import com.paulfernandosr.possystembackend.supplier.domain.port.output.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateNewSupplierService implements CreateNewSupplierUseCase {

    private final SupplierRepository supplierRepository;

    @Override
    public void createNewSupplier(Supplier supplier) {
        validateSupplier(supplier);

        boolean exists = supplierRepository.existsByDocument(supplier.getDocumentType(), supplier.getDocumentNumber());
        if (exists) {
            throw new SupplierAlreadyExistsException(
                    "Supplier already exists with document: " + supplier.getDocumentType() + " " + supplier.getDocumentNumber()
            );
        }

        if (!supplier.isEnabled()) {
            supplier.setEnabled(true);
        }

        supplierRepository.create(supplier);
    }

    private void validateSupplier(Supplier supplier) {
        if (supplier == null) {
            throw new InvalidSupplierException("Supplier is required");
        }
        if (supplier.getDocumentType() == null || supplier.getDocumentType() != DocumentType.RUC) {
            throw new InvalidSupplierException("Supplier document type must be RUC");
        }
        if (supplier.getDocumentNumber() == null || supplier.getDocumentNumber().isBlank()) {
            throw new InvalidSupplierException("Supplier document number (RUC) is required");
        }
        if (supplier.getLegalName() == null || supplier.getLegalName().isBlank()) {
            throw new InvalidSupplierException("Supplier legal name is required");
        }
    }
}
