package com.paulfernandosr.possystembackend.supplier.domain.port.output;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.customer.domain.DocumentType;
import com.paulfernandosr.possystembackend.supplier.domain.Supplier;

import java.util.Optional;

public interface SupplierRepository {
    void create(Supplier supplier);

    Optional<Supplier> findById(Long supplierId);

    Optional<Supplier> findByDocument(DocumentType documentType, String documentNumber);

    Page<Supplier> findPage(String query, Pageable pageable);

    boolean existsByDocument(DocumentType documentType, String documentNumber);
}
