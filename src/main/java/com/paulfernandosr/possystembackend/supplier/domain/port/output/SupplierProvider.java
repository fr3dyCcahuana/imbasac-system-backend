package com.paulfernandosr.possystembackend.supplier.domain.port.output;

import com.paulfernandosr.possystembackend.supplier.domain.Supplier;

import java.util.Optional;

public interface SupplierProvider {
    Optional<Supplier> findByRuc(String ruc);
}
